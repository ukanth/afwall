package dev.ukanth.ufirewall.profiles;

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
public class ProfileAdapter extends ArrayAdapter<ProfileData> {

    private List<ProfileData> profileList;
    private Context context;


    public ProfileAdapter(List<ProfileData> profileList, Context ctx) {
        super(ctx, R.layout.profile_layout, profileList);
        this.profileList = profileList;
        this.context = ctx;
    }

    public int getCount() {
        return profileList.size();
    }

    public ProfileData getItem(int position) {
        return profileList.get(position);
    }

    public long getItemId(int position) {
        return profileList.get(position).hashCode();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ProfileHolder holder = new ProfileHolder();
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.profile_layout, null);
            TextView tv = v.findViewById(R.id.pro_name);
            holder.profileNameView = tv;
            v.setTag(holder);
        } else {
            holder = (ProfileHolder) v.getTag();
        }
        ProfileData p = profileList.get(position);
        holder.profile = p;
        holder.profileNameView.setText(p.getName());
        return v;
    }

	/* *********************************
     * We use the holder pattern
	 * It makes the view faster and avoid finding the component
	 * **********************************/

    private static class ProfileHolder {
        public TextView profileNameView;
        public ProfileData profile;

    }
}
