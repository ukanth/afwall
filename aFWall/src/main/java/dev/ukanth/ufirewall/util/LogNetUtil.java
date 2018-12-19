package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Address;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import eu.chainfire.libsuperuser.Shell;

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

        private static final String PING_CMD = "%s ping -w 1 -W %d %s";

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
                    case PING:
                        // Ping
                        try {
                            String shell_result = "";
                            String command = "";
                            try {
                                // This command needs permission to allow
                                // AFWall+ itself to has access to network
                                // to work probably
                                command = String.format(PING_CMD, "", G.logPingTimeout(), params[0].address);
                                Log.d(TAG, "Execute CMD: " + command);
                                Process process = Runtime.getRuntime().exec(command);
                                process.waitFor();
                                Log.d(TAG, "CMD exit code: " + process.exitValue());
                                // check if ping command does not encounter any errors
                                if (process.exitValue() == 0) {
                                    //The ping was succeeded.
                                    shell_result = parse(process);
                                } else {
                                    shell_result = su_busyboox_ping(params[0].address);
                                }
                            } catch (Exception ping_cmd_ex) {
                                Log.e(TAG, "Exception(00): " + ping_cmd_ex.getMessage());
                                shell_result = su_busyboox_ping(params[0].address);
                            }
                            return shell_result;
                        } catch (Exception eex) {
                            Log.e(TAG, "Exception(01): " + eex.getMessage());
                            // final choice is to use Android API
                            return normal_ping(params[0].address);
                        }
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

        private String normal_ping(String ip) {
            String result = "";
            try {
                if (InetAddress.getByAddress(ip.getBytes()).isReachable(G.logPingTimeout() * 1000)) { // isReachable expect timeout in millisecond
                    result = String.format(context.getString(R.string.reachable_timeout), finish_time());
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception(04): " + e.getMessage());
                result = String.format("Currently IP(%s) is not Reachable, timeout: %d ms", ip, finish_time());
            }
            return result;
        }

        private String su_busyboox_ping(String ip) {
            // using libsuperuser to perform ping by Busybox,
            // This will need permission in AFWall+
            // "0:(root) Apps running as root"
            String result = "";
            String command = String.format(PING_CMD, Api.getBusyBoxPath(context, true), G.logPingTimeout(), ip);
            Log.d(TAG, "Execute CMD: " + command);
            result = parse(Shell.run("su", new String[]{command}, null, true));
            if (result.isEmpty()) {

                return context.getString(R.string.network_connection_not_available);
            }
            return result;
        }

        private String parse(List<String> output) {
            String result = "";
            for (String line : output) {
                result += line + " ";
            }
            if (result.isEmpty()) {
                return context.getString(R.string.output_is_empty);
            }
            return result;
        }

        private String parse(Process process) {
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
                Log.e(TAG, "Exception(05): " + e.getMessage());
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

        public NetParam(JobType type, String address) {
            this.type = type;
            this.address = address;
        }
    }

    public interface OnFinishRequest {
        void then(String result);
    }

}
