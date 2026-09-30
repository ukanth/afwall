package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.net.InetAddress;
import java.net.UnknownHostException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;

/**
 * This file was created to simplify Network Function in AFWall+ log system
 * Created by vzool on 1/20/17.
 */

public class LogNetUtil {

    final static String TAG = "AFWall-LogNetUtil";

    public static class NetTask extends AsyncTask<NetParam, Integer, String> {
        long start_time;
        OnFinishRequest onFinishRequest;
        MaterialDialog progress;
        Context context;
        String output_result = "";

        public NetTask(Context context) {
            this.context = context;
        }

        long finish_time() {
            return System.currentTimeMillis() - start_time;
        }

        public NetTask setOnFinishRequest(OnFinishRequest when) {
            onFinishRequest = when;
            return this;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            progress = new MaterialDialog.Builder(context)
                    .title(R.string.searching)
                    .content(R.string.looking_for_data)
                    .progress(true, 0)
                    .progressIndeterminateStyle(true)
                    .show();
        }

        @Override
        protected String doInBackground(NetParam... params) {
            start_time = System.currentTimeMillis();
            try {
                switch (params[0].type) {
                    case RESOLVE:
                        // Resolve
                        try {
                            InetAddress inetAddress = InetAddress.getByName(params[0].address);
                            // String name = Address.getHostName(InetAddress.getByName(params[0].address));
                            if (inetAddress != null) {
                                return inetAddress.getHostName();
                            } else {
                                return "<Unable to resolve host>";
                            }
                        } catch (UnknownHostException ex) {
                            Log.e(TAG, "Exception(02): " + ex.getMessage());
                            return String.format("Currently can not resolve Host for IP(%s), timeout: %d ms", params[0].address, finish_time());
                        }
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception(03): " + e.getMessage());
            }
            return context.getString(R.string.error_or_unknown_category);
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if (onFinishRequest != null) {
                onFinishRequest.then(s);
            }

            try {
                if ((progress != null) && progress.isShowing()) {
                    progress.dismiss();
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, e.getMessage());
                // Handle or log or ignore
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                // Handle or log or ignore
            } finally {
                progress = null;
            }
            output_result = s;
            new MaterialDialog.Builder(context)
                    .content(s)
                    .title(R.string.result)
                    .neutralText(R.string.OK)
                    .positiveText(R.string.copy_text)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            Api.copyToClipboard(context, output_result);
                            Api.toast(context, context.getString(R.string.result_copied_to_clipboard));
                        }
                    }).show();
        }
    }

    public enum JobType {
        RESOLVE
    }

    public static class NetParam {

        public JobType type;
        public String address;

        public NetParam(JobType type, String address) {
            this.type = type;
            this.address = address;
        }
    }

    public interface OnFinishRequest {
        void then(String result);
    }

}
