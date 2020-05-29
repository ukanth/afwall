package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.os.Build;
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
import dev.ukanth.ufirewall.util.AppIconHelperV26;
import dev.ukanth.ufirewall.util.G;

import static dev.ukanth.ufirewall.Api.TAG;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogRecyclerViewAdapter  extends RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder>{


    private List<LogData> logData;
    private Context context;
    private LogData data;
    private PackageInfo info;
    private static PrettyTime prettyTime;
    private RecyclerItemClickListener recyclerItemClickListener;


    public LogRecyclerViewAdapter(final Context context,RecyclerItemClickListener recyclerItemClickListener){
        this.context = context;
        logData = new ArrayList<>();
        this.recyclerItemClickListener = recyclerItemClickListener;
    }

    public void updateData(List<LogData> logDataList) {
        logData.clear();
        logData.addAll(logDataList);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_recycle_item,parent,false);
        return new ViewHolder(mView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        holder.bind(logData.get(position),recyclerItemClickListener);
        try {
            info = Api.getPackageDetails(context, data.getUid());
            if(Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
                holder.icon.setBackground(info.applicationInfo.loadIcon(context.getPackageManager()));
            } else {
                holder.icon.setImageDrawable(info.applicationInfo.loadIcon(context.getPackageManager()));
            }
        } catch (Exception e) {
            Log.e(TAG,e.getMessage(),e);
            info = null;
            try {
                if(Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O) {
                    holder.icon.setBackground(context.getDrawable(R.drawable.ic_unknown));
                } else {
                    holder.icon.setImageDrawable(context.getDrawable(R.drawable.ic_unknown));
                }
            }catch (Exception e1) {
                Log.e(TAG,e1.getMessage(),e1);
            }
        }

        try {
            //if(data.getTimestamp() != null && !data.getTimestamp().isEmpty()) {
                holder.lastDenied.setText(pretty(new Date(System.currentTimeMillis() - data.getTimestamp())));
            //}
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
            icon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            lastDenied = itemView.findViewById(R.id.last_denied);
            dataDenied = itemView.findViewById(R.id.data_denied);
        }

        public void bind(final LogData item, final RecyclerItemClickListener listener) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    listener.onItemClick(item);
                }
            });
        }
    }

    public List<LogData> getLogData() {
        return logData;
    }

}
