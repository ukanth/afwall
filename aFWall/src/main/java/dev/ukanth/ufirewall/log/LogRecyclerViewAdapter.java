package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.TimeUnit;
import org.ocpsoft.prettytime.units.JustNow;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogRecyclerViewAdapter  extends RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder>{
    private List<LogData> logData;
    private Context context;
    private LogData data;
    private PackageInfo info;
    private static PrettyTime prettyTime;

    public LogRecyclerViewAdapter(final Context context){
        this.context = context;
        logData = new ArrayList<>();
    }

    public void updateData(List<LogData> logDataList) {
        logData.clear();
        logData.addAll(logDataList);
        this.notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_recycle_item,parent,false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        try {
            info = Api.getPackageDetails(context, Integer.parseInt(data.getUid()));
            holder.icon.setImageDrawable(info.applicationInfo.loadIcon(context.getPackageManager()));
        } catch (Exception e) {
            info = null;
            holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_unknown_package));
        }

        try {
            if(data.getTimestamp() != null && !data.getTimestamp().isEmpty()) {
                holder.lastDenied.setText(pretty(new Date(System.currentTimeMillis() - Long.parseLong(data.getTimestamp()))));
            }
        } catch (Exception e) {
            holder.lastDenied.setText("-");
        }
        holder.appName.setText(data.getAppName() != null ? data.getAppName() + "(" + data.getUid() + ")" : context.getString(R.string.log_deletedapp));
        if(data.getCount() > 1) {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_times)) ;
        } else {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_time)) ;
        }
    }

    public static String pretty(Date date) {
        if(prettyTime == null) {
            prettyTime = new PrettyTime(new Locale(G.locale()));
            List<TimeUnit> timeUnits  = new ArrayList<>();
            for (TimeUnit t : prettyTime.getUnits()) {
                if (t instanceof JustNow) {
                    prettyTime.removeUnit(t);
                    break;
                }
            }
        }
        prettyTime.setReference(date);
        return prettyTime.format(new Date(0));
    }

    @Override
    public int getItemCount() {
        return logData.size();
    }


    public class ViewHolder  extends RecyclerView.ViewHolder{

        final ImageView icon;
        final TextView appName;
        final TextView lastDenied;
        final TextView dataDenied;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = (ImageView )itemView.findViewById(R.id.app_icon);
            appName = (TextView)itemView.findViewById(R.id.app_name);
            lastDenied = (TextView)itemView.findViewById(R.id.last_denied);
            dataDenied = (TextView)itemView.findViewById(R.id.data_denied);
        }
    }

}
