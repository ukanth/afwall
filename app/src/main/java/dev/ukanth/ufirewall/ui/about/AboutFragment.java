package dev.ukanth.ufirewall.ui.about;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import java.io.IOException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;


public class AboutFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup group,
			Bundle saved) {
		View view = inflater.inflate(R.layout.help_about_content, group, false);
		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		String version = BuildConfig.VERSION_NAME;

		TextView text = getActivity().findViewById(R.id.afwall_title);
		String versionText = getString(R.string.app_name) + " (v" + version + ")";
		if(G.isDoKey(getActivity().getApplicationContext()) || BuildConfig.APPLICATION_ID.equals("dev.ukanth.ufirewall.donate")) {
			versionText = versionText + " (Donate) " +  getActivity().getString(R.string.donate_thanks)+  ":)";
		}
		text.setText(versionText);
		
		WebView creditsWebView = getActivity().findViewById(R.id.about_thirdsparty_credits);
		try {
			String data = Api.loadData(getActivity().getBaseContext(), "about");
			creditsWebView.loadDataWithBaseURL(null, data, "text/html","UTF-8",null);
		} catch (IOException ioe) {
			Log.e(Api.TAG, "Error reading changelog file!", ioe);
		}
	}


	/*class ActivitySwipeDetector implements View.OnTouchListener {
		static final String logTag = "ActivitySwipeDetector";
		static final int MIN_DISTANCE = 100;
		private float downX, downY, upX, upY;

		public ActivitySwipeDetector() {
		}

		public void onRightToLeftSwipe(View v) {
			Log.i(logTag, "RightToLeftSwipe!");
		}

		public void onLeftToRightSwipe(View v){
			Log.i(logTag, "LeftToRightSwipe!");
		}

		public void onTopToBottomSwipe(View v){
			Log.i(logTag, "onTopToBottomSwipe!");
		}

		public void onBottomToTopSwipe(View v){
			Log.i(logTag, "onBottomToTopSwipe!");
			Toast.makeText(getActivity(),"Swipe Works great",Toast.LENGTH_LONG).show();
		}

		public boolean onTouch(View v, MotionEvent event) {
			switch(event.getAction()){
				case MotionEvent.ACTION_DOWN: {
					downX = event.getX();
					downY = event.getY();
					return true;
				}
				case MotionEvent.ACTION_UP: {
					upX = event.getX();
					upY = event.getY();

					float deltaX = downX - upX;
					float deltaY = downY - upY;

					// swipe horizontal?
					if(Math.abs(deltaX) > MIN_DISTANCE){
						// left or right
						if(deltaX < 0) { this.onLeftToRightSwipe(v); return true; }
						if(deltaX > 0) { this.onRightToLeftSwipe(v); return true; }
					}
					else {
						Log.i(logTag, "Swipe was only " + Math.abs(deltaX) + " long, need at least " + MIN_DISTANCE);
					}

					// swipe vertical?
					if(Math.abs(deltaY) > MIN_DISTANCE){
						// top or down
						if(deltaY < 0) { this.onTopToBottomSwipe(v); return true; }
						if(deltaY > 0) { this.onBottomToTopSwipe(v); return true; }
					}
					else {
						Log.i(logTag, "Swipe was only " + Math.abs(deltaX) + " long, need at least " + MIN_DISTANCE);
						v.performClick();
					}
				}
			}
			return false;
		}
	}*/
}
