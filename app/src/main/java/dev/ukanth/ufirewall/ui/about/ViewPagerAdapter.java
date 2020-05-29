package dev.ukanth.ufirewall.ui.about;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

/**
 * Created by ukanth on 2/5/15.
 */
public class ViewPagerAdapter extends FragmentStatePagerAdapter {

    CharSequence[] pageTitles;
    int noOfTabs;


    // Build a Constructor and assign the passed Values to appropriate values in the class
    public ViewPagerAdapter(FragmentManager fm, CharSequence[] mTitles, int mNumbOfTabsumb) {
        super(fm);
        this.pageTitles = mTitles;
        this.noOfTabs = mNumbOfTabsumb;

    }
    @Override
    public Fragment getItem(int position) {
        if(position == 0) {
            return new AboutFragment();
        } else {
            return new FAQFragment();
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return pageTitles[position];
    }

    @Override
    public int getCount() {
        return noOfTabs;
    }
}
