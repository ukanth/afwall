package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogRecyclerViewAdapter  extends RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder>{
    private List<LogData> logData;
    private Context context;
    private LogData data;
    private PackageInfo info;
    private Drawable icon;
    private PrettyTime prettyTime;
    private int uid;

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
            uid = Integer.parseInt(data.getUid());
            info = Api.getPackageDetails(context, uid);
            icon = info.applicationInfo.loadIcon(context.getPackageManager());
            holder.icon.setImageDrawable(icon);
        } catch (Exception e) {
            info = null;
            icon = null;
            holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_unknown_package));
        }

        try {
            prettyTime = new PrettyTime(new Date(System.currentTimeMillis() - Long.parseLong(data.getTimestamp())));
            if(data.getTimestamp() != null && !data.getTimestamp().isEmpty()) {
                //String time = prettyTime.format(new Date(Integer.parseInt(data.getTimestamp())));
                String time = prettyTime.format(new Date(0));
                holder.lastDenied.setText(time);
            }
        } catch (Exception e) {
        }
        holder.appname.setText(data.getAppName() != null ? data.getAppName() + "(" + data.getUid() + ")" : context.getString(R.string.log_deletedapp));

        if(data.getCount() > 1) {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_times)) ;
        } else {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_time)) ;
        }
        /*holder.dataDest.setText(context.getString(R.string.log_dst) + data.getDst());
        holder.dataSrc.setText(context.getString(R.string.log_src)+ data.getSrc());
        holder.dataProto.setText(context.getString(R.string.log_proto)+ data.getProto());*/
    }


    @Override
    public int getItemCount() {
        return logData.size();
    }


    public class ViewHolder  extends RecyclerView.ViewHolder{

        final ImageView icon;
        final TextView appname;
        final TextView lastDenied;
        final TextView dataDenied;
       /* final TextView dataDest;
        final TextView dataSrc;
        final TextView dataProto;*/

        public ViewHolder(View itemView) {
            super(itemView);
            icon = (ImageView )itemView.findViewById(R.id.app_icon);
            appname = (TextView)itemView.findViewById(R.id.app_name);
            lastDenied = (TextView)itemView.findViewById(R.id.last_denied);
            dataDenied = (TextView)itemView.findViewById(R.id.data_denied);
            /*dataDest = (TextView)itemView.findViewById(R.id.data_dest);
            dataSrc = (TextView)itemView.findViewById(R.id.data_src);
            dataProto = (TextView)itemView.findViewById(R.id.data_proto);*/
        }
    }

}
