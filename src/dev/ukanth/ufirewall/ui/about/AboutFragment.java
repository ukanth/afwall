package dev.ukanth.ufirewall.ui.about;

import java.io.IOException;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;


public class AboutFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup group,
			Bundle saved) {
		View view = inflater.inflate(R.layout.help_about_content, group, false);

	    view.setOnTouchListener(new View.OnTouchListener() {
	    		int count = 0;
				@Override
				public boolean onTouch(View arg0, MotionEvent event) {
					if(!G.isDo()) {
						if(event.getAction() == MotionEvent.ACTION_DOWN){
		                    if(count < 7 && count > 4) {
		                    	Toast.makeText(getActivity(), (7-count) + getActivity().getString(R.string.unlock_donate), Toast.LENGTH_SHORT).show();
		                    	count++;
		                    } 
		                    if(count >= 7){
	                    		G.isDo(true);
	                    		Toast.makeText(getActivity(), getActivity().getString(R.string.donate_support), Toast.LENGTH_LONG).show();
		                    }
		                }
					}
	                return true;
				}
	    });
		
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		PackageInfo pInfo = null;
		String version = "";
		try {
			pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
		} catch (NameNotFoundException e) {
			Log.e(Api.TAG, "Package not found", e);
		}
		version = pInfo.versionName;
		
		TextView text = (TextView) getActivity().findViewById(R.id.afwall_title);
		String versionText = getString(R.string.app_name) + " (v" + version + ")";
		if(G.isDo() || Api.getCurrentPackage(getActivity().getApplicationContext()).equals("dev.ukanth.ufirewall.donate")) {
			versionText = versionText + " (Donate) " +  getActivity().getString(R.string.donate_thanks)+  ":)";
		}
		text.setText(versionText);
		
		WebView creditsWebView = (WebView) getActivity().findViewById(R.id.about_thirdsparty_credits);
		try {
			String data = Api.loadData(getActivity().getBaseContext(), "about");
			creditsWebView.loadDataWithBaseURL(null, data, "text/html","UTF-8",null);
		} catch (IOException ioe) {
			Log.e(Api.TAG, "Error reading changelog file!", ioe);
		}
	}		
}
