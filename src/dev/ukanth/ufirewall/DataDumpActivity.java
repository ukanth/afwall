/**
 * Common framework for LogActivity and RulesActivity
 * 
 * Copyright (C) 2011-2012  Umakanthan Chandran
 * Copyright (C) 2011-2013  Kevin Cernekee
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.WindowCompat;
import android.support.v7.app.ActionBarActivity;

import dev.ukanth.ufirewall.Log;

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.EditText;
import android.widget.Toast;

public abstract class DataDumpActivity extends ActionBarActivity {

	public static final String TAG = "AFWall";

	protected static final int MENU_TOGGLE = -3;
	protected static final int MENU_COPY = 16;
	protected static final int MENU_EXPORT_LOG = 17;
	protected static final int MENU_REFRESH = 13;

	protected Menu mainMenu;
	protected String dataText;

	// to be filled in by subclasses
	protected String sdDumpFile;
	protected abstract void populateMenu(SubMenu sub);
	protected abstract void populateData(final Context ctx);

	protected void setData(String data) {
		this.dataText = data;
		((EditText)findViewById(R.id.rules)).setText(data);
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        setContentView(R.layout.rules);

        // Load partially transparent black background
        getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.ab_bg_black));

        ((EditText)findViewById(R.id.rules)).setKeyListener(null);
        setData("");
        populateData(this);

		Api.updateLanguage(getApplicationContext(), G.locale());
    }

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	// Common options: Copy, Export to SD Card, Refresh
		SubMenu sub = menu.addSubMenu(0, MENU_TOGGLE, 0, "").setIcon(R.drawable.abc_ic_menu_moreoverflow_normal_holo_dark);

		sub.add(0, MENU_COPY, 0, R.string.copy).setIcon(R.drawable.copy);
		sub.add(0, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.exportr);
		sub.add(0, MENU_REFRESH, 0, R.string.refresh).setIcon(R.drawable.reload);
		populateMenu(sub);

		MenuItemCompat.setShowAsAction(sub.getItem(), MenuItemCompat.SHOW_AS_ACTION_ALWAYS
		        | MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
        super.onCreateOptionsMenu(menu);
		mainMenu = menu;
	    return true;
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_COPY:
                copy();
                return true;
            case MENU_EXPORT_LOG:
                exportToSD();
                return true;
            case MENU_REFRESH:
                populateData(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void exportToSD() {
    	final Context ctx = this;

    	new AsyncTask<Void, Void, Boolean>() {
    		public String filename = "";

    		@Override
    		public Boolean doInBackground(Void... args) {
			    FileOutputStream output = null;
			    boolean res = false;

    		    try {
    			    File sdCard = Environment.getExternalStorageDirectory();
    			    File dir = new File (sdCard.getAbsolutePath() + "/afwall/");
    			    dir.mkdirs();

    			    File file = new File(dir, sdDumpFile);
    		        output = new FileOutputStream(file);

    		        output.write(dataText.getBytes());
    		        filename = file.getAbsolutePath();
    		        res = true;
    		    } catch (FileNotFoundException e) {
    		        e.printStackTrace();
    		    } catch (IOException e) {
    		        e.printStackTrace();
    		    } finally {
    		        try {
    		            if (output != null) {
    		                output.flush();
    		                output.close();
    		            }
    		        } catch (IOException ex) {
    		            ex.printStackTrace();
    		        }
    		    }
    			return res;
    		}

    		@Override
    		public void onPostExecute(Boolean res) {
    		    if (res == true) {
    				 Api.displayToasts(ctx,
    						 getString(R.string.export_rules_success) + filename,
    						 Toast.LENGTH_LONG);
    		    } else {
    				 Api.displayToasts(ctx, R.string.export_logs_fail,
    						 Toast.LENGTH_LONG);
    		    }
    		}
    	}.execute();
	}

	@SuppressWarnings("deprecation")
	@SuppressLint({ "NewApi", "NewApi", "NewApi", "NewApi" })
	private void copy() {
		try {
			int sdk = android.os.Build.VERSION.SDK_INT;
			EditText rulesText = (EditText) findViewById(R.id.rules);
			if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
			    android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			    clipboard.setText(rulesText.getText().toString());
			} else {
			    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
			    android.content.ClipData clip = android.content.ClipData.newPlainText("", rulesText.getText().toString());
			    clipboard.setPrimaryClip(clip);
			}
			Api.displayToasts(this, R.string.copied, Toast.LENGTH_SHORT);
		} catch(Exception e ){
			Log.d("AFWall+", "Exception in Clipboard" + e);
		}
		Api.displayToasts(this, R.string.copied, Toast.LENGTH_SHORT);
	}
}
