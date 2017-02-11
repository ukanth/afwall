package dev.ukanth.ufirewall.profiles;

import android.content.Context;
import android.os.AsyncTask;
import android.view.View;

import com.afollestad.materialdialogs.MaterialDialog;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.util.DateComparator;
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

        List<ProfileData> profileDataList = SQLite.select()
                .from(ProfileData.class)
                .queryList();
        return profileDataList;
    }


    public static ProfileData getProfileByName(String profileName) {
        ProfileData profileData = SQLite.select()
                .from(ProfileData.class).where(ProfileData_Table.name.eq(profileName))
                .querySingle();
        return profileData;
    }

    public static ProfileData getProfileByIdentifier(String identifier) {
        ProfileData profileData = SQLite.select()
                .from(ProfileData.class).where(ProfileData_Table.identifier.eq(identifier))
                .querySingle();
        return profileData;
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

    public static void migrateProfiles(Context ctx) {
        if (!G.isProfileMigrated()) {
            List<ProfileData> listProfile = new ArrayList<>();
            List<String> addProfiles = G.getAdditionalProfiles();
            List<String> defaultProfiles = G.getDefaultProfiles();
            if (defaultProfiles != null && addProfiles != null) {
                for (int i = 0; i < defaultProfiles.size(); i++) {
                    String profileName = defaultProfiles.get(i);
                    String customName = "";
                    switch (i) {
                        case 0:
                            customName = G.gPrefs.getString("profile1", ctx.getString(R.string.profile1));
                            break;
                        case 1:
                            customName = G.gPrefs.getString("profile2", ctx.getString(R.string.profile2));
                            break;
                        case 2:
                            customName = G.gPrefs.getString("profile3", ctx.getString(R.string.profile3));
                            break;
                    }
                    ProfileData profile = new ProfileData();
                    profile.setName(customName);
                    profile.setIdentifier(profileName);
                    listProfile.add(profile);
                }
                for (String profileName : addProfiles) {
                    ProfileData profile = new ProfileData();
                    profile.setName(profileName);
                    profile.setIdentifier(profileName);
                    listProfile.add(profile);
                }
            }
            //now store the migrateProfile
            try {
                for (ProfileData profile : listProfile) {
                    ProfileHelper.storeProfile(profile, ctx, null);
                }
                //now all is well, mark as migrated
                G.isProfileMigrated(true);
            } catch (Exception e) {
                G.isProfileMigrated(false);
            }
        }
    }
}
