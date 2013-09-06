package dev.ukanth.ufirewall;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.actionbarsherlock.app.SherlockActivity;

public class HelpActivity extends SherlockActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.help_about);
		
		WebView browser = (WebView)findViewById(R.id.helpwebview);

	    WebSettings settings = browser.getSettings();
	    settings.setJavaScriptEnabled(true);

	    browser.loadUrl("file:///android_asset/about.html");
	    
/*		TextView mSelected = (TextView) findViewById(R.id.text);
		String versionName = "";
		try {
			versionName = getApplicationContext()
					.getPackageManager()
					.getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {

		}
		setTitle("AFWall+" + versionName);
		mSelected.setText(getString(R.string.help_dialog_text));*/
	}

}
