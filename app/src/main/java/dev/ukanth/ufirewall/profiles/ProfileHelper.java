package dev.ukanth.ufirewall.profiles;

import android.content.Context;

import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import java.io.File;
import java.util.List;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileHelper {

    private static final String TAG = "AFWall";

    public static void storeProfile(final ProfileData profile, Context ctx, ProfileData parentProfile) {
        try {
            FlowManager.getDatabase(ProfilesDatabase.class).beginTransactionAsync(new ITransaction() {
                @Override
                public void execute(DatabaseWrapper databaseWrapper) {
                    profile.save(databaseWrapper);
                }
            }).build().execute();
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("connection pool has been closed")) {
                //reconnect logic
                try {
                    FlowManager.init(new FlowConfig.Builder(ctx).build());
                } catch (Exception de) {
                    Log.i(TAG, "Exception while saving profile data:" + e.getLocalizedMessage());
                }
            }
            Log.i(TAG, "Exception while saving profile data:" + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.i(TAG, "Exception while saving profile data:" + e.getLocalizedMessage());
        }
    }

    public static List<ProfileData> getProfiles() {

        return SQLite.select()
                .from(ProfileData.class)
                .queryList();
    }


    public static ProfileData getProfileByName(String profileName) {
        return SQLite.select()
                .from(ProfileData.class).where(ProfileData_Table.name.eq(profileName))
                .querySingle();
    }

    public static ProfileData getProfileByIdentifier(String identifier) {
        return SQLite.select()
                .from(ProfileData.class).where(ProfileData_Table.identifier.eq(identifier))
                .querySingle();
    }

    public static void updateProfileName(String identifier,String newName) {
        ProfileData profileData = SQLite.select()
                .from(ProfileData.class).where(ProfileData_Table.name.eq(identifier))
                .querySingle();
        profileData.setName(newName);
        profileData.save();
    }

    public static boolean deleteProfile(String identifier) {
        ProfileData data = getProfileByIdentifier(identifier);
        if (data != null) {
            data.delete();
        }
        return true;
    }
    public static boolean deleteProfileByName(String profileName) {
        ProfileData data = getProfileByName(profileName);
        if (data != null) {
            data.delete();
        }
        return true;
    }

    private static final String LEGACY_PROFILE_PREFIX = "AFWallProfile";
    // comma-separated custom profile names of the old profile model
    private static final String LEGACY_ADDITIONAL_PROFILES = "plusprofiles";

    /**
     * Move the profiles of the old model (fixed Profile 1-3, custom names kept in the "profile1..3"
     * preferences, plus the "plusprofiles" list) into the profile database, once. The rules stay
     * in their preference files, which are keyed by the identifier, so they are not touched.
     * Profiles that are already in the database are skipped, so it is safe to run again after a
     * partial failure.
     * <p>
     * Profile 1-3 always existed in the old model; they are only carried over when they may be in
     * use, so a new install doesn't start with three empty profiles.
     */
    public static void migrateProfiles(Context ctx) {
        if (G.isProfileMigrated()) {
            return;
        }
        try {
            for (int i = 1; i <= 3; i++) {
                String identifier = LEGACY_PROFILE_PREFIX + i;
                boolean hasRules = new File(ctx.getFilesDir().getParent(), "shared_prefs/" + identifier + ".xml").exists();
                boolean renamed = !G.gPrefs.getString("profile" + i, "").trim().isEmpty();
                if (G.enableMultiProfile() || hasRules || renamed) {
                    ensureProfile(ctx, identifier);
                }
            }
            String additional = G.gPrefs.getString(LEGACY_ADDITIONAL_PROFILES, "");
            for (String name : additional.split("\\s*,\\s*")) {
                if (!name.trim().isEmpty()) {
                    ensureProfile(ctx, name.trim());
                }
            }
            G.isProfileMigrated(true);
            Log.i(TAG, "Profiles migrated to the profile database");
        } catch (Exception e) {
            Log.e(TAG, "Profile migration failed; will retry on next start", e);
        }
    }

    /**
     * Make sure a profile with this identifier exists. A missing one is created with its old-model
     * name: the custom name of Profile 1-3, otherwise the identifier itself.
     */
    public static void ensureProfile(Context ctx, String identifier) {
        if (getProfileByIdentifier(identifier) != null) {
            return;
        }
        String name = identifier;
        if (identifier.startsWith(LEGACY_PROFILE_PREFIX)) {
            String suffix = identifier.substring(LEGACY_PROFILE_PREFIX.length());
            int resId = 0;
            switch (suffix) {
                case "1":
                    resId = R.string.profile1;
                    break;
                case "2":
                    resId = R.string.profile2;
                    break;
                case "3":
                    resId = R.string.profile3;
                    break;
            }
            if (resId != 0) {
                name = G.gPrefs.getString("profile" + suffix, "");
                if (name.trim().isEmpty()) {
                    name = ctx.getString(resId);
                }
            }
        }
        new ProfileData(name, identifier).save();
    }

    /**
     * @return identifier of the profile with this name, created (like a profile added by the user)
     * if it doesn't exist
     */
    public static String ensureProfileNamed(String name) {
        ProfileData data = getProfileByName(name);
        if (data == null) {
            data = new ProfileData(name, name.replaceAll("\\s+", ""));
            data.save();
        }
        return data.getIdentifier();
    }
}
