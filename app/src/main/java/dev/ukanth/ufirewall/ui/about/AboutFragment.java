package dev.ukanth.ufirewall.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;


public class AboutFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup group,
			Bundle saved) {
		return inflater.inflate(R.layout.help_about_content, group, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		String version = BuildConfig.VERSION_NAME;

		TextView titleText = view.findViewById(R.id.afwall_title);
		String versionText = getString(R.string.app_name) + " (v" + version + ")";
		if(G.isDoKey(requireContext()) || BuildConfig.APPLICATION_ID.equals("dev.ukanth.ufirewall.donate")) {
			versionText = versionText + " (Donate) " + getString(R.string.donate_thanks) + " :)";
		}
		titleText.setText(versionText);
		
		loadCreditsContent(view);
	}

	private void loadCreditsContent(View view) {
		WebView creditsWebView = view.findViewById(R.id.about_thirdsparty_credits);
		
		// Configure WebView for better user experience
		creditsWebView.getSettings().setJavaScriptEnabled(false);
		creditsWebView.getSettings().setBuiltInZoomControls(true);
		creditsWebView.getSettings().setDisplayZoomControls(false);
		
		// Handle links to open in external browser
		creditsWebView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView webView, String url) {
				try {
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(browserIntent);
					return true;
				} catch (Exception e) {
					Log.e(Api.TAG, "Error opening URL: " + url, e);
					return false;
				}
			}
		});
		
		try {
			String data = Api.loadData(requireContext(), "about");
			// Enhance the HTML with modern styling that adapts to theme
			String enhancedData = enhanceHTMLForModernUI(data);
			creditsWebView.loadDataWithBaseURL(null, enhancedData, "text/html", "UTF-8", null);
		} catch (IOException ioe) {
			Log.e(Api.TAG, "Error reading about file!", ioe);
			// Show error state
			Snackbar.make(view, "Unable to load about content", Snackbar.LENGTH_LONG).show();
		}
	}

	private String enhanceHTMLForModernUI(String originalHTML) {
		// Replace the old styling with modern, theme-aware styling
		String modernCSS = 
			"<style type='text/css'>" +
			"body { " +
				"background: transparent; " +
				"color: var(--md-sys-color-on-surface, #1C1B1F); " +
				"font-family: 'Roboto', sans-serif; " +
				"font-size: 14px; " +
				"line-height: 1.6; " +
				"margin: 0; " +
				"padding: 16px; " +
			"} " +
			"a { " +
				"color: var(--md-sys-color-primary, #6750A4); " +
				"text-decoration: none; " +
			"} " +
			"a:hover { text-decoration: underline; } " +
			"div.block { " +
				"margin-bottom: 16px; " +
				"padding: 16px; " +
				"background: var(--md-sys-color-surface-variant, #E7E0EC); " +
				"border-radius: 8px; " +
				"border-left: 4px solid var(--md-sys-color-primary, #6750A4); " +
			"} " +
			"div.block label { " +
				"font-weight: 600; " +
				"color: var(--md-sys-color-primary, #6750A4); " +
				"display: block; " +
				"margin-bottom: 8px; " +
				"font-size: 15px; " +
			"} " +
			"div.block .span { " +
				"color: var(--md-sys-color-on-surface-variant, #49454F); " +
			"} " +
			"#credits { " +
				"margin: 0; " +
				"padding: 0; " +
			"} " +
			"#credits li { " +
				"list-style-type: none; " +
				"font-weight: 500; " +
				"color: var(--md-sys-color-on-surface, #1C1B1F); " +
				"margin: 12px 0; " +
				"padding: 8px 0; " +
				"border-bottom: 1px solid var(--md-sys-color-outline-variant, #CAC4D0); " +
			"} " +
			"#credits li ul { margin: 8px 0 16px 16px; } " +
			"#credits li ul li { " +
				"font-weight: normal; " +
				"color: var(--md-sys-color-on-surface-variant, #49454F); " +
				"list-style-type: disc; " +
				"border-bottom: none; " +
				"margin: 4px 0; " +
				"padding: 2px 0; " +
			"} " +
			"strong, b { color: var(--md-sys-color-primary, #6750A4); } " +
			"i, em { color: var(--md-sys-color-secondary, #625B71); } " +
			"</style>";
		
		// Replace the existing style section with modern styling
		if (originalHTML.contains("<style")) {
			originalHTML = originalHTML.replaceAll("<style[^>]*>.*?</style>", modernCSS);
		} else {
			originalHTML = originalHTML.replace("<head>", "<head>" + modernCSS);
		}
		
		return originalHTML;
	}
}
