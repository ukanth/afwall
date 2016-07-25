package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogRecyclerViewAdapter  extends RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder>{
    private List<LogData> logData;
    private Context context;
    private LogData data;

    public LogRecyclerViewAdapter(final Context context,List<LogData> logData ){
        this.context = context;
        this.logData = logData;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_recycle_item,parent,false);
        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        holder.icon.setImageDrawable(data.getIcon());
        holder.appname.setText(data.getAppName() != null ? data.getAppName(): context.getString(R.string.log_deletedapp));
        if(data.getCount() > 2) {
            holder.dataReceived.setText(context.getString(R.string.log_deletedapp) + data.getCount() + " " + context.getString(R.string.log_times)) ;
        } else {
            holder.dataReceived.setText(context.getString(R.string.log_deletedapp) + data.getCount() + " " + context.getString(R.string.log_time)) ;
        }
        holder.dataTransmitted.setText(context.getString(R.string.log_dst) + data.getDst());
        holder.packetsReceived.setText(context.getString(R.string.log_src)+ data.getSrc());
        holder.packetsTransmitted.setText(context.getString(R.string.log_proto)+ data.getProto());
    }


    @Override
    public int getItemCount() {
        return logData.size();
    }


    public class ViewHolder  extends RecyclerView.ViewHolder{

        final ImageView icon;
        final TextView appname;
        final TextView dataReceived;
        final TextView dataTransmitted;
        final TextView packetsReceived;
        final TextView packetsTransmitted;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = (ImageView )itemView.findViewById(R.id.app_icon);
            appname = (TextView)itemView.findViewById(R.id.app_name);
            dataReceived = (TextView)itemView.findViewById(R.id.data_received);
            dataTransmitted = (TextView)itemView.findViewById(R.id.data_transmitted);
            packetsReceived = (TextView)itemView.findViewById(R.id.packets_received);
            packetsTransmitted = (TextView)itemView.findViewById(R.id.packets_transmitted);
        }
    }

}
