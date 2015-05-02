package dev.ukanth.ufirewall.activity;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.widget.Toast;

import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.TabsPagerAdapter;
import dev.ukanth.ufirewall.util.G;

public class HelpActivity extends FragmentActivity implements  ActionBar.TabListener{

	private static final String BUNDLE_KEY_TABINDEX = "tabindex";

    private ViewPager viewPager;
    private TabsPagerAdapter mAdapter;
    private ActionBar actionBar;

	private int count = 0;
	@Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            String[] tabs = { getString(R.string.About), getString(R.string.FAQ) };
            
            getActionBar().setDisplayHomeAsUpEnabled(true);

            setContentView(R.layout.help_about);

            // Initilization
            viewPager = (ViewPager) findViewById(R.id.pager);
            actionBar = getActionBar();
            mAdapter = new TabsPagerAdapter(getSupportFragmentManager());

            viewPager.setAdapter(mAdapter);
            actionBar.setHomeButtonEnabled(false);
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            // Adding Tabs
            for (String tab_name : tabs) {
                actionBar.addTab(actionBar.newTab().setText(tab_name).setTabListener(this));
            }

            viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    actionBar.setSelectedNavigationItem(position);
                }
            });



    }

	 @Override
     public void onSaveInstanceState(Bundle savedInstanceState) {
             super.onSaveInstanceState(savedInstanceState);
             savedInstanceState.putInt(BUNDLE_KEY_TABINDEX, getActionBar()
                             .getSelectedTab().getPosition());
     }

     @Override
     public void onRestoreInstanceState(Bundle savedInstanceState) {
             super.onRestoreInstanceState(savedInstanceState);
             getActionBar().setSelectedNavigationItem(
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
    	 viewPager.setCurrentItem(tab.getPosition());
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
