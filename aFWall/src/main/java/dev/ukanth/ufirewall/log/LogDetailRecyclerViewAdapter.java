package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogDetailRecyclerViewAdapter extends RecyclerView.Adapter<LogDetailRecyclerViewAdapter.ViewHolder> {


    private List<LogData> logData;
    private Context context;
    private LogData data;
    private RecyclerItemClickListener recyclerItemClickListener;


    public LogDetailRecyclerViewAdapter(final Context context, RecyclerItemClickListener recyclerItemClickListener) {
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
        View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.logdetail_recycle_item, parent, false);
        return new ViewHolder(mView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        if (data != null) {
            holder.bind(logData.get(position), recyclerItemClickListener);
            if (data.getOut() != null && (data.getOut().contains("lan") || data.getOut().startsWith("eth") || data.getOut().startsWith("ra") || data.getOut().startsWith("bnep"))) {
                holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_wifi));
                holder.deniedTime.setText(pretty(data.getTimestamp()) + "(" + data.getOut() + ")");
            } else {
                holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_mobiledata));
            }
            holder.dataDest.setText(context.getResources().getString(R.string.log_dst) + data.getDst() + ":" + data.getDpt());
            holder.dataSrc.setText(context.getResources().getString(R.string.log_src) + data.getSrc() + ":" + data.getSpt());
            holder.dataProto.setText(context.getResources().getString(R.string.log_proto) + data.getProto());
            holder.dataHost.setText(context.getResources().getString(R.string.host) + data.getHostname());
        }
    }

    public static String pretty(Long timestamp) {
        return android.text.format.DateFormat.format("dd-MM-yyyy hh:mm:ss", new java.util.Date(timestamp)).toString();
    }

    @Override
    public int getItemCount() {
        return logData.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {

        final ImageView icon;
        //final TextView appName;
        final TextView deniedTime;
        //final TextView dataInterface;
        final TextView dataDest;
        final TextView dataSrc;
        final TextView dataProto;
        final TextView dataHost;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.data_icon);
            //appName = (TextView)itemView.findViewById(R.id.app_name);
            deniedTime = itemView.findViewById(R.id.denied_time);
            //dataInterface = (TextView)itemView.findViewById(R.id.data_interface);
            dataDest = itemView.findViewById(R.id.data_dest);
            dataSrc = itemView.findViewById(R.id.data_src);
            dataProto = itemView.findViewById(R.id.data_proto);
            dataHost = itemView.findViewById(R.id.data_host);
        }

        public void bind(final LogData item, final RecyclerItemClickListener listener) {
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onItemClick(item);
                }
            });
        }
    }

    public List<LogData> getLogData() {
        return logData;
    }

}
