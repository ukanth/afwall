package dev.ukanth.ufirewall.widget;

import java.util.ArrayList;
import java.util.Arrays;
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
import dev.ukanth.ufirewall.RootShell.RootCommand;
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
	
		int xLayoutSize = ll.getWidth();
		int yLayoutSize = ll.getHeight();				
		pieMenu.setSourceLocation(xLayoutSize,yLayoutSize);
		pieMenu.setIconSize(15, 30);
		pieMenu.setTextSize(13);				
		
		pieMenu.setCenterCircle(new Close());
		pieMenu.addMenuEntry(new Status());
		pieMenu.addMenuEntry(new EnableFirewall());
		pieMenu.addMenuEntry(new DisableFirewall());
		
		if(G.enableMultiProfile()){
			pieMenu.addMenuEntry(new Profiles());
		}
		
		ll.addView(pieMenu);

	}
	
	
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
		  public String getLabel() { return getString(R.string.enable); } 
		  public int getIcon() { return 0; }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	    	  startAction(1);
	      }
	   }	
	  
	  public class Status implements RadialMenuEntry
	   {
	      public String getName() { return G.getActiveProfileName(getApplicationContext()); } 
		  public String getLabel() { return G.getActiveProfileName(getApplicationContext()); } 
		  public int getIcon() { return (Api.isEnabled(getApplicationContext()) ?  R.drawable.widget_on :  R.drawable.widget_off); }
	      public List<RadialMenuEntry> getChildren() { return null; }
	      public void menuActiviated()
	      {
	    	  
	      }
	   }	
	  
	  public class DisableFirewall implements RadialMenuEntry
	   {
	      public String getName() { return ""; } 
		  public String getLabel() { return getString(R.string.disable); } 
		  public int getIcon() { return 0; }
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
	      private List<RadialMenuEntry> children = new ArrayList<RadialMenuEntry>( Arrays.asList( new DefaultProfile(), new Profile1(), new Profile2(), new Profile3() ) );
	      public List<RadialMenuEntry> getChildren() { return children; }
	      public Profiles(){
	    	  for(String profileName: G.getAdditionalProfiles()) {
	    		  RadialMenuEntry entry = new GenericProfile(profileName);
	    		  children.add(entry);
	    	  }
	      }
	      public void menuActiviated()
	      {
	      }
	   }	
	   
	   public class GenericProfile implements RadialMenuEntry
       {
          public String getName() { return profileName; } 
          public String getLabel() { return profileName; }
          public int getIcon() { return 0; }
          private String profileName;
          public GenericProfile(String profileName){
        	  this.profileName = profileName;
          }
          public List<RadialMenuEntry> getChildren() { return null; }
          public void menuActiviated()
          {
        	  int pos = G.getProfilePosition(profileName);
        	  final Message msg = new Message();
        	  Toast.makeText(getApplicationContext(), profileName + " pressed. + position" + pos, Toast.LENGTH_SHORT).show();
        	  final Handler toaster = new Handler() {
      			public void handleMessage(Message msg) {
      				if (msg.arg1 != 0)Toast.makeText(getApplicationContext(), msg.arg1, Toast.LENGTH_SHORT).show();
      			}
      		};
      		final Context context = getApplicationContext();
      		G.setProfile(true, pos);
			applyProfileRules(context,msg,toaster);
          }
       }    
	   
	   public class DefaultProfile implements RadialMenuEntry
       {
          public String getName() { return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile)); } 
          public String getLabel() { return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile)); }
          public int getIcon() { return 0; }
          public List<RadialMenuEntry> getChildren() { return null; }
          public void menuActiviated()
          {
        	  startAction(3);
          }
       }    
	   public class Profile1 implements RadialMenuEntry
       {
          public String getName() { return G.gPrefs.getString("profile1", getString(R.string.profile1)); } 
              public String getLabel() { return G.gPrefs.getString("profile1", getString(R.string.profile1)); }
          public int getIcon() { return 0; }
          public List<RadialMenuEntry> getChildren() { return null; }
          public void menuActiviated()
          {
        	  startAction(4);
          }
       }    
	   public class Profile2 implements RadialMenuEntry
       {
          public String getName() { return G.gPrefs.getString("profile2", getString(R.string.profile2)); } 
          public String getLabel() { return G.gPrefs.getString("profile2", getString(R.string.profile2)); }
          public int getIcon() { return 0; }
          public List<RadialMenuEntry> getChildren() { return null; }
          public void menuActiviated()
          {
        	  startAction(5);
          }
       }    
	   public class Profile3 implements RadialMenuEntry
       {
          public String getName() { return G.gPrefs.getString("profile3", getString(R.string.profile3)); } 
          public String getLabel() { return G.gPrefs.getString("profile3", getString(R.string.profile3)); }
          public int getIcon() { return 0; }
          public List<RadialMenuEntry> getChildren() { return null; }
          public void menuActiviated()
          {
        	  startAction(6);
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
				if( i < 7) {
					switch(i){
					case 1: 
						if(applyProfileRules(context,msg,toaster)){
							Api.setEnabled(context, true, false);
						}
						break;
					case 2:
						//validation, check for password
						if(oldPwd.length() == 0 && newPwd.length() == 0){
							Api.purgeIptables(context, true, new RootCommand()
							.setSuccessToast(R.string.toast_disabled)
							.setFailureToast(R.string.toast_error_disabling)
							.setReopenShell(true)
							.setCallback(new RootCommand.Callback() {
								public void cbFunc(RootCommand state) {
									
									if (state.exitCode == 0) {
										msg.arg1 = R.string.toast_disabled;
										toaster.sendMessage(msg);
										Api.setEnabled(context, false, false);
									} else {
										// error details are already in logcat
										msg.arg1 = R.string.toast_error_disabling;
										toaster.sendMessage(msg);
									}
								}
							}));
						} else {
							msg.arg1 = R.string.widget_disable_fail;
							toaster.sendMessage(msg);
						}
						break;
					case 3:
						G.setProfile(G.enableMultiProfile(), 0);
						break;
					case 4:
						G.setProfile(true, 1);
						break;
					case 5:
						G.setProfile(true, 2);
						break;
					case 6:
						G.setProfile(true, 3);
						break;
					}
					if(i > 2) {
						G.reloadPrefs();
						applyProfileRules(context,msg,toaster);
					}
				}
			}
		}.start();
	}
	
	
	private boolean applyProfileRules(final Context context,final Message msg, final Handler toaster) {
		Api.saveRules(context);
		boolean ret = Api.applySavedIptablesRules(context, true, new RootCommand()
		.setFailureToast(R.string.error_apply)
		.setReopenShell(true)
		.setCallback(new RootCommand.Callback() {
			public void cbFunc(RootCommand state) {
				if (state.exitCode == 0) {
					msg.arg1 = R.string.rules_applied;
					toaster.sendMessage(msg);
				} else {
					// error details are already in logcat
					msg.arg1 = R.string.error_apply;
					toaster.sendMessage(msg);
				}
			}
		}));
		return ret;
	}
	
	
}



