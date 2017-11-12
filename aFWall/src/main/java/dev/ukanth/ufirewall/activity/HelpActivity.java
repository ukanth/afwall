package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.ui.about.ViewPagerAdapter;
import dev.ukanth.ufirewall.util.SlidingTabLayout;

public class HelpActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private ViewPagerAdapter adapter;
    private SlidingTabLayout tabs;
	private int count = 0;
    private int noOfTabs =2;


	@Override
    public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            
            String[] viewTitles = { getString(R.string.About), getString(R.string.FAQ) };

            setContentView(R.layout.help_about);

            Toolbar toolbar = (Toolbar) findViewById(R.id.help_toolbar);
            setSupportActionBar(toolbar);


            // Creating The ViewPagerAdapter and Passing Fragment Manager, Titles fot the Tabs and Number Of Tabs.
            adapter =  new ViewPagerAdapter(getSupportFragmentManager(), viewTitles, noOfTabs);

            // Initilization
            viewPager = (ViewPager) findViewById(R.id.pager);

            viewPager.setAdapter(adapter);

            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            // Assiging the Sliding Tab Layout View
            tabs = (SlidingTabLayout) findViewById(R.id.tabs);
            tabs.setDistributeEvenly(true); // To make the Tabs Fixed set this true, This makes the tabs Space Evenly in Available width

            // Setting Custom Color for the Scroll bar indicator of the Tab View
            tabs.setCustomTabColorizer(new SlidingTabLayout.TabColorizer() {
                @Override
                public int getIndicatorColor(int position) {
                    return ContextCompat.getColor(getApplicationContext(),R.color.white);
                }
            });

            // Setting the ViewPager For the SlidingTabsLayout
            tabs.setViewPager(viewPager);
    }

    /*
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
    */

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
