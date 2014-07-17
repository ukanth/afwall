package dev.ukanth.ufirewall;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.MenuItem;

import dev.ukanth.ufirewall.ui.about.AboutFragment;
import dev.ukanth.ufirewall.ui.about.FAQFragment;

public class HelpActivity extends SherlockFragmentActivity implements com.actionbarsherlock.app.ActionBar.TabListener{

	private static final String BUNDLE_KEY_TABINDEX = "tabindex";
	
	private int count = 0;
	@Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            
            setContentView(R.layout.help_about);

            getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            
            
            ActionBar.Tab tab1 = getSupportActionBar().newTab();
            tab1.setText("About");
            tab1.setTabListener(this);
            
            ActionBar.Tab tab3 = getSupportActionBar().newTab();
            tab3.setText("FAQ");
            tab3.setTabListener(this);

            getSupportActionBar().addTab(tab1);
            getSupportActionBar().addTab(tab3);
            
            
    }

	 @Override
     public void onSaveInstanceState(Bundle savedInstanceState) {
             super.onSaveInstanceState(savedInstanceState);
             savedInstanceState.putInt(BUNDLE_KEY_TABINDEX, getSupportActionBar()
                             .getSelectedTab().getPosition());
     }

     @Override
     public void onRestoreInstanceState(Bundle savedInstanceState) {
             super.onRestoreInstanceState(savedInstanceState);
             getSupportActionBar().setSelectedNavigationItem(
                             savedInstanceState.getInt(BUNDLE_KEY_TABINDEX));
     }

     @Override
     public void onTabReselected(Tab tab, FragmentTransaction transaction) {
    	 if(tab.getPosition() == 0) {
   			 count++;	 
    		 if(!G.isDo()) {
	             if(count < 7 && count > 4) {
	             	Toast.makeText(this, (7-count) + this.getString(R.string.unlock_donate), Toast.LENGTH_SHORT).show();
	             	count++;
	             } 
	             if(count >= 7){
	         		G.isDo(true);
	         		Toast.makeText(this, this.getString(R.string.donate_support), Toast.LENGTH_LONG).show();
	            }
    		}
    	 }
     }

     @Override
     public void onTabSelected(Tab tab, FragmentTransaction transaction) {
    	 switch(tab.getPosition()) {
    	 case 0:
    		 AboutFragment fragment = new AboutFragment();
             transaction.replace(android.R.id.content, fragment);
    		 break;
    	 case 1:
    		 FAQFragment changelogFragment = new FAQFragment();
             transaction.replace(android.R.id.content, changelogFragment);
    		 break;
    	 }
             
     }

     @Override
     public void onTabUnselected(Tab tab, FragmentTransaction transaction) {
             Log.i("Tab Unselected", tab.getText().toString());
     }
     
     
     
     @Override
     public boolean onOptionsItemSelected(MenuItem item) {
             switch (item.getItemId()) {
             case android.R.id.home:
                     finish();
                     return true;
             default:
                     return super.onOptionsItemSelected(item);
             }
     }
	
	
	
}
