package dev.ukanth.ufirewall;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;

public class HelpActivity extends SherlockActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.help_about);
		TextView mSelected = (TextView) findViewById(R.id.text);
		String versionName = "";
		try {
			versionName = getApplicationContext()
					.getPackageManager()
					.getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {

		}
		setTitle("AFWall+ (Donate) " + versionName);
		mSelected.setText(getString(R.string.help_dialog_text));
	}

}
