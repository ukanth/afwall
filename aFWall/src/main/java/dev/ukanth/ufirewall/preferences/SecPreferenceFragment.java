package dev.ukanth.ufirewall.preferences;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.haibison.android.lockpattern.LockPatternActivity;
import com.haibison.android.lockpattern.util.Settings;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.admin.AdminDeviceReceiver;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;

import static com.haibison.android.lockpattern.LockPatternActivity.ACTION_COMPARE_PATTERN;
import static com.haibison.android.lockpattern.LockPatternActivity.ACTION_CREATE_PATTERN;
import static com.haibison.android.lockpattern.LockPatternActivity.EXTRA_PATTERN;

public class SecPreferenceFragment extends PreferenceFragment implements
		OnSharedPreferenceChangeListener {

	private static CheckBoxPreference enableAdminPref;

	private static final int REQ_CREATE_PATTERN = 9877;
	private static final int REQ_ENTER_PATTERN = 9755;

	private static final int REQUEST_CODE_ENABLE_ADMIN = 10237; // identifies
																// our request
																// ID

	private static ComponentName deviceAdmin;
	private static DevicePolicyManager mDPM;

	//private String passOption = "p0";

	public static void setupEnableAdmin(Preference pref) {
		if (pref == null) {
			return;
		}
		enableAdminPref = (CheckBoxPreference) pref;
		// query the actual device admin status from the system
		enableAdminPref.setChecked(mDPM.isAdminActive(deviceAdmin));
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// update settings with actual device admin setting
		mDPM = (DevicePolicyManager) this.getActivity().getSystemService(
				Context.DEVICE_POLICY_SERVICE);
		deviceAdmin = new ComponentName(this.getActivity()
				.getApplicationContext(), AdminDeviceReceiver.class);
		super.onCreate(savedInstanceState);



		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.security_preferences);

		//backward compatibility
		preSelectListForBackward();

		setupEnableAdmin(findPreference("enableAdmin"));

		//passOption = G.protectionLevel();
	}

	private void preSelectListForBackward() {

		final ListPreference itemList = (ListPreference)findPreference("passSetting");
		if(itemList != null) {
			//logic for patterns
			if(G.usePatterns() && G.sPrefs.getString("LockPassword", "").length() != 0) {
				itemList.setValueIndex(2);
			}//logic for passwords
			else if(G.profile_pwd().length() != 0) {
				itemList.setValueIndex(1);
			} else {
				itemList.setValueIndex(0);
			}
		}
	}


	/**
	 * Set a new password lock
	 *
	 * @param pwd
	 *            new password (empty to remove the lock)
	 */
	private void setPassword(String pwd) {
		final Resources res = getResources();
		String msg = "";
		if(pwd.length() > 0){
			String enc = Api.hideCrypt("AFW@LL_P@SSWORD_PR0T3CTI0N", pwd);
			if(enc != null) {
				G.profile_pwd(enc);
				G.isEnc(true);
				msg = res.getString(R.string.passdefined);
			}
		} /*else {
			G.profile_pwd(pwd);
			G.isEnc(false);
			msg = res.getString(R.string.passremoved);
		}*/
		Api.displayToasts(getActivity(), msg, Toast.LENGTH_SHORT);
	}

	/**
	 * Display Password dialog
	 * @param itemList
	 */
	private void showPasswordActivity(final ListPreference itemList){

		final AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
		//you should edit this to fit your needs
		builder.setTitle(getString(R.string.pass_titleset));

		final EditText firstPass = new EditText(getActivity());
		firstPass.setHint(getString(R.string.enterpass));//optional
		final EditText secondPass = new EditText(getActivity());
		secondPass.setHint(getString(R.string.reenterpass));//optional

		//in my example i use TYPE_CLASS_NUMBER for input only numbers
		firstPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		secondPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

		LinearLayout lay = new LinearLayout(getActivity());
		lay.setOrientation(LinearLayout.VERTICAL);
		lay.addView(firstPass);
		lay.addView(secondPass);
		builder.setView(lay);
		builder.autoDismiss(false);

		// Set up the buttons
		builder.setPositiveButton(R.string.set_password, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				//get the two inputs
				if(firstPass.getText().toString().equals(secondPass.getText().toString())){
					setPassword(firstPass.getText().toString());
					dialog.dismiss();
				} else {
					Api.toast(getActivity(), getString(R.string.settings_pwd_not_equal));
				}

			}
		});

		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				itemList.setValueIndex(0);
				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void showPatternActivity(){
		Intent intent = new Intent(ACTION_CREATE_PATTERN, null,getActivity(), LockPatternActivity.class);
		startActivityForResult(intent, REQ_CREATE_PATTERN);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		if(key.equals("passSetting")) {
			ListPreference itemList = (ListPreference)findPreference("passSetting");
			final ListPreference patternMaxTry = (ListPreference)findPreference("patternMax");
			final CheckBoxPreference stealthMode = (CheckBoxPreference) findPreference("stealthMode");
			stealthMode.setEnabled(false);
			patternMaxTry.setEnabled(false);
			switch (itemList.getValue()) {
				case "p0":
					//disable password completly -- add reconfirmation based on current index
					confirmResetPasswords(itemList);
					//reset pattern
					/*final SharedPreferences.Editor editor = G.sPrefs.edit();
					editor.putString("LockPassword", "");
					editor.commit();

					//reset password
					G.profile_pwd("");
					G.isEnc(false);*/

					//itemList.setValueIndex(0);
					break;
				case "p1":
					//use the existing method to protect password
					showPasswordActivity(itemList);
					break;
				case "p2":
					//use the existing method to protect password
					showPatternActivity();
					break;
				case "p3":
					//only for donate version
					break;
			}
			//passOption = "p" + index;

			//currentPosition = index;
		}
		if (key.equals("enableAdmin")) {
			boolean value = G.enableAdmin();
			if (value) {
				Log.d("Device Admin Active ?", mDPM.isAdminActive(deviceAdmin)
						+ "");
				if (!mDPM.isAdminActive(deviceAdmin)) {
					// Launch the activity to have the user enable our admin.
					Intent intent = new Intent(
							DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
					intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
							deviceAdmin);
					intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
							getString(R.string.device_admin_desc));
					startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
				}
			} else {
				if (mDPM.isAdminActive(deviceAdmin)) {
					mDPM.removeActiveAdmin(deviceAdmin);
					Api.displayToasts(this.getActivity()
							.getApplicationContext(),
							R.string.device_admin_disabled, Toast.LENGTH_LONG);
				}
			}
		}
		
		if (key.equals("enableStealthPattern")) {
			Settings.Display.setStealthMode(this.getActivity().getApplicationContext(),
					G.enableStealthPattern());
		}

	}

	/**
	 * Make sure it's verified before remove passwords
	 * @param itemList
	 */
	private void confirmResetPasswords(final ListPreference itemList) {
		//TODO: Logic here
		String pattern = G.sPrefs.getString("LockPassword", "");
		String pwd = G.profile_pwd();
		if(pwd.length() > 0 ) {
			new MaterialDialog.Builder(getActivity()).cancelable(false)
					.title(R.string.confirmation).autoDismiss(false)
					.content(R.string.enterpass)
					.inputType(InputType.TYPE_CLASS_TEXT)
					.input(R.string.enterpass, R.string.password_empty, new MaterialDialog.InputCallback() {
						@Override
						public void onInput(MaterialDialog dialog, CharSequence input) {
							String pass = input.toString();
							boolean isAllowed = false;
							if (G.isEnc()) {
								String decrypt = Api.unhideCrypt("AFW@LL_P@SSWORD_PR0T3CTI0N", G.profile_pwd());
								if (decrypt != null) {
									if (decrypt.equals(pass)) {
										isAllowed = true;
										//Api.toast(getActivity(), getString(R.string.wrong_password));
									}
								}
							} else {
								if (pass.equals(G.profile_pwd())) {
									//reset password
									isAllowed = true;
									//Api.toast(getActivity(), getString(R.string.wrong_password));
								}
							}
							if(isAllowed) {
								G.profile_pwd("");
								G.isEnc(false);
								itemList.setValueIndex(0);
								dialog.dismiss();
							} else {
								Api.toast(getActivity(), getString(R.string.wrong_password));
							}
						}
					}).show();

		}

		if(pattern.length() > 0) {
				Intent intent = new Intent(ACTION_COMPARE_PATTERN, null, getActivity(), LockPatternActivity.class);
				String savedPattern  = G.sPrefs.getString("LockPassword", "");
				intent.putExtra(EXTRA_PATTERN, savedPattern.toCharArray());
				startActivityForResult(intent, REQ_ENTER_PATTERN);
		}

	}

	@Override
	public void onResume() {
		super.onResume();
		getPreferenceManager().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(this);

	}

	@Override
	public void onPause() {
		getPreferenceManager().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(this);
		super.onPause();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode) {
			case REQ_CREATE_PATTERN: {
				ListPreference itemList = (ListPreference)findPreference("passSetting");
				if (resultCode == getActivity().RESULT_OK) {
					char[] pattern = data.getCharArrayExtra(
							EXTRA_PATTERN);
					final SharedPreferences.Editor editor = G.sPrefs.edit();
					editor.putString("LockPassword", new String(pattern));
					editor.commit();
					//enable
					if(itemList != null) {
						final ListPreference patternMaxTry = (ListPreference)findPreference("patternMax");
						final CheckBoxPreference stealthMode = (CheckBoxPreference) findPreference("stealthMode");
						if(stealthMode != null )stealthMode.setEnabled(true);
						if(patternMaxTry != null )patternMaxTry.setEnabled(true);
					}

				}else {
					itemList = (ListPreference)findPreference("passSetting");
					if(itemList != null) {
						itemList.setValueIndex(0);
					}
				}
				break;
			}

			case REQ_ENTER_PATTERN: {
				ListPreference itemList = (ListPreference)findPreference("passSetting");
				if (resultCode == getActivity().RESULT_OK) {
					final SharedPreferences.Editor editor = G.sPrefs.edit();
					editor.putString("LockPassword", "");
					editor.commit();
					itemList = (ListPreference)findPreference("passSetting");
					if(itemList != null) {
						itemList.setValueIndex(0);
					}
				}
			}
		}
	}

}
