package dev.ukanth.ufirewall;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.actionbarsherlock.view.Window;

public class Rules extends SherlockActivity {
	
	private static final int MENU_TOGGLE = -3;
	private static final int MENU_CLEARLOG = 7;
	private static final int MENU_FLUSH_RULES = 12;
	private static final int MENU_COPY = 15;
	
	private int viewMode;
	
	public int getViewMode() {
		return viewMode;
	}

	public void setViewMode(int viewMode) {
		this.viewMode = viewMode;
	}

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.rules);
        
        

        //Load partially transparent black background
        getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.ab_bg_black));
        
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.IPTABLE_RULES);
        String title = intent.getStringExtra(MainActivity.VIEW_TITLE);
        setTitle(title);
        
        if(title.equals(getString(R.string.showlog_title))){
        	setViewMode(MENU_CLEARLOG);
        }
        
        if(title.equals(getString(R.string.showrules_title))){
        	setViewMode(MENU_FLUSH_RULES);
        }
        
        final EditText rulesText = (EditText)findViewById(R.id.rules);
        rulesText.setTextColor(Color.WHITE);
        rulesText.setFocusable(false);
        rulesText.setKeyListener(null);
        rulesText.setClickable(false);
        rulesText.setText(message);
        
        // Set the text view as the activity layout
        
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		SubMenu sub = menu.addSubMenu(0, MENU_TOGGLE, 0, "").setIcon(R.drawable.abs__ic_menu_moreoverflow_normal_holo_dark);
		if(getViewMode() == MENU_CLEARLOG) sub.add(0, MENU_CLEARLOG, 0, R.string.clear_log).setIcon(R.drawable.clearlog);
		if(getViewMode() == MENU_FLUSH_RULES) sub.add(0, MENU_FLUSH_RULES, 0, R.string.flush).setIcon(R.drawable.clearlog);
		sub.add(0, MENU_COPY, 0, R.string.copy).setIcon(R.drawable.copy);
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        
       // SearchView searchView = new SearchView(getSupportActionBar().getThemedContext());
       // searchView.setQueryHint("Search for countries…");

       // menu.add("Search").setActionView(searchView).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
		
	    return super.onCreateOptionsMenu(menu);
	}
    
    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	switch (item.getItemId()) {
    	case MENU_CLEARLOG:
			clearLog();
			EditText rulesText = (EditText) findViewById(R.id.rules);
			rulesText.setText(getString(R.string.no_log));
			return true;
    	case MENU_FLUSH_RULES:
			clearRules();
			return true;	
    	case MENU_COPY:
    		copy();
    	default:
	        return super.onOptionsItemSelected(item);
		}
    }
    
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
			MainActivity.displayToasts(Rules.this, R.string.copied,
					Toast.LENGTH_SHORT);
		} catch(Exception e ){
			Log.d("AFWall+", "Exception in Clipboard" + e);
		}
		
		
	}

	private void clearRules() {
		try {
			if (!Api.hasRootAccess(Rules.this, true))
				return;
			if (Api.clearRules(Rules.this)) {
				MainActivity.displayToasts(Rules.this, R.string.flushed,
						Toast.LENGTH_SHORT);
			}
		} catch (IOException e) {
			Api.alert(Rules.this, getString(R.string.error_flush));
		}

	}
    
    /**
	 * Clear logs
	 */
	private void clearLog() {
		final Resources res = getResources();
		final ProgressDialog progress = ProgressDialog.show(this,
				res.getString(R.string.working),
				res.getString(R.string.please_wait), true);
		final Handler handler = new Handler() {
			public void handleMessage(Message msg) {
				try {
					progress.dismiss();
				} catch (Exception ex) {
				}
				if (!Api.hasRootAccess(Rules.this, true))
					return;
				if (Api.clearLog(Rules.this)) {
					MainActivity.displayToasts(Rules.this, R.string.log_cleared,
							Toast.LENGTH_SHORT);
					final EditText rulesText = (EditText)findViewById(R.id.rules);
					rulesText.setText("");
				}
			}
		};
		handler.sendEmptyMessageDelayed(0, 100);
	}

}
