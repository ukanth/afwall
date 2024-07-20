package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogDetailRecyclerViewAdapter extends RecyclerView.Adapter<LogDetailRecyclerViewAdapter.ViewHolder> {

    private final List<LogData> logData;
    private final Context context;
    private LogData data;
    private final RecyclerItemClickListener recyclerItemClickListener;

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
    @NonNull
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.logdetail_recycle_item, parent, false);
        return new ViewHolder(mView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        if (data != null) {
            holder.bind(data, recyclerItemClickListener);
            if (data.getOut() != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(data.getTimestamp());
                java.text.DateFormat dateFormat = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
                String dateTime = dateFormat.format(calendar.getTime());
                holder.deniedTime.setText(dateTime + " (" + data.getOut() + ")");
                if ((data.getOut().contains("lan") || data.getOut().startsWith("eth") || data.getOut().startsWith("ra") || data.getOut().startsWith("bnep"))) {
                    holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_wifi));
                } else {
                    holder.icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_mobiledata));
                }
            }

            holder.dataDest.setText(context.getResources().getString(R.string.log_dst) + data.getDst() + ":" + data.getDpt());
            holder.dataSrc.setText(context.getResources().getString(R.string.log_src) + data.getSrc() + ":" + data.getSpt());
            holder.dataProto.setText(context.getResources().getString(R.string.log_proto) + data.getProto());

            String hostName = data.getHostname();
            if (!hostName.isEmpty()) {
                holder.dataHost.setText(context.getResources().getString(R.string.host) + hostName);
            } else {
                holder.dataHost.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return logData.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final ImageView icon;
        final TextView deniedTime;
        final TextView dataDest;
        final TextView dataSrc;
        final TextView dataProto;
        final TextView dataHost;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.data_icon);
            deniedTime = itemView.findViewById(R.id.denied_time);
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
}
