package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

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
public class LogRecyclerViewAdapter extends RecyclerView.Adapter<LogRecyclerViewAdapter.ViewHolder> {


    private final List<LogData> logData;
    private final Context context;
    private LogData data;
    private PackageInfo info;
    private static PrettyTime prettyTime;
    private final RecyclerItemClickListener recyclerItemClickListener;
    private View mView;

    public LogRecyclerViewAdapter(final Context context, RecyclerItemClickListener recyclerItemClickListener) {
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
        mView = LayoutInflater.from(parent.getContext()).inflate(R.layout.log_recycle_item, parent, false);
        return new ViewHolder(mView);
    }

    /*private Bitmap getAppIcon(PackageManager mPackageManager, ApplicationInfo applicationInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getAppIcon26(mPackageManager, applicationInfo);
        }
        try {
            Drawable drawable = mPackageManager.getApplicationIcon(applicationInfo);
            return ((BitmapDrawable) drawable).getBitmap();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Bitmap getAppIcon26(PackageManager mPackageManager, ApplicationInfo applicationInfo) {
        Drawable drawable = mPackageManager.getApplicationIcon(applicationInfo);
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof AdaptiveIconDrawable) {
            Drawable backgroundDr = ((AdaptiveIconDrawable) drawable).getBackground();
            Drawable foregroundDr = ((AdaptiveIconDrawable) drawable).getForeground();

            Drawable[] drr = new Drawable[2];
            drr[0] = backgroundDr;
            drr[1] = foregroundDr;

            LayerDrawable layerDrawable = new LayerDrawable(drr);

            int width = layerDrawable.getIntrinsicWidth();
            int height = layerDrawable.getIntrinsicHeight();

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmap);

            layerDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            layerDrawable.draw(canvas);

            return bitmap;
        }

        return null;
    }*/

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        data = logData.get(position);
        PackageManager manager = context.getPackageManager();
        holder.bind(logData.get(position),recyclerItemClickListener);
        try {
            info = Api.getPackageDetails(context, data.getUid());
            if(info!=null && info.applicationInfo !=null) {
                Object drawable = info.applicationInfo.loadIcon(manager);
                if (drawable instanceof Bitmap)
                    holder.icon.setImageBitmap((Bitmap)drawable);
                else
                    holder.icon.setBackground((Drawable)drawable);
            } else{
                Drawable appIcon = context.getDrawable(R.drawable.ic_unknown);
                holder.icon.setImageBitmap(Api.getBitmapFromDrawable(appIcon));
            }
        } catch (Exception e) {
            Log.e(TAG,e.getMessage(),e);
            info = null;
            try {
                if(Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.O) {
                    holder.icon.setBackground(context.getDrawable(R.drawable.ic_unknown));
                } else {
                    Drawable appIcon = context.getDrawable(R.drawable.ic_unknown);
                    holder.icon.setImageBitmap(Api.getBitmapFromDrawable(appIcon));
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
        if (data.getCount() > 1) {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_times));
        } else {
            holder.dataDenied.setText(context.getString(R.string.log_denied) + " " + data.getCount() + " " + context.getString(R.string.log_time));
        }
        holder.icon.invalidate();
    }

    public static String pretty(Date date) {
        if (prettyTime == null) {
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


    public static class ViewHolder extends RecyclerView.ViewHolder {

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
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
