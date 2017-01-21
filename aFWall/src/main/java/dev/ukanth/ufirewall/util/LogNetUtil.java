package dev.ukanth.ufirewall.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

/**
 * This file was created to simplify Network Function in AfWall+ log system
 * Created by vzool on 1/20/17.
 */

public class LogNetUtil {

    private static final String PING_CMD = "/system/bin/ping -q -n -w 1 -c 1 %s";

    public static class NetTask extends AsyncTask<NetParam, Integer, String>
    {
        long start_time;
        OnFinishRequest onFinishRequest_callback;
        MaterialDialog progress;
        Context context;
        String output_result = "";

        public NetTask(Context context){
            this.context = context;
        }

        long finish_time(){
            return System.currentTimeMillis() - start_time;
        }

        public NetTask setOnFinishRequest(OnFinishRequest when){
            onFinishRequest_callback = when;
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
        protected String doInBackground(NetParam... params)
        {
            start_time = System.currentTimeMillis();

            try{

                switch(params[0].type){

                    case PING:

                        // Ping

                        try{

                            /* [WIP] looking for ping timeout option */
                            String result = output(Runtime.getRuntime().exec(String.format(PING_CMD, /*G.logPingTimeout(), */params[0].address)));

                            if(result.isEmpty()){

                                result = context.getString(R.string.network_connection_not_available);
                            }

                            return result;

                        }catch (Exception eex){

                            try{

                                if(InetAddress.getByAddress(params[0].address.getBytes()).isReachable(G.logPingTimeout())){

                                    return String.format(context.getString(R.string.reachable_timeout), finish_time());

                                }

                            }catch(UnknownHostException ex){

                                return String.format("Currently IP(%s) is not Reachable, timeout: %d ms", params[0].address, finish_time());
                            }
                        }

                    case RESOLVE:

                        // Resolve

                        try{

                            return InetAddress.getByName(params[0].address).getCanonicalHostName();

                        }catch(UnknownHostException ex){

                            return String.format("Currently can not resolve Host for IP(%s), timeout: %d ms", params[0].address, finish_time());
                        }
                }

            }catch (Exception e){

                e.printStackTrace();
            }

            return context.getString(R.string.error_or_unknown_category);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if(onFinishRequest_callback != null){
                onFinishRequest_callback.then(s);
            }

            try {
                if ((progress != null) && progress.isShowing()) {
                    progress.dismiss();
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                // Handle or log or ignore
            } catch (final Exception e) {
                e.printStackTrace();
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
                            copyToClipboard(context, output_result);
                            Api.toast(context, context.getString(R.string.result_copied_to_clipboard));
                        }
                    })
                    .show();
        }

        String output(Process process){

            try {
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));

                // Grab the results
                StringBuilder log = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    log.append(line + "\n");
                }

                return log.toString();

            } catch (IOException e) {

                e.printStackTrace();
            }

            return context.getString(R.string.output_is_empty);
        }
    }

    public enum JobType {
        PING,
        RESOLVE
    }

    public static class NetParam {

        public JobType type;
        public String address;

        public NetParam(JobType type, String address){
            this.type = type;
            this.address = address;
        }
    }

    // Clipboard

    public static void copyToClipboard(Context context, String val){
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", val);
        clipboard.setPrimaryClip(clip);
    }

    public interface OnFinishRequest{
        void then(String result);
    }

}
