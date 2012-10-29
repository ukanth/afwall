package dev.ukanth.ufirewall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectivityChangeReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo[] ni = cm.getAllNetworkInfo();
		for(NetworkInfo info: ni) {
			if ( ni != null )
			{
			    if (info.getType() == ConnectivityManager.TYPE_MOBILE)
			        if (info.isConnectedOrConnecting() && info.isRoaming()) {
			        	Api.applyIptablesRules(context, false);        	
			        }
			            
			}	
		}
	}
}
