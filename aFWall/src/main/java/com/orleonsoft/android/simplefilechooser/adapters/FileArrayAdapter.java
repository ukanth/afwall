package com.orleonsoft.android.simplefilechooser.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.orleonsoft.android.simplefilechooser.Constants;
import com.orleonsoft.android.simplefilechooser.FileInfo;

import java.util.List;

import dev.ukanth.ufirewall.R;


@SuppressLint("DefaultLocale")
public class FileArrayAdapter extends ArrayAdapter<FileInfo> {

	private Context context;
	private int resorceID;
	private List<FileInfo> items;

	public FileArrayAdapter(Context context, int textViewResourceId,
			List<FileInfo> objects) {
		super(context, textViewResourceId, objects);
		this.context = context;
		this.resorceID = textViewResourceId;
		this.items = objects;
	}

	public FileInfo getItem(int i) {
		return items.get(i);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder viewHolder;
		if (convertView == null) {
			LayoutInflater layoutInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = layoutInflater.inflate(resorceID, null);
			viewHolder = new ViewHolder();
			viewHolder.icon = (ImageView) convertView
					.findViewById(android.R.id.icon);
			viewHolder.name = (TextView) convertView.findViewById(R.id.name);
			viewHolder.details = (TextView) convertView
					.findViewById(R.id.details);
			convertView.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) convertView.getTag();
		}

		FileInfo option = items.get(position);
		if (option != null) {

			if (option.getData().equalsIgnoreCase(Constants.FOLDER)) {
				viewHolder.icon.setImageResource(R.drawable.ic_action_folder);
			} else if (option.getData().equalsIgnoreCase(Constants.PARENT_FOLDER)) {
				viewHolder.icon.setImageResource(R.drawable.ic_action_back);
			} else {
				String name = option.getName().toLowerCase();
				if (name.endsWith(Constants.JSON) && !name.contains("all")) {
					viewHolder.icon.setImageResource(R.drawable.ic_launcher_free);
				} else if(name.endsWith(Constants.JSON) && name.contains("all")) {
					viewHolder.icon.setImageResource(R.drawable.ic_launcher);
				} else {
					viewHolder.icon.setImageResource(R.drawable.enable_log);
				}
			}

			viewHolder.name.setText(option.getName());
			viewHolder.details.setText(option.getData());

		}
		return convertView;
	}

	class ViewHolder {
		ImageView icon;
		TextView name;
		TextView details;
	}

}
