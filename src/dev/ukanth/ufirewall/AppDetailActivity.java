package dev.ukanth.ufirewall;

import java.util.Arrays;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.TrafficStats;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;

public class AppDetailActivity extends SherlockActivity {
	public static final String TAG = "AFWall";
	private static String packageName = "";
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.traffic_detail_title));
        setContentView(R.layout.app_detail);
        
        final Context ctx = getApplicationContext();
        
        ImageView image = (ImageView) findViewById(R.id.app_icon);
    	TextView textView = (TextView) findViewById(R.id.app_title);
    	TextView textView2 = (TextView) findViewById(R.id.app_package);
    	TextView up = (TextView) findViewById(R.id.up);
    	TextView down = (TextView) findViewById(R.id.down);
        
        /**/
        
        int appid = getIntent().getIntExtra("appid", -1);
        if(appid > 0) {
        	
        	final PackageManager packageManager = getApplicationContext().getPackageManager();
        	final String[] packageNameList = ctx.getPackageManager().getPackagesForUid(appid);
        	
        	if(packageNameList != null) {
        		packageName = packageNameList.length > 0 ? packageNameList[0] : ctx.getPackageManager().getNameForUid(appid);	
        	} else {
        		packageName = ctx.getPackageManager().getNameForUid(appid);
        	}
        	
        	
        	Button button = (Button) findViewById(R.id.app_settings);
            button.setOnClickListener(new OnClickListener() {
    			public void onClick(View v) {
    				Api.showInstalledAppDetails(getApplicationContext(),packageName);
    			}
    		});
        	ApplicationInfo applicationInfo;
        	
        	try {
        		
        	    applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        	    image.setImageDrawable(applicationInfo.loadIcon(packageManager));
        	    textView.setText(packageManager.getApplicationLabel(applicationInfo));
        	    if(packageNameList.length > 1) {
        	    	textView2.setText(Arrays.toString(packageNameList));
        	    	button.setEnabled(false);
        	    } else {
        	    	textView2.setText(packageName);
        	    }
        	    
        	    long download_bytes = TrafficStats.getUidRxBytes(applicationInfo.uid);
        	    long uploaded_bytes = TrafficStats.getUidTxBytes(applicationInfo.uid);
        	    
       	    	down.setText(" : " +humanReadableByteCount(download_bytes, false));	
       	    	up.setText(" : " +humanReadableByteCount(uploaded_bytes, false));	
        	    
        	} catch (final NameNotFoundException e) {
        		long download_bytes = TrafficStats.getUidRxBytes(appid);
         	    long uploaded_bytes = TrafficStats.getUidTxBytes(appid);
         	    
        	    down.setText(" : " +humanReadableByteCount(download_bytes, false));	
        	    up.setText(" : " +humanReadableByteCount(uploaded_bytes, false));	
        		button.setEnabled(false);
        	}
        	
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
