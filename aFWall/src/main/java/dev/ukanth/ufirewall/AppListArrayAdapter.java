package dev.ukanth.ufirewall;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;
import dev.ukanth.ufirewall.Api.PackageInfoData;

public class AppListArrayAdapter extends ArrayAdapter<PackageInfoData> implements OnCheckedChangeListener {

	public static final String TAG = "AFWall";
	private final Context context;
	private final List<PackageInfoData> listApps;
	private Activity activity;

	final int color = G.sysColor();
	final int defaultColor = Color.WHITE;

	public AppListArrayAdapter(MainActivity activity, Context context, List<PackageInfoData> apps) {
		super(context, R.layout.main_list, apps);
		this.activity = activity;
		this.context = context;
		this.listApps = apps;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (convertView == null) {
			// Inflate a new view
			convertView = inflater.inflate(R.layout.main_list, parent, false);
			holder = new ViewHolder();
			holder.box_wifi = (CheckBox) convertView.findViewById(R.id.itemcheck_wifi);
			holder.box_wifi.setOnCheckedChangeListener(this);
			
			if(Api.isMobileNetworkSupported(context)) {
				holder.box_3g = addSupport(holder.box_3g, convertView,true, R.id.itemcheck_3g);
			} else {
				removeSupport(holder.box_3g, convertView,R.id.itemcheck_3g);
			}
			
			if (G.enableRoam()) {
				holder.box_roam = addSupport(holder.box_roam, convertView,true, R.id.itemcheck_roam);
			}
			if (G.enableVPN()) {
				holder.box_vpn = addSupport(holder.box_vpn, convertView, true,R.id.itemcheck_vpn);
			}
			if (G.enableLAN()) {
				holder.box_lan = addSupport(holder.box_lan, convertView, true,R.id.itemcheck_lan);
			}

			holder.text = (TextView) convertView.findViewById(R.id.itemtext);
			holder.icon = (ImageView) convertView.findViewById(R.id.itemicon);

			if (G.disableIcons()) {
				holder.icon.setVisibility(View.GONE);
				activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
			}
			convertView.setTag(holder);
		} else {
			// Convert an existing view
			holder = (ViewHolder) convertView.getTag();
			holder.box_wifi = (CheckBox) convertView.findViewById(R.id.itemcheck_wifi);
			if(Api.isMobileNetworkSupported(context)) {
				holder.box_3g = addSupport(holder.box_3g, convertView,true, R.id.itemcheck_3g);
			} else {
				removeSupport(holder.box_3g, convertView,R.id.itemcheck_3g);
			}
			if (G.enableRoam()) {
				addSupport(holder.box_roam, convertView, false,R.id.itemcheck_roam);
			}
			if (G.enableVPN()) {
				addSupport(holder.box_vpn, convertView, false,R.id.itemcheck_vpn);
			}
			if (G.enableLAN()) {
				addSupport(holder.box_lan, convertView, false,R.id.itemcheck_lan);
			}

			holder.text = (TextView) convertView.findViewById(R.id.itemtext);
			holder.icon = (ImageView) convertView.findViewById(R.id.itemicon);
			if (G.disableIcons()) {
				holder.icon.setVisibility(View.GONE);
				activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
			}
		}

		holder.app = listApps.get(position);

		if (G.showUid()) {
			holder.text.setText(holder.app.toStringWithUID());
		} else {
			holder.text.setText(holder.app.toString());
		}

		final int id = holder.app.uid;
		if (id > 0) {
			holder.text.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					Intent intent = new Intent(context, AppDetailActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					intent.putExtra("appid", id);
					context.startActivity(intent);
					return true;
				}
			});
		}

		ApplicationInfo info = holder.app.appinfo;
		if (info != null && (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
			holder.text.setTextColor(defaultColor);
		} else {
			holder.text.setTextColor(color);
		}

		if (!G.disableIcons()) {
			holder.icon.setImageDrawable(holder.app.cached_icon);
			if (!holder.app.icon_loaded && info != null) {
				// this icon has not been loaded yet - load it on a
				// separated thread
				try {
					new LoadIconTask().execute(holder.app,
							context.getPackageManager(), convertView);
				} catch (RejectedExecutionException r) {
				}
			}
		} else {
			holder.icon.setVisibility(View.GONE);
			activity.findViewById(R.id.imageHolder).setVisibility(View.GONE);
		}

		holder.box_wifi.setTag(holder.app);
		holder.box_wifi.setChecked(holder.app.selected_wifi);

		
		if(Api.isMobileNetworkSupported(context)) {
			holder.box_3g.setTag(holder.app);
			holder.box_3g.setChecked(holder.app.selected_3g);
		} 

		if (G.enableRoam()) {
			holder.box_roam = addSupport(holder.box_roam, holder, holder.app, 0);
		}
		if (G.enableVPN()) {
			holder.box_vpn = addSupport(holder.box_vpn, holder, holder.app, 1);
		}
		if (G.enableLAN()) {
			holder.box_lan = addSupport(holder.box_lan, holder, holder.app, 2);
		}

		return convertView;
	}
	
	private CheckBox addSupport(CheckBox check,ViewHolder entry,PackageInfoData app, int flag ) {
		check.setTag(app);
		switch (flag) {
			case 0: check.setChecked(app.selected_roam); break;
			case 1: check.setChecked(app.selected_vpn); break;
			case 2: check.setChecked(app.selected_lan); break;
		}
		return check;
	}
	private CheckBox addSupport(CheckBox check, View convertView, boolean action, int id) {
		check = (CheckBox) convertView.findViewById(id);
		check.setVisibility(View.VISIBLE);
		if(action){
			check.setOnCheckedChangeListener(this);
		}
		return check;
	}
	
	private CheckBox removeSupport(CheckBox check, View convertView, int id) {
		check = (CheckBox) convertView.findViewById(id);
		check.setVisibility(View.GONE);
		return check;
	}
	
	
	static class ViewHolder { 
		private CheckBox box_lan;
		private CheckBox box_wifi;
		private CheckBox box_3g;
		private CheckBox box_roam;
		private CheckBox box_vpn;
		private TextView text;
		private ImageView icon;
		private PackageInfoData app;
	}
	
	/**
	 * Asynchronous task used to load icons in a background thread.
	 */
	private static class LoadIconTask extends AsyncTask<Object, Void, View> {
		@Override
		protected View doInBackground(Object... params) {
			try {
				final PackageInfoData app = (PackageInfoData) params[0];
				final PackageManager pkgMgr = (PackageManager) params[1];
				final View viewToUpdate = (View) params[2];
				if (!app.icon_loaded) {
					app.cached_icon = pkgMgr.getApplicationIcon(app.appinfo);
					app.icon_loaded = true;
				}
				// Return the view to update at "onPostExecute"
				// Note that we cannot be sure that this view still references
				// "app"
				return viewToUpdate;
			} catch (Exception e) {
				Log.e(TAG, "Error loading icon", e);
				return null;
			}
		}

		protected void onPostExecute(View viewToUpdate) {
			try {
				// This is executed in the UI thread, so it is safe to use
				// viewToUpdate.getTag()
				// and modify the UI
				final ViewHolder entryToUpdate = (ViewHolder) viewToUpdate.getTag();
				entryToUpdate.icon.setImageDrawable(entryToUpdate.app.cached_icon);
			} catch (Exception e) {
				Log.e(TAG, "Error showing icon", e);
			}
		};
	}

	/**
	 * Called an application is check/unchecked
	 */
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		final PackageInfoData app = (PackageInfoData) buttonView.getTag();
		if (app != null) {
			switch (buttonView.getId()) {
			case R.id.itemcheck_wifi:
				if (app.selected_wifi != isChecked) {
					app.selected_wifi = isChecked;
					MainActivity.dirty = true;
				}
				break;
			case R.id.itemcheck_3g:
				if (app.selected_3g != isChecked) {
					app.selected_3g = isChecked;
					MainActivity.dirty = true;
				}
				break;
			case R.id.itemcheck_roam:
				if (app.selected_roam != isChecked) {
					app.selected_roam = isChecked;
					MainActivity.dirty = true;
				}
				break;
			case R.id.itemcheck_vpn:
				if (app.selected_vpn != isChecked) {
					app.selected_vpn = isChecked;
					MainActivity.dirty = true;
				}
				break;
			case R.id.itemcheck_lan:
				if (app.selected_lan != isChecked) {
					app.selected_lan = isChecked;
					MainActivity.dirty = true;
				}
				break;
			}
		}
	}

}
