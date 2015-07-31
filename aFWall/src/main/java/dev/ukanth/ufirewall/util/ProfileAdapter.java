package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 31/7/15.
 */
public class ProfileAdapter extends ArrayAdapter<Profile> {

    private List<Profile> ProfileList;
    private Context context;



    public ProfileAdapter(List<Profile> ProfileList, Context ctx) {
        super(ctx, R.layout.profile_layout, ProfileList);
        this.ProfileList = ProfileList;
        this.context = ctx;
    }

    public int getCount() {
        return ProfileList.size();
    }

    public Profile getItem(int position) {
        return ProfileList.get(position);
    }

    public long getItemId(int position) {
        return ProfileList.get(position).hashCode();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        ProfileHolder holder = new ProfileHolder();

        // First let's verify the convertView is not null
        if (convertView == null) {
            // This a new view we inflate the new layout
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.profile_layout, null);
            // Now we can fill the layout with the right values
            TextView tv = (TextView) v.findViewById(R.id.pro_name);
            holder.ProfileNameView = tv;
            v.setTag(holder);
        }
        else
            holder = (ProfileHolder) v.getTag();

        Profile p = ProfileList.get(position);
        holder.ProfileNameView.setText(p.getProfileName());

        return v;
    }
	
	/* *********************************
	 * We use the holder pattern        
	 * It makes the view faster and avoid finding the component
	 * **********************************/

    private static class ProfileHolder {
        public TextView ProfileNameView;
    }
}
