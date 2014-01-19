package dev.ukanth.ufirewall.ui.about;

import java.io.IOException;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;


public class AboutFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup group,
			Bundle saved) {
		return inflater.inflate(R.layout.help_about_content, group, false);
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
		text.setText(getString(R.string.app_name) + " (v" + version + ")");
		
		WebView creditsWebView = (WebView) getActivity().findViewById(R.id.about_thirdsparty_credits);
		try {
			String data = Api.loadData(getActivity().getBaseContext(), "about");
			creditsWebView.loadDataWithBaseURL(null, data, "text/html","UTF-8",null);
		} catch (IOException ioe) {
			Log.e(Api.TAG, "Error reading changelog file!", ioe);
		}
	}		
}
