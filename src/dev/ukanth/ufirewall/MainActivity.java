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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.ActionBarSherlock.OnCreateOptionsMenuListener;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnActionExpandListener;
import com.haibison.android.lockpattern.LockPatternActivity;
import com.haibison.android.lockpattern.util.Settings;

import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.RootShell.RootCommand;
import dev.ukanth.ufirewall.preferences.PreferencesActivity;
 
/**
 * Main application activity. This is the screen displayed when you open the
 * application
 */

public class MainActivity extends SherlockListActivity implements OnClickListener,
					ActionBar.OnNavigationListener,OnCreateOptionsMenuListener, OnCheckedChangeListener  {

	private TextView mSelected;
    private String[] mLocations;
	private Menu mainMenu;
	
	private static final int SHOW_PREFERENCE_RESULT = 10012;
	
	public boolean isOnPause = false;
	
	/** progress dialog instance */
	private ListView listview = null;
	/** indicates if the view has been modified and not yet saved */
	public static boolean dirty = false;
	
	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		MainActivity.dirty = dirty;
	}

	private String currentPassword = "";
	
	public String getCurrentPassword() {
		return currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}
	
	private static final int REQ_CREATE_PATTERN = 9877;
	private static final int REQ_ENTER_PATTERN = 9755;
	private boolean isPassVerify = false;
	
	ProgressDialog plsWait;
	
	ArrayAdapter<String> spinnerAdapter;
	
	private int index;
	private int top;
	
	private List<String> mlocalList = new ArrayList<String>();
	
	/** Called when the activity is first created
	 * . */
	@Override
	public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
			isOnPause = false;
			
			if (getIntent().getBooleanExtra("EXIT", false)) {
			    this.finish();
			    android.os.Process.killProcess(android.os.Process.myPid());
			}

			try {
				/* enable hardware acceleration on Android >= 3.0 */
				final int FLAG_HARDWARE_ACCELERATED = WindowManager.LayoutParams.class
						.getDeclaredField("FLAG_HARDWARE_ACCELERATED").getInt(null);
				getWindow().setFlags(FLAG_HARDWARE_ACCELERATED,
						FLAG_HARDWARE_ACCELERATED);
			} catch (Exception e) {
			}
			checkPreferences();
			
			 //language
		    Api.updateLanguage(getApplicationContext(), G.locale());
		    
			setContentView(R.layout.main);
			//set onclick listeners
			this.findViewById(R.id.label_mode).setOnClickListener(this);
			this.findViewById(R.id.img_wifi).setOnClickListener(this);
			this.findViewById(R.id.img_reset).setOnClickListener(this);
			this.findViewById(R.id.img_invert).setOnClickListener(this);
			
			if(G.disableIcons()){
				this.findViewById(R.id.imageHolder).setVisibility(View.GONE);
			}
			
			if(G.showFilter()) {
				this.findViewById(R.id.appFilterGroup).setVisibility(View.VISIBLE);
			}
			if(G.enableRoam()){
				addColumns(R.id.img_roam);
			}
			
			if(G.enableVPN()){
				addColumns(R.id.img_vpn);
			}
			
			if(!Api.isMobileNetworkSupported(getApplicationContext())){
				ImageView view = (ImageView)this.findViewById(R.id.img_3g);
				view.setVisibility(View.GONE);
			} else {
				this.findViewById(R.id.img_3g).setOnClickListener(this);
			}

			if(G.enableLAN()){
				addColumns(R.id.img_lan);
			}

			/**/
			
			updateRadioFilter();
	        
	        //start logging
	       /* if(G.enableLogService()) {
	        	Intent intent = new Intent(getApplicationContext(), LogService.class);
				getApplicationContext().startService(intent);
	        }*/
	        
        	Settings.Display.setStealthMode(getApplicationContext(), G.enableStealthPattern());
	        Settings.Display.setMaxRetries(getApplicationContext(), G.getMaxPatternTry());
	       
		    Api.assertBinaries(this, true);
		    
		   
			plsWait = new ProgressDialog(this);
	        plsWait.setCancelable(false);
		    
	        checkforRoot();
	        
	        setupMultiProfile(true);
		    
	}
	
	
	private void checkforRoot() {
		//check for root
        new Startup().execute();
	}

	private void updateRadioFilter() {
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.appFilterGroup);
		radioGroup.setOnCheckedChangeListener(this);
	}

	private void selectFilterGroup() {
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.appFilterGroup);
		switch (radioGroup.getCheckedRadioButtonId()) {
		case R.id.rpkg_core:
			showApplications(null, 0);
			break;
		case R.id.rpkg_sys:
			showApplications(null, 1);
			break;
		case R.id.rpkg_user:
			showApplications(null, 2);
			break;
		default:
			showApplications("",-1);
			break;
		}
	}
	

	private void updateIconStatus() {
		if(Api.isEnabled(getApplicationContext())) {
			getSupportActionBar().setIcon(R.drawable.widget_on);
		} else {
			getSupportActionBar().setIcon(R.drawable.widget_off);
		}
	}
	
	private void startRootShell() {
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean hasRoot = prefs.getBoolean("hasRoot", false);
		
		if(hasRoot) {
			List<String> cmds = new ArrayList<String>();
			cmds.add("true");
			new RootCommand().setFailureToast(R.string.error_su)
				.setReopenShell(true).run(getApplicationContext(), cmds);
		} 
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		// to improve responsiveness, try to open a root shell in the background on launch
		// (if this fails we'll try again later)
		startRootShell();
		
		if (this.listview == null) {
			this.listview = (ListView) this.findViewById(R.id.listview);
		}
		
		//verifyMultiProfile();
		refreshHeader();
		updateIconStatus();

		NotificationManager mNotificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(Api.NOTIFICATION_ID);
		
		if(passCheck()){
	    	showOrLoadApplications();
	    }
		
	}

	private void addColumns(int id) {
		ImageView view = (ImageView)this.findViewById(id);
		view.setVisibility(View.VISIBLE);
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
			
		    spinnerAdapter =  new ArrayAdapter<String>(this,R.layout.sherlock_spinner_item,
		    	    mLocations);
		    spinnerAdapter.setDropDownViewResource(R.layout.sherlock_spinner_dropdown_item);
	
			getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
			getSupportActionBar().setListNavigationCallbacks(spinnerAdapter, this);
			
			if(position > -1) {
				getSupportActionBar().setSelectedNavigationItem(position);
				getSupportActionBar().setDisplayShowTitleEnabled(false);
				mSelected.setText("  |  " + mLocations[position]);
			}
			getSupportActionBar().setDisplayUseLogoEnabled(true);
		}
	}
	
	private boolean passCheck(){
		
		//wait for 30 seconds before prompt for password again.
	    //if (System.currentTimeMillis() - mLastPause > 30000) {
		if(!isOnPause){
	        // If more than 5 seconds since last pause, prompt for password
	    	if(G.usePatterns()){
				if(!isPassVerify){
					final String pwd = G.sPrefs.getString("LockPassword", "");
					if (pwd.length() == 0) {
						return true;
					} else {
						// Check the password
						requestPassword(pwd);
					}
				}else {
					return true;
				}	
			} else{
				final String oldpwd = G.pPrefs.getString(Api.PREF_PASSWORD, "");
				if (oldpwd.length() == 0) {
					return true;
				} else {
					// Check the password
					requestPassword(oldpwd);	
				}	
			}
	    } else {
	    	return true;
	    }
		return false;
	}

	@Override
	protected void onPause() {
		super.onPause();
		//this.listview.setAdapter(null);
		//mLastPause = Syst em.currentTimeMillis();
		isOnPause = true;
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
		labelmode.setText(res.getString(R.string.mode_header,res.getString(resid)));
	}

	/**
	 * Displays a dialog box to select the operation mode (black or white list)
	 */
	private void selectMode() {
		final Resources res = getResources();
		new AlertDialog.Builder(this)
				.setItems(
						new String[] { res.getString(R.string.mode_whitelist),
								res.getString(R.string.mode_blacklist) },
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								final String mode = (which == 0 ? Api.MODE_WHITELIST: Api.MODE_BLACKLIST);
								final Editor editor = getSharedPreferences(Api.PREFS_NAME, 0).edit();
								editor.putString(Api.PREF_MODE, mode);
								editor.commit();
								refreshHeader();
							}
						}).setTitle("Select mode:").show();
	}
	
	/**
	 * Set a new password lock
	 * 
	 * @param pwd
	 *            new password (empty to remove the lock)
	 */
	private void setPassword(String pwd) {
		final Resources res = getResources();
		final Editor editor = G.pPrefs.edit();
		editor.putString(Api.PREF_PASSWORD, pwd);
		String msg;
		if (editor.commit()) {
			if (pwd.length() > 0) {
				msg = res.getString(R.string.passdefined);
			} else {
				msg = res.getString(R.string.passremoved);
			}
		} else {
			msg = res.getString(R.string.passerror);
		}
		Api.displayToasts(getApplicationContext(), msg, Toast.LENGTH_SHORT);
	}



	/**
	 * Request the password lock before displayed the main screen.
	 */
	private void requestPassword(final String pwd) {

		if(G.usePatterns()){
			Intent intent = new Intent(LockPatternActivity.ACTION_COMPARE_PATTERN, null, getApplicationContext(), LockPatternActivity.class);
			String savedPattern  = G.sPrefs.getString("LockPassword", "");
			intent.putExtra(LockPatternActivity.EXTRA_PATTERN, savedPattern.toCharArray());
			startActivityForResult(intent, REQ_ENTER_PATTERN);
		}
		else{
			new PassDialog(this, false, new android.os.Handler.Callback() {
				public boolean handleMessage(Message msg) {
					if (msg.obj == null) {
						MainActivity.this.finish();
						android.os.Process.killProcess(android.os.Process.myPid());
						return false;
					}
					if (!pwd.equals(msg.obj)) {
						requestPassword(pwd);
						return false;
					} 
					// Password correct
					showOrLoadApplications();
					return true;
				}
			}).show();
				
		}
		
	}


	/**
	 * If the applications are cached, just show them, otherwise load and show
	 */
	private void showOrLoadApplications() {
		//nocache!!
		new GetAppList().execute();	
	}
	

	public class GetAppList extends AsyncTask<Void, Integer, Void> {

		boolean ready = false;
		boolean started = false;
		Activity mContext = null;
		AsyncTask<Void,Integer,Void> myAsyncTaskInstance = null; 

		@Override
		protected void onPreExecute() {
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
			} catch (Exception e) {
				// nothing
			}
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {

			if (progress[0] == 0) {
				plsWait.setMax(getPackageManager().getInstalledApplications(0)
						.size());
				plsWait.setMessage(getString(R.string.reading_apps));
				plsWait.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				plsWait.show();
			}  else if( progress[0] == -1 ){
			} else {
				 plsWait.setProgress(progress[0]);
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
	
	/**
	 * Show the list of applications
	 */
	private void showApplications(final String searchStr, int flag) {
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
		if(!isResultsFound && flag == -1) {
			apps2 = apps; 
		} else {
			apps2 = searchApp;
		}
		// Sort applications - selected first, then alphabetically
		Collections.sort(apps2, new PackageComparator());	
		
		this.listview.setAdapter(new AppListArrayAdapter(this, getApplicationContext(), apps2));
		// restore
		this.listview.setSelectionFromTop(index, top);
		
	}
	
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//language
		Api.updateLanguage(getApplicationContext(), G.locale());
		super.onCreateOptionsMenu(menu);
		getSupportMenuInflater().inflate(R.menu.menu_bar, menu);
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
			apply.setTitle(R.string.applyrules);
			onoff.setTitle(R.string.fw_disabled).setIcon(R.drawable.widget_off);
			//onoff.setTitle(R.string.fw_enabled).setIcon(R.drawable.widget_on);
			getSupportActionBar().setIcon(R.drawable.widget_on);
		} else {
			apply.setTitle(R.string.saverules);
			onoff.setTitle(R.string.fw_enabled).setIcon(R.drawable.widget_on);
			//onoff.setTitle(R.string.fw_disabled).setIcon(R.drawable.widget_off);
			getSupportActionBar().setIcon(R.drawable.widget_off);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//language
		menuSetApplyOrSave(menu, Api.isEnabled(this));
		Api.updateLanguage(getApplicationContext(), G.locale());
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		
		case android.R.id.home:
			disableOrEnable();
	        return true;
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
		case R.id.menu_setpwd:
			setPassword();
			return true;
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
		case R.id.menu_reload:
			Api.applications = null;
			showOrLoadApplications();
			refreshCache();
			return true;
		case R.id.menu_search:	
			item.setActionView(R.layout.searchbar);
			final EditText filterText = (EditText) item.getActionView().findViewById(
					R.id.searchApps);
			filterText.addTextChangedListener(filterTextWatcher);
			filterText.setEllipsize(TruncateAt.END);
			filterText.setSingleLine();
			
			item.setOnActionExpandListener(new OnActionExpandListener() {
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
			Api.saveSharedPreferencesToFileConfirm(MainActivity.this);
			return true;
		case R.id.menu_import:
			AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
			builder.setMessage(getString(R.string.overrideRules))
			       .setCancelable(false)
			       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   if(Api.loadSharedPreferencesFromFile(MainActivity.this)){
			        		   Api.applications = null;
			        		   showOrLoadApplications();
			        		   Api.alert(MainActivity.this, getString(R.string.import_rules_success) +  Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/");
			        	   } else {
			   				   Api.alert(MainActivity.this, getString(R.string.import_rules_fail));
			   				}
			           }
			       })
			       .setNegativeButton("No", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert2 = builder.create();
			alert2.show();
			return true;
			
		case R.id.menu_import_dw:
			AlertDialog.Builder builder2 = new AlertDialog.Builder(MainActivity.this);
			builder2.setMessage(getString(R.string.overrideRules))
			       .setCancelable(false)
			       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   if(ImportApi.loadSharedPreferencesFromDroidWall(MainActivity.this)){
			        		   Api.applications = null;
			        		   showOrLoadApplications();
			        		   Api.alert(MainActivity.this, getString(R.string.import_rules_success) +  Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/");
			        	   } else {
			   					Api.alert(MainActivity.this, getString(R.string.import_rules_fail));
			   				}
			           }
			       })
			       .setNegativeButton("No", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert3 = builder2.create();
			alert3.show();
			return true;	
			
		default:
	        return super.onOptionsItemSelected(item);
		}
	}
	
	

	private void refreshCache() {
		new AsyncTask<Void, Void, Void>() {
			protected Void doInBackground(Void... params) {
				Api.removeAllUnusedCacheLabel(getApplicationContext());
				return null;
			}
		}.execute();
	}

	private TextWatcher filterTextWatcher = new TextWatcher() {

		public void afterTextChanged(Editable s) {
			showApplications(s.toString(),-1);
		}

		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			showApplications(s.toString(),-1);
		}

	};

	private void showPreferences() {
		Intent i = new Intent(this, PreferencesActivity.class);
		startActivityForResult(i, SHOW_PREFERENCE_RESULT);
		//startActivity(new Intent(this, PrefsActivity.class));
	}
	
	private void showAbout() {
		startActivity(new Intent(this, HelpActivity.class));
	}

	/**
	 * Enables or disables the firewall
	 */
	private void disableOrEnable() {
		final boolean enabled = !Api.isEnabled(this);
		Api.setEnabled(this, enabled,true);
		if (enabled) {
			applyOrSaveRules();
		} else {
			if(G.enableConfirm()){
				confirmDisable();
			} else {
				purgeRules();
			}
		}
		refreshHeader();
	}
	
	

	public void confirmDisable(){
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("Info");
	    builder.setMessage(R.string.confirmMsg)
	           .setCancelable(false)
	           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int id) {
	            	   	purgeRules();
	               }
	           })
	           .setNegativeButton("No", new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int id) {
	            	   //cancel. reset to the default enable state
	            	   Api.setEnabled(getApplicationContext(), true, true);
	                   return;
	               }
	           }).show();
	}

	private void confirmPassword(){
		new PassDialog(this, true, new android.os.Handler.Callback() {
			public boolean handleMessage(Message msg) {
				if (msg.obj != null) {
					if(getCurrentPassword().equals((String) msg.obj)) {
						setPassword((String) msg.obj);	
					} else{
						Api.alert(MainActivity.this,getString(R.string.settings_pwd_not_equal));
					}
				}
				return false;
			}
		}).show();
	}
	
	private AlertDialog resetPassword()
	 {
	    AlertDialog myQuittingDialogBox =new AlertDialog.Builder(this) 
	        //set message, title, and icon
	        .setTitle(getString(R.string.delete))
	        .setMessage(getString(R.string.resetPattern)) 
	        .setPositiveButton(getString(R.string.Yes), new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int whichButton) {
	 	    		final Editor editor = G.sPrefs.edit();
	     			editor.putString("LockPassword", "");
	     			editor.commit();
	            }   
	        })
	        .setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int which) {
	                dialog.dismiss();
	            }
	        })
	        .create();
	        return myQuittingDialogBox;
	    }

	/**
	 * Set a new lock password
	 */
	private void setPassword() {
		if(G.usePatterns()){
			final String pwd = G.sPrefs.getString(
					"LockPassword", "");
			if (pwd.length() != 0) {
				AlertDialog diaBox = resetPassword();
				diaBox.show();
			} else {
				//Intent intent = new Intent(MainActivity.this, LockPatternActivity.class);
				//intent.putExtra(LockPatternActivity._Mode, LockPatternActivity.LPMode.CreatePattern);
				//startActivityForResult(intent, _ReqCreatePattern);
				Intent intent = new Intent(LockPatternActivity.ACTION_CREATE_PATTERN, null,getApplicationContext(), LockPatternActivity.class);
				startActivityForResult(intent, REQ_CREATE_PATTERN);
			}	
		}  else {
			new PassDialog(this, true, new android.os.Handler.Callback() {
				public boolean handleMessage(Message msg) {
					if (msg.obj != null) {
						String getPass = (String) msg.obj;
						if(getPass.length() > 0) {
							setCurrentPassword(getPass);
							confirmPassword();
						} else {
							setPassword(getPass);
						}
					}
					return false;
				}
			}).show();
		}
	}
	/**
	 * Set a new init script
	 */
	private void setCustomScript() {
		Intent intent = new Intent();
		intent.setClass(this, CustomScriptActivity.class);
		startActivityForResult(intent, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == SHOW_PREFERENCE_RESULT && resultCode == RESULT_OK ) {
			Intent intent = getIntent();
		    finish();
			Api.updateLanguage(getApplicationContext(), G.locale());
		    startActivity(intent);
		} else {
			if(G.usePatterns()){
				switch (requestCode) {
				case REQ_CREATE_PATTERN:
					if (resultCode == RESULT_OK) {
						char[] pattern = data.getCharArrayExtra(
				                    LockPatternActivity.EXTRA_PATTERN);
			    		final Editor editor = G.sPrefs.edit();
		    			editor.putString("LockPassword", new String(pattern));
		    			editor.commit();
					}
					break;
					
				case REQ_ENTER_PATTERN: {
					switch (resultCode) {
						case RESULT_OK:
							isPassVerify = true;
							showOrLoadApplications();
							break;
						case RESULT_CANCELED:
							MainActivity.this.finish();
							android.os.Process.killProcess(android.os.Process.myPid());
							break;
						case LockPatternActivity.RESULT_FAILED:
							MainActivity.this.finish();
							android.os.Process.killProcess(android.os.Process.myPid());
							break;
						case LockPatternActivity.RESULT_FORGOT_PATTERN:
							break;
						}
					}
				}
				
			}
		    
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
		startActivity(new Intent(this, RulesActivity.class));
	}
	
	/**
	 * Show logs on a dialog
	 */
	private void showLog() {
		startActivity(new Intent(this, LogActivity.class));
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

		final ProgressDialog progress = ProgressDialog.show(this, res
				.getString(R.string.working), res
				.getString(enabled ? R.string.applying_rules
						: R.string.saving_rules), true);

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
				data.selected_lan = flag;
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
				data.selected_vpn = flag;
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
				data.selected_wifi = !data.selected_wifi;
				data.selected_3g = !data.selected_3g;
				data.selected_roam = !data.selected_roam;
				data.selected_vpn = !data.selected_vpn;
				data.selected_lan = !data.selected_lan;
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
				data.selected_roam = flag;
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
				data.selected_3g = flag;
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
				data.selected_wifi = flag;
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
			final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case DialogInterface.BUTTON_POSITIVE:
						applyOrSaveRules();
						break;
					case DialogInterface.BUTTON_NEGATIVE:
						// Propagate the event back to perform the desired
						// action
						setDirty(false);
						Api.applications = null;
						finish();
						System.exit(0);
						//force reload rules.
						MainActivity.super.onKeyDown(keyCode, event);
						break;
					}
				}
			};
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.unsaved_changes)
					.setMessage(R.string.unsaved_changes_message)
					.setPositiveButton(R.string.apply, dialogClickListener)
					.setNegativeButton(R.string.discard, dialogClickListener)
					.show();
			// Say that we've consumed the event
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
					new GetAppList().execute();
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
	
	private int selectedItem = 0;
	
	public void removeProfileDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(getString(R.string.profile_remove));
		String[] profiles = G.getAdditionalProfiles().toArray(new String[G.getAdditionalProfiles().size()]);
		alert.setSingleChoiceItems(profiles, 0, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				selectedItem = which;
			}
		});
		alert.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
			G.removeAdditionalProfile(mLocations[selectedItem + 4], selectedItem + 4);
			setupMultiProfile(true);
			Api.applications = null;
			showOrLoadApplications();
		  }
		});

		alert.setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
		  public void onClick(DialogInterface dialog, int whichButton) {
		    // Canceled.
		  }
		});
		alert.show();	
	}
	
	public void addProfileDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(getString(R.string.profile_add));

		// Set an EditText view to get user input 
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton(getString(R.string.OK), new DialogInterface.OnClickListener() {
		public void onClick(DialogInterface dialog, int whichButton) {
			String value = input.getText().toString();
			if(value !=null && value.length() > 0) {
				G.addAdditionalProfile(value.trim());
		  		setupMultiProfile(true);
			} 
		  }
		});

		alert.setNegativeButton(getString(R.string.Cancel), new DialogInterface.OnClickListener() {
		  public void onClick(DialogInterface dialog, int whichButton) {
		    // Canceled.
		  }
		});
		alert.show();	
	}
	
   /**
	* 
	* @param i
    */
	
	private void selectActionConfirmation(String displayMessage, final int i){
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setMessage(displayMessage)
				.setCancelable(false)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								switch (i) {
								case R.id.img_invert:
									selectRevert();
									break;
								case R.id.img_reset:
									clearAll();
								}
							}
						})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert2 = builder.create();
		alert2.show();
}
	private void selectActionConfirmation(final int i){
		final Dialog settingsDialog = new Dialog(this); 
		settingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		settingsDialog.getWindow().setBackgroundDrawableResource(R.drawable.class_zero_background);
		final View dialogView = getLayoutInflater().inflate(R.layout.select_action , null);
		settingsDialog.setContentView(dialogView); 
		settingsDialog.show(); 
		
		
		TextView textView = (TextView) dialogView.findViewById(R.id.check_title);
		String text = getString(R.string.select_action) + " ";
		switch (i) {
		case R.id.img_wifi:
			textView.setText( text + getString(R.string.wifi));
			break;
		case R.id.img_3g:
			textView.setText( text +  getString(R.string.data));
			break;
		case R.id.img_roam:
			textView.setText( text +  getString(R.string.roam));
			break;
		case R.id.img_vpn:
			textView.setText( text +  getString(R.string.vpn));
			break;
		case R.id.img_lan:
			textView.setText( text +  getString(R.string.lan));
			break;
		}
		
		
		Button checkAll = (Button) dialogView.findViewById(R.id.checkAll);
		checkAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (i) {
				case R.id.img_wifi:
					selectAllWifi(true);
					break;
				case R.id.img_3g:
					selectAll3G(true);
					break;
				case R.id.img_roam:
					selectAllRoam(true);
					break;
				case R.id.img_vpn:
					selectAllVPN(true);
					break;
				case R.id.img_lan:
					selectAllLAN(true);
					break;
				}
				dirty = true;
				settingsDialog.dismiss();
			}
		});
		
		Button uncheckAll = (Button) dialogView.findViewById(R.id.uncheckAll);
		uncheckAll.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				switch (i) {
				case R.id.img_wifi:
					selectAllWifi(false);
					break;
				case R.id.img_3g:
					selectAll3G(false);
					break;
				case R.id.img_roam:
					selectAllRoam(false);
					break;
				case R.id.img_vpn:
					selectAllVPN(false);
					break;
				case R.id.img_lan:
					selectAllLAN(false);
					break;
				}
				dirty = true;
				settingsDialog.dismiss();
			}
		});
		
		Button invertAll = (Button) dialogView.findViewById(R.id.invertAll);
		invertAll.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				selectRevert(i);
				dirty = true;
				settingsDialog.dismiss();
			}
		});
		
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int checkedId) {
		switch (checkedId) {
			case R.id.rpkg_all:
				
				showOrLoadApplications();
				break;
			case R.id.rpkg_core:
				showApplications(null, 0);
				break;
			case R.id.rpkg_sys:
				showApplications(null, 1);
				break;
			case R.id.rpkg_user:
				showApplications(null, 2);
				break;
			}
	}
	
	private class Startup extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			Api.hasRootAccess(getApplicationContext());
			return null;
		}
	}

	
}

