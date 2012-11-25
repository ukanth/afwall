package dev.ukanth.ufirewall;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
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
	private static final int MENU_COPY = 16;
	private static final int MENU_EXPORT_LOG = 17;
	private static final int MENU_INTERFACES = 18;
	
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
		sub.add(0, MENU_COPY, 0, R.string.copy).setIcon(R.drawable.copy);
		if(getViewMode() == MENU_CLEARLOG) {
			sub.add(0, MENU_CLEARLOG, 0, R.string.clear_log).setIcon(R.drawable.clearlog);
		}
		if(getViewMode() == MENU_FLUSH_RULES) {
			sub.add(0, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.exportr);
			sub.add(0, MENU_INTERFACES, 0, R.string.ifaces).setIcon(R.drawable.show);
			sub.add(0, MENU_FLUSH_RULES, 0, R.string.flush).setIcon(R.drawable.clearlog);
		}
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
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
			clearRules(Rules.this);
			return true;	
    	case MENU_COPY:
    		copy();
    		return true;
    	case MENU_INTERFACES:
    		String[] ifaces = Api.showIfaces().split(",");
    		if(ifaces != null && ifaces.length > 0 ){
    			AlertDialog.Builder builder = new AlertDialog.Builder(this);
    			builder.setTitle(R.string.ifaces);
    			builder.setItems(ifaces, new DialogInterface.OnClickListener() {
    			    public void onClick(DialogInterface dialog, int item) {
    			         // Do something with the selection
    			    }
    			});
    			AlertDialog alert = builder.create();
    			alert.show();
    		}
    		return true;
    	case MENU_EXPORT_LOG:
    		if(exportToSD()){
    			 MainActivity.displayToasts(Rules.this, R.string.export_rules_success,
    						Toast.LENGTH_LONG);
    				
    		}else{
    			 MainActivity.displayToasts(Rules.this, R.string.export_logs_fail,
    						Toast.LENGTH_LONG);
    		}
    		return true;
    	default:
	        return super.onOptionsItemSelected(item);
		}
    }
    
   
    private boolean exportToSD() {
	    File sdCard = Environment.getExternalStorageDirectory();
	    File dir = new File (sdCard.getAbsolutePath() + "/afwall/");
	    dir.mkdirs();
	    boolean res = false;
	    File file = new File(dir, "rules.log");
	    
	    
	    ObjectOutputStream output = null;
	    try {
	        output = new ObjectOutputStream(new FileOutputStream(file));
	        EditText rulesText = (EditText) findViewById(R.id.rules);
	        output.writeObject(rulesText.getText().toString());
	        res = true;
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }finally {
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
		MainActivity.displayToasts(Rules.this, R.string.copied,
				Toast.LENGTH_SHORT);
		
	}

	private void clearRules(final Context ctx) {
			AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
			builder.setMessage(getString(R.string.flushRulesConfirm))
			       .setCancelable(false)
			       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	final StringBuilder res = new StringBuilder();
			   			StringBuilder script = new StringBuilder();
			   			if(Api.getIpPath() == null) {
			   				Api.setIpTablePath(ctx);
			   			}
			   			script.append(Api.getIpPath() +" -F\n");
			   			script.append(Api.getIpPath() +" -X\n");
			   			int code = -1;
						try {
							code = Api.runScriptAsRoot(ctx, script.toString(), res);
						} catch (IOException e) {
							Api.alert(Rules.this, getString(R.string.error_flush));
						}
			   			if (code == -1) {
			   				Api.alert(ctx, getString(R.string.error_purge) + code + "\n" + res);
			   			}else {
			   				Api.displayToasts(ctx, R.string.flushed, Toast.LENGTH_SHORT);
			   			}
			           }
			       })
			       .setNegativeButton("No", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();

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
