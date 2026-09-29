package dev.ukanth.ufirewall.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.ProfileActivity;

public class MultiProfilePreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.profiles_preferences);
        Preference button = findPreference("manage_profiles");
        button.setOnPreferenceClickListener(preference -> {
            //code for what you want it to do
            startActivity(new Intent(getActivity(), ProfileActivity.class));
            return true;
        });
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
