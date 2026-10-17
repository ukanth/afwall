package dev.ukanth.ufirewall.plugin;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

/**
 * Signs Tasker/Locale actions in the editor and checks them when they run, so only actions the
 * user saved in AFWall+ can control it (the plug-in receiver has to accept broadcasts from any app).
 * <p>
 * The private key lives in the Android Keystore. With a screen lock it can only sign after the user
 * unlocks (per use on Android 11+, within {@link #AUTH_VALIDITY_S} s on older versions); the
 * Keystore enforces this, not AFWall+. Checking needs only the public keys, kept in AFWall+'s
 * private storage, so actions run in the background, and keep working when the Keystore key is
 * replaced (e.g. the screen lock was removed, which deletes keys bound to it).
 */
public final class ActionSigner {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS_PREFIX = "afwall_tasker_";
    private static final String ALGORITHM = "SHA256withECDSA";
    private static final int AUTH_VALIDITY_S = 30;

    private static final String PREFS = "tasker_signing";
    private static final String KEY_ALIAS = "alias";
    private static final String KEY_ALIAS_AUTH = "aliasAuth";
    private static final String KEY_TRUSTED = "trusted";

    private ActionSigner() {
    }

    static boolean isDeviceSecure(Context ctx) {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    /**
     * A signature ready for {@link Signature#update} / {@link Signature#sign}. With a screen lock,
     * signing fails until the user has unlocked: on Android 11+ pass it to BiometricPrompt as the
     * CryptoObject; on older versions confirm the device credential first, then call this.
     * <p>
     * Creates the key the first time, and a new one when the screen lock was added or removed since
     * (the old public key stays trusted, so saved actions keep working).
     */
    static Signature signature(Context ctx) throws GeneralSecurityException {
        boolean secure = isDeviceSecure(ctx);
        SharedPreferences prefs = prefs(ctx);
        String alias = prefs.getString(KEY_ALIAS, null);
        if (alias != null && prefs.getBoolean(KEY_ALIAS_AUTH, false) == secure) {
            try {
                return initSign(alias);
            } catch (KeyPermanentlyInvalidatedException e) {
                Log.i(G.TAG, "Tasker signing key invalidated, creating a new one");
            } catch (GeneralSecurityException e) {
                if (isUserNotAuthenticated(e)) {
                    throw e; // Android 6-10: the caller asks for the credential and retries
                }
                Log.w(G.TAG, "Tasker signing key unusable, creating a new one: " + e);
            }
        }
        alias = createKey(ctx, secure);
        return initSign(alias);
    }

    static boolean isUserNotAuthenticated(Exception e) {
        return e instanceof android.security.keystore.UserNotAuthenticatedException;
    }

    private static Signature initSign(String alias) throws GeneralSecurityException {
        KeyStore ks = keyStore();
        KeyStore.Entry entry = ks.getEntry(alias, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new GeneralSecurityException("no key " + alias);
        }
        PrivateKey key = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        Signature sig = Signature.getInstance(ALGORITHM);
        sig.initSign(key);
        return sig;
    }

    private static String createKey(Context ctx, boolean requireAuth) throws GeneralSecurityException {
        String alias = ALIAS_PREFIX + System.currentTimeMillis();
        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256);
        if (requireAuth) {
            spec.setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // every signature needs a fresh unlock
                spec.setUserAuthenticationParameters(0,
                        KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG);
            } else {
                spec.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_S);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // enrolling another fingerprint shouldn't need a new key
                spec.setInvalidatedByBiometricEnrollment(false);
            }
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE);
        gen.initialize(spec.build());
        KeyPair pair = gen.generateKeyPair();

        SharedPreferences prefs = prefs(ctx);
        Set<String> trusted = new HashSet<>(prefs.getStringSet(KEY_TRUSTED, new HashSet<>()));
        trusted.add(Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP));
        String old = prefs.getString(KEY_ALIAS, null);
        prefs.edit()
                .putStringSet(KEY_TRUSTED, trusted)
                .putString(KEY_ALIAS, alias)
                .putBoolean(KEY_ALIAS_AUTH, requireAuth)
                .commit();
        if (old != null) {
            deleteEntry(old); // its public key stays trusted
        }
        Log.i(G.TAG, "Tasker signing key created (screen lock required: " + requireAuth + ")");
        return alias;
    }

    /**
     * @return true if {@code signature} (Base64) was made by one of AFWall+'s keys over {@code payload}
     */
    static boolean verify(Context ctx, byte[] payload, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] sig;
        try {
            sig = Base64.decode(signature, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            return false;
        }
        List<byte[]> keys = new ArrayList<>();
        for (String k : prefs(ctx).getStringSet(KEY_TRUSTED, new HashSet<>())) {
            try {
                keys.add(Base64.decode(k, Base64.NO_WRAP));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return verify(keys, payload, sig);
    }

    /**
     * @param encodedKeys X.509 encoded EC public keys
     */
    static boolean verify(Collection<byte[]> encodedKeys, byte[] payload, byte[] signature) {
        for (byte[] encoded : encodedKeys) {
            try {
                PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
                Signature sig = Signature.getInstance(ALGORITHM);
                sig.initVerify(key);
                sig.update(payload);
                if (sig.verify(signature)) {
                    return true;
                }
            } catch (GeneralSecurityException | RuntimeException ignored) {
                // a malformed signature or key: try the next key
            }
        }
        return false;
    }

    static String encode(byte[] signature) {
        return Base64.encodeToString(signature, Base64.NO_WRAP);
    }

    /**
     * Stop every saved action from working: forget all public keys and delete the Keystore keys.
     * The next action saved gets a new key.
     */
    public static void revokeAll(Context ctx) {
        prefs(ctx).edit().clear().commit();
        try {
            KeyStore ks = keyStore();
            List<String> aliases = new ArrayList<>();
            for (Enumeration<String> e = ks.aliases(); e.hasMoreElements(); ) {
                String alias = e.nextElement();
                if (alias.startsWith(ALIAS_PREFIX)) {
                    aliases.add(alias);
                }
            }
            for (String alias : aliases) {
                ks.deleteEntry(alias);
            }
        } catch (GeneralSecurityException e) {
            Log.w(G.TAG, "Unable to delete the Tasker signing keys: " + e);
        }
    }

    private static void deleteEntry(String alias) {
        try {
            keyStore().deleteEntry(alias);
        } catch (GeneralSecurityException e) {
            Log.w(G.TAG, "Unable to delete Tasker signing key " + alias + ": " + e);
        }
    }

    private static KeyStore keyStore() throws GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        try {
            ks.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
        return ks;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
