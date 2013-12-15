package dev.ukanth.ufirewall;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;

public class AppDetailActivity extends SherlockActivity {
	public static final String TAG = "AFWall";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Application Details");
        setContentView(R.layout.app_detail);
        
        final Context ctx = getApplicationContext();
        
        /**/
        
        int appid = getIntent().getIntExtra("appid", -100);
        if(appid > -100) {
        	
        	final PackageManager packageManager = getApplicationContext().getPackageManager();
        	final String packageName = ctx.getPackageManager().getNameForUid(appid);
        	
        	Button button = (Button) findViewById(R.id.app_settings);
            button.setOnClickListener(new OnClickListener() {
    			
    			public void onClick(View v) {
    				if (android.os.Build.VERSION.SDK_INT >= 9) {
    		            Uri packageURI = Uri.parse("package:" + packageName);
    		            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", packageURI);
    		            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		            ctx.startActivity(intent);
    		        }  else  {
    		        	Intent intent = new Intent(Intent.ACTION_VIEW); 
    		            intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails"); 
    		            intent.putExtra("com.android.settings.ApplicationPkgName", packageName); 
    		            intent.putExtra("pkg", packageName);
    		            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		            ctx.startActivity(intent);          
    		        }
    			}
    		});
        	ApplicationInfo applicationInfo;
        	ImageView image = (ImageView) findViewById(R.id.app_icon);
        	TextView textView = (TextView) findViewById(R.id.app_title);
        	TextView textView2 = (TextView) findViewById(R.id.app_package);
        	TextView up = (TextView) findViewById(R.id.up);
        	TextView down = (TextView) findViewById(R.id.down);
        	try {
        	    applicationInfo = packageManager.getApplicationInfo(packageName, packageManager.GET_META_DATA);
        	    image.setImageDrawable(applicationInfo.loadIcon(packageManager));
        	    textView.setText(packageManager.getApplicationLabel(applicationInfo));
        	    textView2.setText(packageName);
        	    
        	    long download_bytes = TrafficStats.getUidRxBytes(applicationInfo.uid);
        	    long uploaded_bytes = TrafficStats.getUidTxBytes(applicationInfo.uid);
        	    
        	    
        	    up.setText(" : " + humanReadableByteCount(uploaded_bytes, false));
        	    down.setText(" : " +humanReadableByteCount(download_bytes, false));
        	    
        	} catch (final NameNotFoundException e) {}
        	
        	/*long total = TrafficStats.getTotalRxBytes();
        	long mobileTotal = TrafficStats.getMobileRxBytes();
        	long wifiTotal = (total - mobileTotal);
        	Log.v(TAG, "total=" + total + " mob=" + mobileTotal + " wifi=" + wifiTotal);*/
        	
        	
        }
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if(bytes < 0) return "0 B";
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
}
