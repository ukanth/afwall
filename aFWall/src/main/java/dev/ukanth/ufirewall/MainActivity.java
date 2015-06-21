/**
 * Main application activity.
 * This is the screen displayed when you open the application
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
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
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */

package dev.ukanth.ufirewall;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.haibison.android.lockpattern.LockPatternActivity;
import com.haibison.android.lockpattern.util.Settings;
import com.orleonsoft.android.simplefilechooser.Constants;
import com.orleonsoft.android.simplefilechooser.ui.FileChooserActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.service.RootShell.RootCommand;
import dev.ukanth.ufirewall.activity.CustomScriptActivity;
import dev.ukanth.ufirewall.activity.HelpActivity;
import dev.ukanth.ufirewall.activity.LogActivity;
import dev.ukanth.ufirewall.activity.RulesActivity;
import dev.ukanth.ufirewall.preferences.PreferencesActivity;
import dev.ukanth.ufirewall.util.AppListArrayAdapter;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ImportApi;
import eu.chainfire.libsuperuser.Shell;

import static com.haibison.android.lockpattern.LockPatternActivity.ACTION_COMPARE_PATTERN;
import static com.haibison.android.lockpattern.LockPatternActivity.EXTRA_PATTERN;
import static com.haibison.android.lockpattern.LockPatternActivity.RESULT_FAILED;
import static com.haibison.android.lockpattern.LockPatternActivity.RESULT_FORGOT_PATTERN;


