package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 25/7/16.
 */
public class LogDetailRecyclerViewAdapter extends RecyclerView.Adapter<LogDetailRecyclerViewAdapter.ViewHolder> {


    private final List<LogData> logData;
    private final Context context;
    private LogData data;
    private final RecyclerItemClickListener recyclerItemClickListener;
    
    // Track repeated connection attempts for better UX
    private final Map<String, Integer> connectionAttempts = new ConcurrentHashMap<>();
    
    // Cache for expensive operations
    private final Map<Integer, String> serviceCache = new HashMap<>();
    private final Map<String, String> interfaceNameCache = new HashMap<>();


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
            
            // Format timestamp with interface info (cached)
            String timeAndInterface = pretty(data.getTimestamp());
            if (data.getOut() != null && !data.getOut().isEmpty()) {
                String interfaceType = getCachedInterfaceDisplayName(data.getOut());
                timeAndInterface += " via " + interfaceType;
            }
            holder.deniedTime.setText(timeAndInterface);
            
            // Set connection type icon with better logic
            setConnectionIcon(holder.icon, data.getOut());
            
            // Format destination with better readability
            String destination = data.getDst() + ":" + data.getDpt();
            holder.dataDest.setText(destination);
            
            // Format source with better readability  
            String source = data.getSrc() + ":" + data.getSpt();
            holder.dataSrc.setText(source);
            
            // Format protocol with more context (cached)
            String protocol = data.getProto();
            if (protocol != null) {
                protocol = protocol.toUpperCase();
                // Add service context for common ports (cached)
                String serviceInfo = getCachedServiceInfo(data.getDpt(), protocol);
                if (!serviceInfo.isEmpty()) {
                    protocol += " (" + serviceInfo + ")";
                }
            }
            holder.dataProto.setText(protocol != null ? protocol : "Unknown");
            
            // Format hostname with better handling
            if (data.getHostname() != null && !data.getHostname().trim().isEmpty() && !data.getHostname().equals(data.getDst())) {
                holder.dataHost.setText(data.getHostname());
                holder.dataHost.setVisibility(View.VISIBLE);
            } else {
                holder.dataHost.setVisibility(View.GONE);
            }
            
            // Show packet size if available
            if (data.getLen() > 0 && holder.packetSize != null) {
                holder.packetSize.setText(formatBytes(data.getLen()));
                holder.packetSize.setVisibility(View.VISIBLE);
            } else if (holder.packetSize != null) {
                holder.packetSize.setVisibility(View.GONE);
            }
            
            // Hide block count for now to improve performance - can be re-enabled later
            if (holder.blockCount != null) {
                holder.blockCount.setVisibility(View.GONE);
            }
        }
    }
    
    private void setConnectionIcon(ImageView icon, String outInterface) {
        if (outInterface == null || outInterface.isEmpty()) {
            icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_help));
            return;
        }
        
        if (outInterface.contains("lan") || outInterface.startsWith("eth") || 
            outInterface.startsWith("ra") || outInterface.startsWith("bnep") ||
            outInterface.contains("wlan") || outInterface.contains("wifi")) {
            icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_wifi));
        } else if (outInterface.contains("mobile") || outInterface.contains("rmnet") ||
                  outInterface.contains("ccmni") || outInterface.contains("pdp")) {
            icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_mobiledata));
        } else if (outInterface.contains("tun") || outInterface.contains("ppp")) {
            icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_lan)); // Use lan icon for VPN as fallback
        } else {
            icon.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_help)); // Use help icon as fallback
        }
    }
    
    private String getCachedInterfaceDisplayName(String outInterface) {
        if (outInterface == null || outInterface.isEmpty()) {
            return "Unknown";
        }
        
        // Check cache first
        if (interfaceNameCache.containsKey(outInterface)) {
            return interfaceNameCache.get(outInterface);
        }
        
        String displayName = getInterfaceDisplayName(outInterface);
        interfaceNameCache.put(outInterface, displayName);
        return displayName;
    }
    
    private String getInterfaceDisplayName(String outInterface) {
        if (outInterface == null || outInterface.isEmpty()) {
            return "Unknown";
        }
        
        if (outInterface.contains("lan") || outInterface.startsWith("eth") || 
            outInterface.startsWith("ra") || outInterface.startsWith("bnep") ||
            outInterface.contains("wlan") || outInterface.contains("wifi")) {
            return "Wi-Fi";
        } else if (outInterface.contains("mobile") || outInterface.contains("rmnet") ||
                  outInterface.contains("ccmni") || outInterface.contains("pdp")) {
            return "Mobile Data";
        } else if (outInterface.contains("tun") || outInterface.contains("ppp")) {
            return "VPN";
        } else {
            return outInterface;
        }
    }
    
    private String getCachedServiceInfo(int port, String protocol) {
        int key = (protocol != null ? protocol.hashCode() : 0) * 100000 + port;
        
        // Check cache first
        if (serviceCache.containsKey(key)) {
            return serviceCache.get(key);
        }
        
        String serviceInfo = getServiceInfo(port, protocol);
        serviceCache.put(key, serviceInfo);
        return serviceInfo;
    }
    
    private String getServiceInfo(int port, String protocol) {
        if ("TCP".equals(protocol)) {
            switch (port) {
                case 80: return "HTTP";
                case 443: return "HTTPS";
                case 21: return "FTP";
                case 22: return "SSH";
                case 23: return "Telnet";
                case 25: return "SMTP";
                case 53: return "DNS";
                case 110: return "POP3";
                case 143: return "IMAP";
                case 993: return "IMAPS";
                case 995: return "POP3S";
                case 1723: return "PPTP";
                case 3389: return "RDP";
                case 5060: return "SIP";
                case 8080: return "HTTP Alt";
            }
        } else if ("UDP".equals(protocol)) {
            switch (port) {
                case 53: return "DNS";
                case 67: return "DHCP Server";
                case 68: return "DHCP Client";
                case 123: return "NTP";
                case 500: return "IPSec";
                case 1194: return "OpenVPN";
                case 5060: return "SIP";
            }
        }
        return "";
    }
    
    private String formatBytes(int bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }

    public static String pretty(Long timestamp) {
        return android.text.format.DateFormat.format("dd-MM-yyyy hh:mm:ss", new java.util.Date(timestamp)).toString();
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
        final TextView packetSize;
        final TextView blockCount;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.data_icon);
            deniedTime = itemView.findViewById(R.id.denied_time);
            dataDest = itemView.findViewById(R.id.data_dest);
            dataSrc = itemView.findViewById(R.id.data_src);
            dataProto = itemView.findViewById(R.id.data_proto);
            dataHost = itemView.findViewById(R.id.data_host);
            packetSize = itemView.findViewById(R.id.packet_size);
            blockCount = itemView.findViewById(R.id.block_count);
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
