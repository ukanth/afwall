package dev.ukanth.ufirewall.ui.about;

import java.io.IOException;

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


public class FAQFragment extends Fragment {
	private static final String TAG = "ChangelogFragment";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup group,
			Bundle saved) {
		return inflater.inflate(R.layout.help_about_content, group, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		TextView text = (TextView) getActivity().findViewById(R.id.afwall_title);
		text.setText(R.string.faq);
		
		WebView creditsWebView = (WebView) getActivity().findViewById(R.id.about_thirdsparty_credits);
		try {
			String data = Api.loadData(getActivity().getBaseContext(), "faq");
			creditsWebView.loadDataWithBaseURL(null, data, "text/html","UTF-8",null);
		} catch (IOException ioe) {
			Log.e(TAG, "Error reading changelog file!", ioe);
		}
	}		
}
