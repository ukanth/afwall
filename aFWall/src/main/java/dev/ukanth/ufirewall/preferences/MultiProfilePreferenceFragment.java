package dev.ukanth.ufirewall.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.activity.ProfileActivity;
import dev.ukanth.ufirewall.util.G;

public class MultiProfilePreferenceFragment extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.profiles_preferences);
		Preference button = (Preference)findPreference("manage_profiles");
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				//code for what you want it to do
				startActivity(new Intent(getActivity(), ProfileActivity.class));
				return true;
			}
		});

		Preference migrate = (Preference) findPreference("migrate_profile");
		if(!G.isMigrated()) {
			migrate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					migrateProfiles();
					return true;
				}
			});
		} else {
			migrate.setEnabled(false);
		}


	}

	private void migrateProfiles() {
		try{
			//making sure profiles are enabled and not migrated
			if(!G.isMigrated()) {
				//first check if they have additional profiles
				List<String> additionalProfiles = G.getAdditionalProfiles();
				int count = 4;
				int index = 0;
				if(additionalProfiles != null && additionalProfiles.size() > 0) {
					File dir = new File(getActivity().getFilesDir().getParent() + "/shared_prefs/");
					String[] children = dir.list();
					Arrays.sort(children);
					for (int i = 0; i < children.length; i++) {
						//String profName = ;
						// clear each of the prefrances
						if(children[i].replace(".xml", "").equals("AFWallProfile" + count)) {
							File src = new File(dir, children[i]);
							File dest = new File(dir,additionalProfiles.get(index) + ".xml");
							copy(src,dest);
							src.delete();
							//new File(dir, children[i]).renameTo(new File(dir,additionalProfiles.get(index++)));
							count = count + 1;
							index = index + 1;
						}
					}
					G.isMigrated(true);
					Toast.makeText(getActivity(),"Successfully migrated the profiles.",Toast.LENGTH_LONG).show();
				}
			}else {
				Toast.makeText(getActivity(),"You have already migrated the profiles",Toast.LENGTH_LONG).show();
			}
		} catch (Exception e) {
			Toast.makeText(getActivity(),"Unable to migrate the profiles. Please clear the data and reconfigure it",Toast.LENGTH_LONG).show();
			G.isMigrated(false);
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
