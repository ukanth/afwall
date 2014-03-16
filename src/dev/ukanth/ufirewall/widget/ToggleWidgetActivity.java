package dev.ukanth.ufirewall.widget;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.LinearLayout;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.G;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.widget.RadialMenuWidget.RadialMenuEntry;

public class ToggleWidgetActivity extends Activity {
	
	private RadialMenuWidget pieMenu;
	private LinearLayout ll;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.toggle_widget_view);
		
		ll = (LinearLayout) this.findViewById(R.id.widgetCircle);
		pieMenu = new RadialMenuWidget(getBaseContext());

		pieMenu.setAnimationSpeed(0L);
		//pieMenu.setSourceLocation(100,100);
		//pieMenu.setCenterLocation(240,400);
		//pieMenu.setInnerRingRadius(50, 120);
		//pieMenu.setInnerRingColor(Color.LTGRAY, 255);
		//pieMenu.setHeader("Menu Header", 20);

		int xLayoutSize = ll.getWidth();
		int yLayoutSize = ll.getHeight();				
		pieMenu.setSourceLocation(xLayoutSize,yLayoutSize);
		pieMenu.setIconSize(15, 30);
		pieMenu.setTextSize(13);				
		
		pieMenu.setCenterCircle(new Close());
		pieMenu.addMenuEntry(new EnableFirewall());
		pieMenu.addMenuEntry(new DisableFirewall());
		
		if(G.enableMultiProfile()){
			pieMenu.addMenuEntry(new Profiles());
		}
		
		ll.addView(pieMenu);


	}
	
	/*public boolean onTouchEvent(MotionEvent e) {
		int state = e.getAction();
		int eventX = (int) e.getX();
		int eventY = (int) e.getY();
		if (state == MotionEvent.ACTION_DOWN) {

			//Screen Sizes
			int xScreenSize = (getResources().getDisplayMetrics().widthPixels);
			int yScreenSize = (getResources().getDisplayMetrics().heightPixels);
			int xLayoutSize = ll.getWidth();
			int yLayoutSize = ll.getHeight();
			int xCenter = xScreenSize/2;
			int xSource = eventX;
			int yCenter = yScreenSize/2;
			int ySource = eventY;
			
			if (xScreenSize != xLayoutSize) {
				xCenter = xLayoutSize/2;
				xSource = eventX-(xScreenSize-xLayoutSize);
			}
			if (yScreenSize != yLayoutSize) {
				yCenter = yLayoutSize/2;
				ySource = eventY-(yScreenSize-yLayoutSize);
			}				
			
			if(ll.getChildCount() == 0) {
				pieMenu = new RadialMenuWidget(getBaseContext());

				pieMenu.setSourceLocation(xSource,ySource);
				pieMenu.setShowSourceLocation(true);
				pieMenu.setCenterLocation(xCenter,yCenter);

				pieMenu.setCenterCircle(new Close());
				ll.addView(pieMenu);
			}
			
			
			
		}
		return true;
	}
	*/
	
	  public class Close implements RadialMenuEntry
	   {

		  public String getName() { return "Close"; } 
		  public String getLabel() { return null; } 
	      public int getIcon() { return android.R.drawable.ic_menu_close_clear_cancel; }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	    	  ll = (LinearLayout) findViewById(R.id.widgetCircle);
	    	  ll.removeAllViews();
	    	  finish();
	      }
	   }	
	
	  public class EnableFirewall implements RadialMenuEntry
	   {
	      public String getName() { return ""; } 
		  public String getLabel() { return ""; } 
		  public int getIcon() { return R.drawable.widget_on; }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	    	  startAction(1);
	      }
	   }	
	  
	  public class DisableFirewall implements RadialMenuEntry
	   {
	      public String getName() { return ""; } 
		  public String getLabel() { return ""; } 
		  public int getIcon() { return R.drawable.widget_off; }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	    	  startAction(2);
	      }
	   }	
	   

	   public class Profiles implements RadialMenuEntry
	   {
	      public String getName() { return "Profiles"; } 
		  public String getLabel() { return "Profiles"; }
	      public int getIcon() { return 0; }
	      private  List<RadialMenuEntry> children =  new ArrayList<RadialMenuEntry>();
	      public Profiles(){
	    	  children.add(new GenericProfile(G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile))));
	    	  children.add(new GenericProfile(G.gPrefs.getString("profile1", getString(R.string.profile1))));
	    	  children.add(new GenericProfile(G.gPrefs.getString("profile2", getString(R.string.profile2))));
	    	  children.add(new GenericProfile(G.gPrefs.getString("profile3", getString(R.string.profile3))));
	    	  for(String profile: G.getAdditionalProfiles()){
	    		  children.add(new GenericProfile(profile));
	    	  }
	      }
	      
	      /*private List<RadialMenuEntry> children = new ArrayList<RadialMenuEntry>( 
	    		  Arrays.asList( new GenericProfile(), new GenericProfile()) );*/
	      
	      public List<RadialMenuEntry> getChildren() { return children; }
	      public void menuActiviated()
	      {
	    	  
	      }
	   }	
	   public static class GenericProfile implements RadialMenuEntry
	   {
	      public String getName() { return profile; } 
		  public String getLabel() { return profile; }
		  public String profile;
		  GenericProfile(String profile){
			  this.profile = profile;
		  }
	      public int getIcon() { return 0; }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	      }
	   }	   


	private void startAction(final int i) {
		
		final Handler toaster = new Handler() {
			public void handleMessage(Message msg) {
				if (msg.arg1 != 0)Toast.makeText(getApplicationContext(), msg.arg1, Toast.LENGTH_SHORT).show();
			}
		};
		final Context context = getApplicationContext();
		final SharedPreferences prefs2 = context.getSharedPreferences(Api.PREFS_NAME, 0);
		final String oldPwd = prefs2.getString(Api.PREF_PASSWORD, "");
		final String newPwd = getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0).getString("LockPassword", "");
		new Thread() {
			@Override
			public void run() {
				Looper.prepare();
				final Message msg = new Message();
				switch(i){
				case 1: 
					if(applyRules(context,msg,toaster)){
						Api.setEnabled(context, true, false);
					}
					break;
				case 2:
					//validation, check for password
					if(oldPwd.length() == 0 && newPwd.length() == 0){
						if (Api.purgeIptables(context, false)) {
							msg.arg1 = R.string.toast_disabled;
							toaster.sendMessage(msg);
							Api.setEnabled(context, false, false);
						} else {
							msg.arg1 = R.string.toast_error_disabling;
							toaster.sendMessage(msg);
						}
					} else {
						msg.arg1 = R.string.widget_disable_fail;
						toaster.sendMessage(msg);
					}
					
					break;
				case 3:
					G.setProfile(G.enableMultiProfile(), 0);
					if(applyProfileRules(context,msg,toaster)) {
					}
					break;
				case 4:
					G.setProfile(true, 1);
					if(applyProfileRules(context,msg,toaster)){
					}
					break;
				case 5:
					G.setProfile(true, 2);
					if(applyProfileRules(context,msg,toaster)){
					}
					break;
				case 6:
					G.setProfile(true, 3);
					if(applyProfileRules(context,msg,toaster)){
					}
					break;
				}
			}
		}.start();
	}
	

	
	private boolean applyRules(Context context,Message msg, Handler toaster) {
		boolean success = false;
		if (Api.applySavedIptablesRules(context, false)) {
			msg.arg1 = R.string.toast_enabled;
			toaster.sendMessage(msg);
			success = true;
		} else {
			msg.arg1 = R.string.toast_error_enabling;
			toaster.sendMessage(msg);
		}
		return success;
	}
	
	private boolean applyProfileRules(Context context,Message msg, Handler toaster) {
		boolean success = false;
		if (Api.applySavedIptablesRules(context, false)) {
			msg.arg1 = R.string.rules_applied;
			toaster.sendMessage(msg);
			success = true;
		} else {
			msg.arg1 = R.string.error_apply;
			toaster.sendMessage(msg);
		}
		return success;
	}
}



