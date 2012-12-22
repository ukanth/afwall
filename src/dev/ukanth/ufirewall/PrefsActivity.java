/**
 * Preference Interface.
 * All iptables "communication" is handled by this class.
 * 
 * Copyright (C) 2011-2012  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall;

import net.saik0.android.unifiedpreference.UnifiedPreferenceFragment;
import net.saik0.android.unifiedpreference.UnifiedSherlockPreferenceActivity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class PrefsActivity extends UnifiedSherlockPreferenceActivity implements OnSharedPreferenceChangeListener {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setHeaderRes(R.xml.pref_headers);
		super.onCreate(savedInstanceState);
	}
	
	@Override 
    protected void onResume(){
        super.onResume();
        // Set up a listener whenever a key changes             
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }
	
	  @Override 
      protected void onPause() { 
          super.onPause();
          // Unregister the listener whenever a key changes             
          PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);     
      } 

	public static class GeneralPreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class FirewallPreferenceFragment extends
			UnifiedPreferenceFragment {
	}

	public static class MultiProfilePreferenceFragment extends
			UnifiedPreferenceFragment {
	}
	
	@Override
	 public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("showUid") || key.equals("enableMultiProfile")
				|| key.equals("altICSJB")) {
			Api.applications = null;
		}
     }


}