public class MainActivity extends AppCompatActivity implements OnClickListener,
					ActionBar.OnNavigationListener,SwipeRefreshLayout.OnRefreshListener {

	private TextView mSelected;
    private String[] mLocations;
	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private Menu mainMenu;
   	public boolean isOnPause = false;
	private ListView listview = null;
	public static boolean dirty = false;
	private MaterialDialog plsWait;
	private ArrayAdapter<String> spinnerAdapter = null;
	private SwipeRefreshLayout mSwipeLayout;
	private int index;
	private int top;
	private List<String> mlocalList = new ArrayList<String>();

	private static final int REQ_ENTER_PATTERN = 9755;
	private static final int SHOW_ABOUT_RESULT = 1200;
	private static final int PREFERENCE_RESULT = 1205;
	private static final int SHOW_CUSTOM_SCRIPT = 1201;
	private static final int SHOW_RULES_ACTIVITY = 1202;
	private static final int SHOW_LOGS_ACTIVITY = 1203;
	private static final int FILE_CHOOSER_LOCAL = 1700;
	private static final int FILE_CHOOSER_ALL = 1701;

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		MainActivity.dirty = dirty;
	}

	/** Called when the activity is first created
	 * . */
	@Override
	public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			isOnPause = false;
			
			try {
				/* enable hardware acceleration on Android >= 3.0 */
				final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class
						.getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
				getWindow().setFlags(FLAG_HARDWARE_ACCELERATED,
						FLAG_HARDWARE_ACCELERATED);
			} catch (Exception e) {
			}

			setContentView(R.layout.main);



			Toolbar toolbar = (Toolbar) findViewById(R.id.main_toolbar);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
						WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
			}
			setSupportActionBar(toolbar);
			//set onclick listeners
			this.findViewById(R.id.label_mode).setOnClickListener(this);
			this.findViewById(R.id.img_wifi).setOnClickListener(this);
			this.findViewById(R.id.img_reset).setOnClickListener(this);
			this.findViewById(R.id.img_invert).setOnClickListener(this);


        	Settings.Display.setStealthMode(getApplicationContext(), G.enableStealthPattern());
	        Settings.Display.setMaxRetries(getApplicationContext(), G.getMaxPatternTry());
	       
		    Api.assertBinaries(this, true);

			mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
			mSwipeLayout.setOnRefreshListener(this);
			// Let's do some background stuff
			(new Startup()).setContext(this).execute();
	}


	@Override
	public void onRefresh() {
		Api.applications = null;
		showOrLoadApplications();
		mSwipeLayout.setRefreshing(false);
	}

	private void selectFilterGroup() {
		Spinner spinner1 = (Spinner) findViewById(R.id.filterGroup);
		spinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
			{
				switch(pos) {
					case 0:
						showApplications("", 99, true);
						break;
					case 1:
						showApplications(null, 0, false);
						break;
					case 2:
						showApplications(null, 1, false);
						break;
					case 3:
						showApplications(null, 2, false);
						break;
					default:
						showOrLoadApplications();
						break;
				}
			}

			public void onNothingSelected(AdapterView<?> parent)
			{

			}
		});
		showApplications("", 99, true);
	}
	

	private void updateIconStatus() {
		if(Api.isEnabled(getApplicationContext())) {
			getSupportActionBar().setIcon(R.drawable.notification);
		} else {
			getSupportActionBar().setIcon(R.drawable.notification_error);
		}
	}
	
	private void startRootShell() {
		List<String> cmds = new ArrayList<String>();
		cmds.add("true");
		new RootCommand().setFailureToast(R.string.error_su)
			.setReopenShell(true).run(getApplicationContext(), cmds);
		//put up the notification
		if(G.activeNotification()){ 
			Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		reloadPreferences();
	}

	private void reloadPreferences() {

		getSupportActionBar().setDisplayShowHomeEnabled(true);
		G.reloadPrefs();
		checkPreferences();
		//language
		Api.updateLanguage(getApplicationContext(), G.locale());

		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}

		//verifyMultiProfile();
		refreshHeader();
		updateIconStatus();

		NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(Api.NOTIFICATION_ID);

		if (G.disableIcons()) {
			this.findViewById(R.id.imageHolder).setVisibility(View.GONE);
		} else {
			this.findViewById(R.id.imageHolder).setVisibility(View.VISIBLE);
		}

		if (G.showFilter()) {
			this.findViewById(R.id.filerOption).setVisibility(View.VISIBLE);
		} else {
			this.findViewById(R.id.filerOption).setVisibility(View.GONE);
		}
		if (G.enableRoam()) {
			addColumns(R.id.img_roam);
		} else {
			hideColumns(R.id.img_roam);
		}
		if (G.enableVPN()) {
			addColumns(R.id.img_vpn);
		} else {
			hideColumns(R.id.img_vpn);
		}

		if (!Api.isMobileNetworkSupported(getApplicationContext())) {
			ImageView view = (ImageView) this.findViewById(R.id.img_3g);
			view.setVisibility(View.GONE);

		} else {
			this.findViewById(R.id.img_3g).setOnClickListener(this);
		}

		if (G.enableLAN()) {
			addColumns(R.id.img_lan);
		} else {
			hideColumns(R.id.img_lan);
		}

		//updateRadioFilter();

		if (!G.enableMultiProfile()) {
			getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			mSelected = (TextView)findViewById(R.id.text);
			mSelected.setText("");
		} else {
			setupMultiProfile(true);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		reloadPreferences();
	}

	private void addColumns(int id) {
		ImageView view = (ImageView)this.findViewById(id);
		view.setVisibility(View.VISIBLE);
		view.setOnClickListener(this);
	}
	
	private void hideColumns(int id) {
		ImageView view = (ImageView)this.findViewById(id);
		view.setVisibility(View.GONE);
		view.setOnClickListener(this);
	}
	
	private void setupMultiProfile(boolean reset){
		if(G.enableMultiProfile()) {
			
			if(reset) {
				 mlocalList = new ArrayList<String>();
			}
			G.reloadPrefs();
			mSelected = (TextView)findViewById(R.id.text);
			
			
			mlocalList.add(G.gPrefs.getString("default", getString(R.string.defaultProfile)));
			mlocalList.add(G.gPrefs.getString("profile1", getString(R.string.profile1)));
			mlocalList.add(G.gPrefs.getString("profile2", getString(R.string.profile2)));
			mlocalList.add(G.gPrefs.getString("profile3", getString(R.string.profile3)));
			
			boolean isAdditionalProfiles = false;
			List<String> profilesList = G.getAdditionalProfiles();
			for(String profiles : profilesList) {
				isAdditionalProfiles = true;
				mlocalList.add(profiles);
			}
			
			int position = G.storedPosition();
			//something went wrong - No profiles but still it's set more. reset to default
			if(!isAdditionalProfiles && position > 3) {
				G.storedPosition(0);
				position = 0;
			}
			
			
			mlocalList.add(getString(R.string.profile_add));
			mlocalList.add(getString(R.string.profile_remove));

			mLocations = mlocalList.toArray(new String[mlocalList.size()]);
			
			spinnerAdapter =  new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,
		    	    mLocations);
			spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	
			getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			getSupportActionBar().setListNavigationCallbacks(spinnerAdapter, this);
			
			if(position > -1) {
				getSupportActionBar().setSelectedNavigationItem(position);
				getSupportActionBar().setDisplayShowTitleEnabled(true);
				mSelected.setText("  |  " + mLocations[position]);
			}
			getSupportActionBar().setDisplayUseLogoEnabled(true);
		}
	}
	
	private boolean passCheck(){
		switch (G.protectionLevel()) {
			case 0:
				return true;
			case 1:
				final String oldpwd = G.profile_pwd();
				if (oldpwd.length() == 0) {
					return true;
				} else {
					// Check the password
					requestPassword();
				}
				break;
			case 2:
				final String pwd = G.sPrefs.getString("LockPassword", "");
				if (pwd.length() == 0) {
					return true;
				} else {
					requestPassword();
				}
				break;
		}
		return false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		//this.listview.setAdapter(null);
		//mLastPause = Syst em.currentTimeMillis();
		isOnPause = true;
		//checkForProfile = true;
		index = this.listview.getFirstVisiblePosition();
		View v = this.listview.getChildAt(0);
		top = (v == null) ? 0 : v.getTop();
	}

	/**
	 * Check if the stored preferences are OK
	 */
	private void checkPreferences() {
		final Editor editor = G.pPrefs.edit();
		boolean changed = false;
		if (G.pPrefs.getString(Api.PREF_MODE, "").length() == 0) {
			editor.putString(Api.PREF_MODE, Api.MODE_WHITELIST);
			changed = true;
		}
		if (changed)
			editor.commit();
	}

	/**
	 * Refresh informative header
	 */
	private void refreshHeader() {
		final String mode = G.pPrefs.getString(Api.PREF_MODE, Api.MODE_WHITELIST);
		final TextView labelmode = (TextView) this.findViewById(R.id.label_mode);
		final Resources res = getResources();
		int resid = (mode.equals(Api.MODE_WHITELIST) ? R.string.mode_whitelist: R.string.mode_blacklist);
		labelmode.setText(res.getString(R.string.mode_header, res.getString(resid)));
	}

	/**
	 * Displays a dialog box to select the operation mode (black or white list)
	 */
	private void selectMode() {
		final Resources res = getResources();

		new MaterialDialog.Builder(this)
				.title(R.string.selectMode)
				.items(new String[]{
						res.getString(R.string.mode_whitelist),
						res.getString(R.string.mode_blacklist)})
				.itemsCallback(new MaterialDialog.ListCallback() {
					@Override
					public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
						final String mode = (which == 0 ? Api.MODE_WHITELIST : Api.MODE_BLACKLIST);
						final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
						editor.putString(Api.PREF_MODE, mode);
						editor.commit();
						refreshHeader();
					}
				})
				.show();		
	}
	




	/**
	 * Request the password lock before displayed the main screen.
	 */
	private void requestPassword() {
		switch(G.protectionLevel()) {
			case 1:
				new MaterialDialog.Builder(MainActivity.this)
						.title(R.string.pass_titleget).autoDismiss(false)
						.inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
						.positiveText(R.string.submit)
						.negativeText(R.string.Cancel)
						.callback(new MaterialDialog.ButtonCallback() {
							@Override
							public void onNegative(MaterialDialog dialog) {
								MainActivity.this.finish();
								android.os.Process.killProcess(android.os.Process.myPid());
							}
						})
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
										}
									}
								} else {
									if (pass.equals(G.profile_pwd())) {
										isAllowed = true;
									}
								}
								if (isAllowed) {
									showOrLoadApplications();
									dialog.dismiss();
								} else {
									Api.toast(MainActivity.this, getString(R.string.wrong_password));
								}


							}
						}).show();
				break;
			case 2:
				Intent intent = new Intent(ACTION_COMPARE_PATTERN, null, getApplicationContext(), LockPatternActivity.class);
				String savedPattern  = G.sPrefs.getString("LockPassword", "");
				intent.putExtra(EXTRA_PATTERN, savedPattern.toCharArray());
				startActivityForResult(intent, REQ_ENTER_PATTERN);
				break;
		}

	}


	/**
	 * If the applications are cached, just show them, otherwise load and show
	 */
	private void showOrLoadApplications() {
		//nocache!!
		(new GetAppList()).setContext(this).execute();	
	}
	

	public class GetAppList extends AsyncTask<Void, Integer, Void> {

		boolean started = false;
		Context context = null;
		AsyncTask<Void,Integer,Void> myAsyncTaskInstance = null;
		
		public GetAppList setContext(Context context) {
	        this.context = context;
	        return this;
	    }

		@Override
		protected void onPreExecute() {
			plsWait = new MaterialDialog.Builder(context).
					title(getString(R.string.reading_apps)).progress(false, getPackageManager().getInstalledApplications(0)
					.size(), true).show();
			publishProgress(0);
		}

		public void doProgress(int value) {
			publishProgress(value);
		}
		
		public AsyncTask<Void, Integer, Void> getInstance() {
			// if the current async task is already running, return null: no new
			// async task
			// shall be created if an instance is already running
			if ((myAsyncTaskInstance != null)
					&& myAsyncTaskInstance.getStatus() == Status.RUNNING) {
				// it can be running but cancelled, in that case, return a new
				// instance
				if (myAsyncTaskInstance.isCancelled()) {
					myAsyncTaskInstance = new GetAppList();
				} else {
					return null;
				}
			}

			// if the current async task is pending, it can be executed return
			// this instance
			if ((myAsyncTaskInstance != null)
					&& myAsyncTaskInstance.getStatus() == Status.PENDING) {
				return myAsyncTaskInstance;
			}

			// if the current async task is finished, it can't be executed
			// another time, so return a new instance
			if ((myAsyncTaskInstance != null)
					&& myAsyncTaskInstance.getStatus() == Status.FINISHED) {
				myAsyncTaskInstance = new GetAppList();
			}

			// if the current async task is null, create a new instance
			if (myAsyncTaskInstance == null) {
				myAsyncTaskInstance = new GetAppList();
			}
			// return the current instance
			return myAsyncTaskInstance;
		}

		@Override
		protected Void doInBackground(Void... params) {
			Api.getApps(MainActivity.this, this);
			if( isCancelled() )
                return null;
            //publishProgress(-1);
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			selectFilterGroup();
			publishProgress(-1);
			try {
				started = false;
				plsWait.dismiss();
				mSwipeLayout.setRefreshing(false);
				//plsWait.autoDismiss(true);
			} catch (Exception e) {
				// nothing
			}
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {

			if (progress[0] == 0 ||  progress[0] == -1) {
				//do nothing
			} else {
				 plsWait.incrementProgress(progress[0]);
			}
		}
	};

	
	
	class PackageComparator implements Comparator<PackageInfoData> {

		@Override
		public int compare(PackageInfoData o1, PackageInfoData o2) {
			if (o1.firstseen != o2.firstseen) {
				return (o1.firstseen ? -1 : 1);
			}
			boolean o1_selected = o1.selected_3g || o1.selected_wifi || o1.selected_roam ||
					o1.selected_vpn || o1.selected_lan;
			boolean o2_selected = o2.selected_3g || o2.selected_wifi || o2.selected_roam ||
					o2.selected_vpn || o2.selected_lan;

			if (o1_selected == o2_selected) {
				return String.CASE_INSENSITIVE_ORDER.compare(o1.names.get(0).toString(),o2.names.get(0).toString());
			}
			if (o1_selected)
				return -1;
			return 1;
		}
	}


    class PackageModifiedComparator implements Comparator<PackageInfoData> {

        @Override
        public int compare(PackageInfoData p1, PackageInfoData p2) {
            if (p1.firstseen != p2.firstseen) {
                return (p1.firstseen ? -1 : 1);
            }

            boolean p1_selected = p1.selected_3g || p1.selected_wifi || p1.selected_roam ||
                    p1.selected_vpn || p1.selected_lan;
            boolean p2_selected = p2.selected_3g || p2.selected_wifi || p2.selected_roam ||
                    p2.selected_vpn || p2.selected_lan;

            if (p1_selected == p2_selected) {
                return p1.installTime > p2.installTime ? -1: p1.installTime < p2.installTime ? 1 : 0;
            }
            if (p1_selected)
                return -1;
            return 1;
        }
    }


    /**
	 * Show the list of applications
	 */
	private void showApplications(final String searchStr, int flag, boolean showAll) {
		setDirty(false);
		List<PackageInfoData> searchApp = new ArrayList<PackageInfoData>();
		final List<PackageInfoData> apps = Api.getApps(this,null);
		boolean isResultsFound = false;
		if(searchStr !=null && searchStr.length() > 1) {
			for(PackageInfoData app:apps) {
				for(String str: app.names) {
					if(str.contains(searchStr.toLowerCase()) || str.toLowerCase().contains(searchStr.toLowerCase())
							&& !searchApp.contains(app)) {
						searchApp.add(app);
						isResultsFound = true;
					} 
				}
			}
		} else if (flag > -1){
			switch(flag){
				case 0:
					for(PackageInfoData app:apps) {
					   if(app.pkgName.startsWith("dev.afwall.special")) {
						   searchApp.add(app);   
					   }
					}
					break;
				case 1:
					for(PackageInfoData app: apps) {
						if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
							searchApp.add(app);
						}
					}
					break;
				case 2:
					for(PackageInfoData app: apps) {
						if (app.appinfo != null && (app.appinfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
							searchApp.add(app);
						}
					}
					break;
			}
			
		}
		List<PackageInfoData> apps2;
		if(showAll || (searchStr != null && searchStr.equals(""))) {
			apps2 = apps; 
		} else if(isResultsFound || searchApp.size() > 0) {
			apps2 = searchApp;
		} else {
			apps2 = new ArrayList<PackageInfoData>();
		}
		
		// Sort applications - selected first, then alphabetically
        switch (G.sortBy()) {
            case 0:
                Collections.sort(apps2, new PackageComparator());
                break;
            case 1:
                Collections.sort(apps2, new PackageModifiedComparator());
                break;

        }

		this.listview.setAdapter(new AppListArrayAdapter(this, getApplicationContext(), apps2));
		// restore
		this.listview.setSelectionFromTop(index, top);
		
	}
	
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//language
		Api.updateLanguage(getApplicationContext(), G.locale());
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.menu_bar, menu);
		
		// Get widget's instance
		mainMenu = menu;
	    return true;
	}
	
	public void menuSetApplyOrSave(Menu menu, boolean isEnabled) {
		if (menu == null) {
			return;
		}
		MenuItem onoff = menu.findItem(R.id.menu_toggle);
		MenuItem apply = menu.findItem(R.id.menu_apply);

		if (isEnabled) {
			onoff.setTitle(R.string.fw_disabled).setIcon(R.drawable.notification_error);
			apply.setTitle(R.string.applyrules);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					getSupportActionBar().setIcon(R.drawable.notification);
				}
			});
			
			
		} else {
			onoff.setTitle(R.string.fw_enabled).setIcon(R.drawable.notification);
			apply.setTitle(R.string.saverules);
			runOnUiThread(new Runnable() {
			     @Override
			     public void run() {
			    	 getSupportActionBar().setIcon(R.drawable.notification_error);
			    }
			});
			
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//language
		menuSetApplyOrSave(menu, Api.isEnabled(this));
		Api.updateLanguage(getApplicationContext(), G.locale());
		return super.onPrepareOptionsMenu(menu);
	}
	
	private void disableOrEnable() {
		final boolean enabled = !Api.isEnabled(this);
		Api.setEnabled(this, enabled, true);
		if (enabled) {
			applyOrSaveRules();
		} else {
			if (G.enableConfirm()) {
				confirmDisable();
			} else {
				purgeRules();
			}
		}
		refreshHeader();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		
		/*case android.R.id.home:
			disableOrEnable();
	        return true;*/
		case R.id.menu_toggle:
			disableOrEnable();
			return true;
		case R.id.menu_apply:
			applyOrSaveRules();
			return true;
		case R.id.menu_exit:
			finish();
			System.exit(0);
			return false;
		case R.id.menu_help:
			showAbout();
			return true;
		/*case R.id.menu_setpwd:
			setPassword();
			return true;*/
		case R.id.menu_log:
			showLog();
			return true;
		case R.id.menu_rules:
			showRules();
			return true;
		case R.id.menu_setcustom:
			setCustomScript();
			return true;
		case R.id.menu_preference:
			showPreferences();
			return true;
		/*case R.id.menu_reload:
			Api.applications = null;
			showOrLoadApplications();
			return true;*/
		case R.id.menu_search:


			item.setActionView(R.layout.searchbar);
			final EditText filterText = (EditText) item.getActionView().findViewById(
					R.id.searchApps);
			filterText.addTextChangedListener(filterTextWatcher);
			filterText.setEllipsize(TruncateAt.END);
			filterText.setSingleLine();

			MenuItemCompat.setOnActionExpandListener(item, new MenuItemCompat.OnActionExpandListener() {
				@Override
				public boolean onMenuItemActionCollapse(MenuItem item) {
					// Do something when collapsed
					return true;  // Return true to collapse action view
				}

				@Override
				public boolean onMenuItemActionExpand(MenuItem item) {
					filterText.post(new Runnable() {
						@Override
						public void run() {
							filterText.requestFocus();
							InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
							imm.showSoftInput(filterText, InputMethodManager.SHOW_IMPLICIT);
						}
					});
					return true;  // Return true to expand action view
				}
			});

			return true;
		case R.id.menu_export:

			new MaterialDialog.Builder(this)
					.title(R.string.exports)
					.items(new String[]{
							getString(R.string.export_rules),
							getString(R.string.export_all)})
					.itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
						@Override
						public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
							switch (which) {
								case 0:
									Api.saveSharedPreferencesToFileConfirm(MainActivity.this);
									break;
								case 1:
									Api.saveAllPreferencesToFileConfirm(MainActivity.this);
									break;
							}
							return true;
						}
					}).positiveText(R.string.exports)
					.negativeText(R.string.Cancel)
					.show();
			return true;
		case R.id.menu_import:

			new MaterialDialog.Builder(this)
					.title(R.string.imports)
					.items(new String[]{
							getString(R.string.import_rules),
							getString(R.string.import_all),
							getString(R.string.import_rules_droidwall)})
					.itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
						@Override
						public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
							switch (which) {
								case 0:
									Intent intent = new Intent(MainActivity.this, FileChooserActivity.class);
									startActivityForResult(intent, FILE_CHOOSER_LOCAL);
									break;
								case 1:

									if (Api.getCurrentPackage(MainActivity.this).equals("dev.ukanth.ufirewall.donate") || G.isDo()) {
										Intent intent2 = new Intent(MainActivity.this, FileChooserActivity.class);
										startActivityForResult(intent2, FILE_CHOOSER_ALL);
										//dialogImport.dismiss();
									} else {

										new MaterialDialog.Builder(MainActivity.this)
												.title(R.string.buy_donate)
												.content(R.string.donate_only)
												.positiveText(R.string.buy_donate)
												.negativeText(R.string.close)
												.icon(getResources().getDrawable(R.drawable.ic_launcher))
												.callback(new MaterialDialog.ButtonCallback() {
													@Override
													public void onPositive(MaterialDialog dialog) {
														Intent intent = new Intent(Intent.ACTION_VIEW);
														intent.setData(Uri.parse("market://details?id=dev.ukanth.ufirewall.donate"));
														startActivity(intent);
													}

													@Override
													public void onNegative(MaterialDialog dialog) {
														dialog.cancel();
													}
												})
												.show();
									}
									break;
								case 2:

									new MaterialDialog.Builder(MainActivity.this)
											.title(R.string.import_rules_droidwall)
											.content(R.string.overrideRules)
											.positiveText(R.string.Yes)
											.negativeText(R.string.No)
											.icon(getResources().getDrawable(R.drawable.ic_launcher))
											.callback(new MaterialDialog.ButtonCallback() {
												@Override
												public void onPositive(MaterialDialog dialog) {
													if (ImportApi.loadSharedPreferencesFromDroidWall(MainActivity.this)) {
														Api.applications = null;
														showOrLoadApplications();
														Api.toast(MainActivity.this, getString(R.string.import_rules_success) + Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/");
													} else {
														Api.toast(MainActivity.this, getString(R.string.import_rules_fail));
													}
												}

												@Override
												public void onNegative(MaterialDialog dialog) {
													dialog.cancel();
												}
											})
											.show();


									break;
							}
							return true;
						}
					})
					.positiveText(R.string.imports)
					.negativeText(R.string.Cancel)
					.show();

			return true;
		default:
	        return super.onOptionsItemSelected(item);
		}
	}

	private TextWatcher filterTextWatcher = new TextWatcher() {

		public void afterTextChanged(Editable s) {
			showApplications(s.toString(),-1,false);
		}


			public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			showApplications(s.toString(),-1,false);
		}

	};

	private void showPreferences() {
		Intent i = new Intent(this, PreferencesActivity.class);
        startActivityForResult(i,PREFERENCE_RESULT);
	}
	
	private void showAbout() {
		Intent i = new Intent(this, HelpActivity.class);
		startActivityForResult(i, SHOW_ABOUT_RESULT);
	}

	public void confirmDisable(){

		new MaterialDialog.Builder(this)
				.title(R.string.confirmMsg)
				//.content(R.string.confirmMsg)
				.positiveText(R.string.Yes)
				.negativeText(R.string.No)
				.callback(new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						purgeRules();
						if (G.activeNotification()) {
							Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
						}
						dialog.dismiss();
					}

					@Override
					public void onNegative(MaterialDialog dialog) {
						Api.setEnabled(getApplicationContext(), true, true);
						dialog.dismiss();
					}
				})
				.show();
	}

	/**
	 * Set a new init script
	 */
	private void setCustomScript() {
		Intent intent = new Intent();
		intent.setClass(this, CustomScriptActivity.class);
		startActivityForResult(intent, SHOW_CUSTOM_SCRIPT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		//checkForProfile = false;
		
		switch(requestCode) {
			/*case REQ_CREATE_PATTERN: {
				if(G.usePatterns()){
					if (resultCode == RESULT_OK) {
						char[] pattern = data.getCharArrayExtra(
				                    EXTRA_PATTERN);
			    		final Editor editor = G.sPrefs.edit();
		    			editor.putString("LockPassword", new String(pattern));
		    			editor.commit();
					}
					break;
				}
			}
			break;*/
			
			case REQ_ENTER_PATTERN: {
				if(G.usePatterns()){
				switch (resultCode) {
					case RESULT_OK:
						//isPassVerify = true;
						showOrLoadApplications();
						break;
					case RESULT_CANCELED:
						MainActivity.this.finish();
						android.os.Process.killProcess(android.os.Process.myPid());
						break;
					case RESULT_FAILED:
						MainActivity.this.finish();
						android.os.Process.killProcess(android.os.Process.myPid());
						break;
					case RESULT_FORGOT_PATTERN:
						break;
					}
				}
			}
            break;
            case PREFERENCE_RESULT:
				reloadPreferences();
				showOrLoadApplications();
				/*Intent intent = getIntent();
				finish();
				startActivity(intent);*/
               	break;

			case FILE_CHOOSER_LOCAL:
				if (resultCode == RESULT_OK) {
					String fileSelected = data.getStringExtra(Constants.KEY_FILE_SELECTED);
					StringBuilder builder = new StringBuilder();
					if(Api.loadSharedPreferencesFromFile(MainActivity.this,builder,fileSelected)){
						Api.applications = null;
						showOrLoadApplications();
						Api.toast(MainActivity.this, getString(R.string.import_rules_success) +  fileSelected);
					} else {
						if (builder.toString().equals("")) {
							Api.toast(MainActivity.this, getString(R.string.import_rules_fail));
						} else {
							Api.toast(MainActivity.this,builder.toString());
						}
					}
				}
			break;

			case FILE_CHOOSER_ALL:
				if (resultCode == RESULT_OK) {
					String fileSelected = data.getStringExtra(Constants.KEY_FILE_SELECTED);
					StringBuilder builder = new StringBuilder();
					if(Api.loadAllPreferencesFromFile(MainActivity.this, builder,fileSelected)){
						Api.applications = null;
						showOrLoadApplications();
						Api.toast(MainActivity.this, getString(R.string.import_rules_success) + fileSelected);
						Intent intent = getIntent();
						finish();
						startActivity(intent);
					} else {
						if(builder.toString().equals("")) {
							Api.toast(MainActivity.this, getString(R.string.import_rules_fail));
						} else {
							Api.toast(MainActivity.this,builder.toString());
						}
					}
				}
			break;
		}
		
		if (resultCode == RESULT_OK
				&& data != null && Api.CUSTOM_SCRIPT_MSG.equals(data.getAction())) {
			final String script = data.getStringExtra(Api.SCRIPT_EXTRA);
			final String script2 = data.getStringExtra(Api.SCRIPT2_EXTRA);
			setCustomScript(script, script2);
		}
		//
	}

	/**
	 * Set a new init script
	 * 
	 * @param script
	 *            new script (empty to remove)
	 * @param script2
	 *            new "shutdown" script (empty to remove)
	 */
	private void setCustomScript(String script, String script2) {
		final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
		// Remove unnecessary white-spaces, also replace '\r\n' if necessary
		script = script.trim().replace("\r\n", "\n");
		script2 = script2.trim().replace("\r\n", "\n");
		editor.putString(Api.PREF_CUSTOMSCRIPT, script);
		editor.putString(Api.PREF_CUSTOMSCRIPT2, script2);
		int msgid;
		if (editor.commit()) {
			if (script.length() > 0 || script2.length() > 0) {
				msgid = R.string.custom_script_defined;
			} else {
				msgid = R.string.custom_script_removed;
			}
		} else {
			msgid = R.string.custom_script_error;
		}
		Api.displayToasts(MainActivity.this, msgid, Toast.LENGTH_SHORT);
		if (Api.isEnabled(this)) {
			// If the firewall is enabled, re-apply the rules
			applyOrSaveRules();
		}
	}

	/**
	 * Show iptables rules on a dialog
	 */
	private void showRules() {
		Intent i = new Intent(this, RulesActivity.class);
		startActivityForResult(i, SHOW_RULES_ACTIVITY);
	}
	
	/**
	 * Show logs on a dialog
	 */
	private void showLog() {
		Intent i = new Intent(this, LogActivity.class);
		startActivityForResult(i, SHOW_LOGS_ACTIVITY);
	}



	/**
	 * Apply or save iptables rules, showing a visual indication
	 */
	private void applyOrSaveRules() {
		final Resources res = getResources();
		final boolean enabled = Api.isEnabled(this);
		final Context ctx = getApplicationContext();

		Api.saveRules(ctx);
		if (!enabled) {
			Api.setEnabled(ctx, false, true);
			Api.displayToasts(ctx, R.string.rules_saved, Toast.LENGTH_SHORT);
			setDirty(false);
			return;
		}


		final MaterialDialog progress = new MaterialDialog.Builder(this)
				.title(R.string.working)
				.content(enabled ? R.string.applying_rules
						: R.string.saving_rules)
				.progress(true, 0)
				.show();


		Api.applySavedIptablesRules(ctx, true, new RootCommand()
				.setSuccessToast(R.string.rules_applied)
				.setFailureToast(R.string.error_apply)
				.setReopenShell(true)
				.setCallback(new RootCommand.Callback() {

					public void cbFunc(RootCommand state) {
						try {
							progress.dismiss();
						} catch (Exception ex) {
						}

						boolean result = enabled;

						if (state.exitCode == 0) {
							setDirty(false);
						} else {
							result = false;
						}
						menuSetApplyOrSave(MainActivity.this.mainMenu, result);
						Api.setEnabled(ctx, result, true);
					}
				}));
	}

	/**
	 * Purge iptables rules, showing a visual indication
	 */
	private void purgeRules() {
		final Context ctx = getApplicationContext();

		Api.purgeIptables(ctx, true, new RootCommand()
				.setSuccessToast(R.string.rules_deleted)
				.setFailureToast(R.string.error_purge)
				.setReopenShell(true)
				.setCallback(new RootCommand.Callback() {

			public void cbFunc(RootCommand state) {
				// error exit -> assume the rules are still enabled
				// we shouldn't wind up in this situation, but if we do, the user's
				// best bet is to click Apply then toggle Enabled again
				boolean nowEnabled = state.exitCode != 0;

				Api.setEnabled(ctx, nowEnabled, true);
				menuSetApplyOrSave(MainActivity.this.mainMenu, nowEnabled);
			}
		}));
	}


	@Override
	public void onClick(View v) {
		
		switch (v.getId()) {
		case R.id.label_mode:
			selectMode();
			break;
		case R.id.img_wifi:
			selectActionConfirmation(v.getId());
			break;
		case R.id.img_3g:
			selectActionConfirmation(v.getId());
			break;
		case R.id.img_roam:
			selectActionConfirmation(v.getId());
			break;
		case R.id.img_vpn:
			selectActionConfirmation(v.getId());
			break;
		case R.id.img_lan:
			selectActionConfirmation(v.getId());
			break;
		case R.id.img_invert:
			 selectActionConfirmation(getString(R.string.reverse_all), v.getId());
			break;
		case R.id.img_reset:
			 selectActionConfirmation(getString(R.string.unselect_all), v.getId());
			break;
		//case R.id.img_invert:
		//	revertApplications();
		//	break;
		}
	}

	private void selectAllLAN(boolean flag) {
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if(adapter !=null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_lan = flag;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}

	private void selectAllVPN(boolean flag) {
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if(adapter !=null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_vpn = flag;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}

	private void selectRevert(int flag){
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if (adapter != null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					switch (flag) {
					case R.id.img_wifi:
						data.selected_wifi = !data.selected_wifi;
						break;
					case R.id.img_3g:
						data.selected_3g = !data.selected_3g;
						break;
					case R.id.img_roam:
						data.selected_roam = !data.selected_roam;
						break;
					case R.id.img_vpn:
						data.selected_vpn = !data.selected_vpn;
						break;
					case R.id.img_lan:
						data.selected_lan = !data.selected_lan;
						break;
					}
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}
	
	private void selectRevert() {
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if (adapter != null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_wifi = !data.selected_wifi;
					data.selected_3g = !data.selected_3g;
					data.selected_roam = !data.selected_roam;
					data.selected_vpn = !data.selected_vpn;
					data.selected_lan = !data.selected_lan;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}
	
	
	private void selectAllRoam(boolean flag){
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if(adapter !=null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_roam = flag;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}
	
	private void clearAll(){
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if(adapter !=null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item); 
				data.selected_wifi = false;
				data.selected_3g = false;
				data.selected_roam = false;
				data.selected_vpn = false;
				data.selected_lan = false;
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}

	private void selectAll3G(boolean flag) {
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		if(adapter !=null) {
			int count = adapter.getCount(), item;
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_3g = flag;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
		
	}

	private void selectAllWifi(boolean flag) {
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		ListAdapter adapter = listview.getAdapter();
		int count = adapter.getCount(), item;
		if(adapter !=null) {
			for (item = 0; item < count; item++) {
				PackageInfoData data = (PackageInfoData) adapter.getItem(item);
				if(data.uid != Api.SPECIAL_UID_ANY) {
					data.selected_wifi = flag;
				}
				setDirty(true);
			}
			((BaseAdapter) adapter).notifyDataSetChanged();
		}
	}
 	
	@Override
	public boolean onKeyUp(final int keyCode, final KeyEvent event) {
		
		if (event.getAction() == KeyEvent.ACTION_UP)
        {
			switch (keyCode) {
			case KeyEvent.KEYCODE_MENU:
				if(mainMenu != null){
					mainMenu.performIdentifierAction(R.id.menu_list_item, 0);
					return true;
				}
			}
        }
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		
		// Handle the back button when dirty
		if (isDirty() && (keyCode == KeyEvent.KEYCODE_BACK)) {
			new MaterialDialog.Builder(this)
					.title(R.string.confirmation)
					.content(R.string.unsaved_changes_message)
					.positiveText(R.string.apply)
					.negativeText(R.string.discard)
					.callback(new MaterialDialog.ButtonCallback() {
						@Override
						public void onPositive(MaterialDialog dialog) {
							applyOrSaveRules();
							dialog.dismiss();
						}

						@Override
						public void onNegative(MaterialDialog dialog) {
							setDirty(false);
							Api.applications = null;
							finish();
							System.exit(0);
							//force reload rules.
							MainActivity.super.onKeyDown(keyCode, event);
							dialog.dismiss();
						}
					})
					.show();

			return true;
		}
		return super.onKeyDown(keyCode, event);
		
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		
		if(G.enableMultiProfile()){
			//user clicked add  
			if(itemPosition == mLocations.length - 2){
				addProfileDialog();
			}
			//user clicked remove
			else if(itemPosition == mLocations.length - 1){
				//G.removeProfile(itemPosition, mSelected.getText().toString());
				removeProfileDialog();
			} else {
				if(G.setProfile(true, itemPosition)) {
                    (new GetAppList()).setContext(this).execute();
					mSelected.setText("  |  " + mLocations[itemPosition]);
					if(G.applyOnSwitchProfiles()){
						applyOrSaveRules();
					}
					refreshHeader();
				}
			}
		}
		return true;
	}
	
	public void removeProfileDialog() {
	new MaterialDialog.Builder(this)
				.title(R.string.profile_remove)
				.items(G.getAdditionalProfiles().toArray(new String[G.getAdditionalProfiles().size()]))
			.itemsCallbackSingleChoice(-1, new MaterialDialog.ListCallbackSingleChoice() {
				@Override
				public boolean onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
					G.removeAdditionalProfile(mLocations[which + 4], which + 4);
					setupMultiProfile(true);
					Api.applications = null;
					showOrLoadApplications();
					return true;
				}
			})
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onNegative(MaterialDialog dialog) {
					G.storedPosition(0);
					reloadPreferences();
				}
			})
			.positiveText(R.string.apply)
			.negativeText(R.string.Cancel)
				.show();
	}
	
	public void addProfileDialog() {

		new MaterialDialog.Builder(this)
				.title(R.string.profile_add)
				.inputType(InputType.TYPE_CLASS_TEXT)
				.input(R.string.prefill_profile, R.string.profile_default, new MaterialDialog.InputCallback() {
					@Override
					public void onInput(MaterialDialog dialog, CharSequence input) {
						String value = input.toString();
						if (value != null && value.length() > 0 && !value.contains(",")) {
							G.addAdditionalProfile(value.trim());
							setupMultiProfile(true);
						} else {
							Toast.makeText(getApplicationContext(), getString(R.string.invalid_profile), Toast.LENGTH_SHORT).show();
						}
					}
				})
				.callback(new MaterialDialog.ButtonCallback() {
					@Override
					public void onNegative(MaterialDialog dialog) {
						reloadPreferences();
					}
				})
				.negativeText(R.string.Cancel)
				.show();
	}
	
   /**
	* 
	* @param i
    */
	
	private void selectActionConfirmation(String displayMessage, final int i){

		new MaterialDialog.Builder(this)
				.title(R.string.confirmation).content(displayMessage)
				.positiveText(R.string.OK)
				.negativeText(R.string.Cancel)
				.callback(new MaterialDialog.ButtonCallback() {
					@Override
					public void onPositive(MaterialDialog dialog) {
						switch (i) {
							case R.id.img_invert:
								selectRevert();
								break;
							case R.id.img_reset:
								clearAll();
						}
						dialog.dismiss();
					}

					@Override
					public void onNegative(MaterialDialog dialog) {
						dialog.dismiss();
					}
				})
				.show();
	}

	private void selectActionConfirmation(final int i) {

		new MaterialDialog.Builder(this)
				.title(R.string.select_action)
				.items(new String[]{
						getString(R.string.check_all),
						getString(R.string.invert_all),
						getString(R.string.uncheck_all)})
				.itemsCallback(new MaterialDialog.ListCallback() {
					@Override
					public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
						switch (which) {
							case 0:
								switch (i) {
									case R.id.img_wifi:
										dialog.setTitle(text + getString(R.string.wifi));
										selectAllWifi(true);
										break;
									case R.id.img_3g:
										dialog.setTitle(text + getString(R.string.data));
										selectAll3G(true);
										break;
									case R.id.img_roam:
										dialog.setTitle(text + getString(R.string.roam));
										selectAllRoam(true);
										break;
									case R.id.img_vpn:
										dialog.setTitle(text + getString(R.string.vpn));
										selectAllVPN(true);
										break;
									case R.id.img_lan:
										dialog.setTitle(text + getString(R.string.lan));
										selectAllLAN(true);
										break;
								}
								break;
							case 1:
								switch (i) {
									case R.id.img_wifi:
										dialog.setTitle(text + getString(R.string.wifi));
										break;
									case R.id.img_3g:
										dialog.setTitle(text + getString(R.string.data));
										break;
									case R.id.img_roam:
										dialog.setTitle(text + getString(R.string.roam));
										break;
									case R.id.img_vpn:
										dialog.setTitle(text + getString(R.string.vpn));
										break;
									case R.id.img_lan:
										dialog.setTitle(text + getString(R.string.lan));
										break;
								}
								selectRevert(i);
								dirty = true;
								break;
							case 2:
								switch (i) {
									case R.id.img_wifi:
										dialog.setTitle(text + getString(R.string.wifi));
										selectAllWifi(false);
										break;
									case R.id.img_3g:
										dialog.setTitle(text + getString(R.string.data));
										selectAll3G(false);
										break;
									case R.id.img_roam:
										dialog.setTitle(text + getString(R.string.roam));
										selectAllRoam(false);
										break;
									case R.id.img_vpn:
										dialog.setTitle(text + getString(R.string.vpn));
										selectAllVPN(false);
										break;
									case R.id.img_lan:
										dialog.setTitle(text + getString(R.string.lan));
										selectAllLAN(false);
										break;
								}
								break;
						}
					}
				}).show();
	}

	/*@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		switch (checkedId) {
			case R.id.rpkg_all:
				showOrLoadApplications();
				break;
			case R.id.rpkg_core:
				showApplications(null, 0,false);
				break;
			case R.id.rpkg_sys:
				showApplications(null, 1,false);
				break;
			case R.id.rpkg_user:
				showApplications(null, 2,false);
				break;
			}
	}*/
	
	private class Startup extends AsyncTask<Void, Void, Void> {
        private MaterialDialog dialog = null;
        private Context context = null;
        private boolean suAvailable = false;

        public Startup setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            // We're creating a progress dialog here because we want the user to wait.
            // If in your app your user can just continue on with clicking other things,
            // don't do the dialog thing.

			dialog = new MaterialDialog.Builder(context).
					title(getString(R.string.su_check_title)).progress(true,0).show();
			dialog.setContent(context.getString(R.string.su_check_message));
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Let's do some SU stuff
        	suAvailable = Shell.SU.available();
            if (suAvailable) {
            	startRootShell();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        	dialog.dismiss();
        	if(!suAvailable) {
					new MaterialDialog.Builder(MainActivity.this)
						.title(R.string.error_common)
						.content(R.string.error_su)
						.positiveText(R.string.OK)
						.callback(new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog dialog) {
								MainActivity.this.finish();
								android.os.Process.killProcess(android.os.Process.myPid());
								dialog.dismiss();
							}
						})
						.show();
			} else {
        		if(passCheck()){
           	    	showOrLoadApplications();
           	    }	
        	}
        }		
    }
	
}

