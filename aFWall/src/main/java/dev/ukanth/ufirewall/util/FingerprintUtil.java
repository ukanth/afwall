package dev.ukanth.ufirewall.util;

/**
 * This file was created to simplify Fingerprint APIs and made specifically for (AFWall+) application.
 * You are free to re-distributed this file anywhere you like. :)
 * ----------------------------------------------
 * Created by vzool on 1/20/17.
 */

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;

import static android.content.Context.FINGERPRINT_SERVICE;
import static android.content.Context.KEYGUARD_SERVICE;

public class FingerprintUtil {

    final static String TAG = "AfWall-FingerprintUtil";

    // generate key based on pkg name
    public static String GetKey(Context context){
        return Api.getCurrentPackage(context) + ":Fingerprint";
    }

    // safely check if device support fingerprint
    public static boolean isAndroidSupport(){
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /*
    * Dialog
    **/
    public static class FingerprintDialog extends Dialog {

        private KeyStore keyStore;
        // Variable used for storing the key in the Android Keystore container
        private static String KEY_NAME = "Key will be determined at onCreate Event";
        private Cipher cipher;
        TextView errorText;

        boolean isNotFirstWindowFocus = false;

        KeyguardManager keyguardManager;
        FingerprintManager fingerprintManager;

        FingerprintHandler helper;

        FingerprintManager.CryptoObject cryptoObject;

        // callbacks
        OnFingerprintFailure failureCallback;
        OnFingerprintSuccess successCallback;

        @RequiresApi(api = Build.VERSION_CODES.M)
        public FingerprintDialog(Context context) {
            super(context);

            // Initializing both Android Keyguard Manager and Fingerprint Manager
            keyguardManager = (KeyguardManager) getContext().getSystemService(KEYGUARD_SERVICE);
            fingerprintManager = (FingerprintManager) getContext().getSystemService(FINGERPRINT_SERVICE);
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setCancelable(false);
            setContentView(R.layout.fingerprint);
            setTitle(R.string.fingerprint_required);

            errorText = (TextView) findViewById(R.id.fingerprintErrorText);

            // choose key that depends on [pkg_name]:Fingerprint
            KEY_NAME = GetKey(getContext());

            setFullscreenDialog();
        }

        // Full screen
        void setFullscreenDialog(){

            try{

                WindowManager manager = (WindowManager) getContext().getSystemService(Activity.WINDOW_SERVICE);

                Point point = new Point();
                manager.getDefaultDisplay().getSize(point);

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams();

                lp.copyFrom(getWindow().getAttributes());
                lp.width = point.x;
                lp.height = point.y;
                getWindow().setAttributes(lp);

            }catch (NullPointerException ex){
                
                Log.e(TAG, ex.getMessage());
            }
        }

        @Override
        protected void onStart() {
            super.onStart();

            startReadFingerTip();
        }

        @Override
        public void onBackPressed() {
            super.onBackPressed();

            if(failureCallback != null){

                failureCallback.then();
            }
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            super.onWindowFocusChanged(hasFocus);

            /* Focus on current window is required and critical,
            *  if focus interrupted by "Home Button" or "SuperSU Dialog"
            */

            if(hasFocus){

                if(isNotFirstWindowFocus){

                    startReadFingerTip();
                }

                isNotFirstWindowFocus = true;

            }else{

                stopReadFingerTip();
            }
        }

        public void setOnFingerprintFailureListener(OnFingerprintFailure mayHappen){
            failureCallback = mayHappen;
        }

        public void setOnFingerprintSuccess(OnFingerprintSuccess doSomething){
            successCallback = doSomething;
        }

        /**
         * Created by whit3hawks on 11/16/16.
         * Modified by vzool on 1/14/17.
         */
        @TargetApi(Build.VERSION_CODES.M)
        void startReadFingerTip(){

            // Check whether the device has a Fingerprint sensor.
            if(!fingerprintManager.isHardwareDetected()){
                /**
                 * This block will not be touched unless weird things happened,
                 * because we already checked if device support fingerprint before enable it.
                 * We just leave it as-is for the days, who knows! :)
                 */
                errorText.setText(R.string.device_with_no_fingerprint_sensor);
            }else {
                // Checks whether fingerprint permission is set on manifest
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                    errorText.setText(R.string.fingerprint_permission_manifest_missing);
                }else{
                    // Check whether at least one fingerprint is registered
                    if (!fingerprintManager.hasEnrolledFingerprints()) {
                        errorText.setText(R.string.register_at_least_one_fingerprint);
                    }else{
                        // Checks whether lock screen security is enabled or not
                        if (!keyguardManager.isKeyguardSecure()) {
                            errorText.setText(R.string.lock_screen_not_enabled);
                        }else{

                            generateKey();

                            if (cipherInit()) {

                                cryptoObject = new FingerprintManager.CryptoObject(cipher);

                                if(helper == null){

                                    helper = new FingerprintHandler();
                                }

                                helper.startAuth(fingerprintManager, cryptoObject);
                            }
                        }
                    }
                }
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        void stopReadFingerTip(){

            if(helper != null){

                helper.stopAuth();
            }
        }

        private void triggerSuccess(){
            if(successCallback != null){
                successCallback.then();
            }
            if(isShowing()) {
                dismiss();
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        private void generateKey() {
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore");
            } catch (Exception e) {
                Log.e(TAG, "Error(0): " + e.getMessage());
            }


            KeyGenerator keyGenerator;
            try {
                keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
                throw new RuntimeException("Failed to get KeyGenerator instance", e);
            }


            try {
                keyStore.load(null);
                keyGenerator.init(new
                        KeyGenParameterSpec.Builder(KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT |
                                KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setUserAuthenticationRequired(true)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .build());
                keyGenerator.generateKey();
            } catch (NoSuchAlgorithmException |
                    InvalidAlgorithmParameterException
                    | CertificateException | ProviderException | IOException e) {
                Log.e(TAG, "Error(1): " + e.getMessage());
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        private boolean cipherInit() {
            try {
                cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_CBC + "/" + KeyProperties.ENCRYPTION_PADDING_PKCS7);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                throw new RuntimeException("Failed to get Cipher", e);
            }


            try {
                keyStore.load(null);
                SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME,
                        null);
                cipher.init(Cipher.ENCRYPT_MODE, key);
                return true;
            } catch (KeyPermanentlyInvalidatedException e) {
                return false;
            } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException("Failed to init Cipher", e);
            }
        }

        /**
         * Created by whit3hawks on 11/16/16.
         * Modified by vzool on 1/14/17.
         */
        @RequiresApi(api = Build.VERSION_CODES.M)
        private class FingerprintHandler extends FingerprintManager.AuthenticationCallback {

            CancellationSignal cancellationSignal;

            void FingerprintHandler(){

                cancellationSignal = new CancellationSignal();
            }

            private void startAuth(FingerprintManager manager, FingerprintManager.CryptoObject cryptoObject) {

                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                manager.authenticate(cryptoObject, cancellationSignal, 0, this, null);
            }

            private void stopAuth(){

                if(cancellationSignal != null){

                    cancellationSignal.cancel();

                    cancellationSignal = null;
                }
            }


            @Override
            public void onAuthenticationError(int errMsgId, CharSequence errString) {

                // First attempts always fail due to interruption by SuperSU dialog.
                // So, this view should be first responder.
                // We ignore any errors and repeat process again :)
                //
                // this.update(getContext().getString(R.string.fingerprint_authentication_error) + errString, false);

                startReadFingerTip();
            }


            @Override
            public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
                this.update(getContext().getString(R.string.fingerprint_authentication_help) + helpString, false);
            }


            @Override
            public void onAuthenticationFailed() {
                this.update(getContext().getString(R.string.fingerprint_authentication_failed), false);
            }


            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                this.update(getContext().getString(R.string.fingerprint_authentication_successded), true);
            }


            public void update(String e, Boolean success){

                errorText.setText(e);

                if(success){
                    errorText.setTextColor(getContext().getColor(R.color.dark_bg));
                    triggerSuccess();
                }
            }
        }
    }

    // interface for callback on failure
    public interface OnFingerprintFailure{
        void then();
    }

    // interface for callback on Success
    public interface OnFingerprintSuccess{
        void then();
    }
}
