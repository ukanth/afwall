package dev.ukanth.ufirewall.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.ProfileActivity;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.G;

public class MultiProfilePreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.profiles_preferences);
        Preference button = findPreference("manage_profiles");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //code for what you want it to do
                startActivity(new Intent(getActivity(), ProfileActivity.class));
                return true;
            }
        });

        final PreferenceCategory mCategory = (PreferenceCategory) findPreference("promigrate");
        final PreferenceCategory mCategory2 = (PreferenceCategory) findPreference("oldprofile_pref");
        final Preference migrate = findPreference("migrate_profile");
        if (!G.isProfileMigrated()) {
            migrate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Context ctx = getActivity();
                    ProfileHelper.migrateProfiles(ctx);
                    if (ctx != null) {
                        Api.toast(getActivity(), ctx.getString(R.string.profile_migrate_msg));
                        mCategory.removePreference(migrate);

                        Preference migrate = findPreference("profile1");
                        mCategory2.removePreference(migrate);

                        migrate = findPreference("profile2");
                        mCategory2.removePreference(migrate);

                        migrate = findPreference("profile3");
                        mCategory2.removePreference(migrate);
                    }
                    return true;
                }
            });
        } else {
            mCategory.removePreference(migrate);

            Preference migrate2 = findPreference("profile1");
            mCategory2.removePreference(migrate2);

            migrate2 = findPreference("profile2");
            mCategory2.removePreference(migrate2);

            migrate2 = findPreference("profile3");
            mCategory2.removePreference(migrate2);
        }
    }

    public void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }
}
