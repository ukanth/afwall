/**
 * Capture Logs from dmesg and return the formatted string
 * <p>
 * <p>
 * Copyright (C) 2014  Umakanthan Chandran
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.log;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import org.xbill.DNS.Address;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

public class LogInfo {
    public String uidString;
    public String appName;
    public int uid;
    public String in;
    public String out;
    public String proto;
    public int spt;
    public String dst;
    public int len;
    public String src;
    public int dpt;
    public String host = "";
    public int type;
    public String timestamp;
    int totalBlocked;

    private HashMap<String, Integer> dstBlocked; // Number of packets blocked per destination IP address

    public LogInfo() {
        this.dstBlocked = new HashMap<String, Integer>();
    }


    public static String parseLog(Context ctx, List<LogData> listLogData) {

        //final BufferedReader r = new BufferedReader(new StringReader(dmesg.toString()));
        StringBuilder res = new StringBuilder();
        Integer appid;
        final SparseArray<LogInfo> map = new SparseArray<LogInfo>();
        LogInfo loginfo = null;

        try {
            for (LogData logData : listLogData) {
                appid = logData.getUid();

                loginfo = map.get(appid);
                if (loginfo == null) {
                    loginfo = new LogInfo();
                }

                loginfo.dst = logData.getDst();
                loginfo.dpt = logData.getDpt();
                loginfo.spt = logData.getSpt();
                loginfo.proto = logData.getProto();
                loginfo.len = logData.getLen();
                loginfo.src = logData.getSrc();
                loginfo.out = logData.getOut();
                map.put(appid, loginfo);
                loginfo.totalBlocked += 1;
                String unique = "[" + loginfo.proto + "]" + loginfo.dst + ":" + loginfo.dpt;
                if (loginfo.dstBlocked.containsKey(unique)) {
                    loginfo.dstBlocked.put(unique, loginfo.dstBlocked.get(unique) + 1);
                } else {
                    loginfo.dstBlocked.put(unique, 1);
                }
            }
            final List<PackageInfoData> apps = Api.getApps(ctx, null);
            Integer id;
            String appName = "";
            int appId = -1;
            int totalBlocked;
            for (int i = 0; i < map.size(); i++) {
                StringBuilder address = new StringBuilder();
                id = map.keyAt(i);
                if (id != -1) {
                    for (PackageInfoData app : apps) {
                        if (app.uid == id) {
                            appId = id;
                            appName = app.names.get(0);
                            break;
                        }
                    }
                } else {
                    appName = ctx.getString(R.string.unknown_item);
                }
                loginfo = map.valueAt(i);
                totalBlocked = loginfo.totalBlocked;
                if (loginfo.dstBlocked.size() > 0) {
                    for (String unique : loginfo.dstBlocked.keySet()) {
                        address.append(unique + "(" + loginfo.dstBlocked.get(unique) + ")");
                        address.append("\n");
                    }
                }
                res.append("AppID :\t");
                res.append(appId);
                res.append("\n");
                res.append(ctx.getString(R.string.LogAppName));
                res.append(":\t");
                res.append(appName);
                res.append("\n");
                res.append(ctx.getString(R.string.LogPackBlock));
                res.append(":\t");
                res.append(totalBlocked);
                res.append("\n");
                res.append(address.toString());
                res.append("\n\t---------\n");
            }
        } catch (Exception e) {
            return null;
        }
        if (res.length() == 0) {
            res.append(ctx.getString(R.string.no_log));
        }
        return res.toString();
    }


    public static LogInfo parseLogs(String result, final Context ctx, String pattern, int type) {
        StringBuilder address;
        int start, end;
        Integer uid = -11;
        Integer strUid;
        String out, src, dst, proto, spt, dpt, len;
        LogInfo logInfo = new LogInfo();

        HashMap<Integer, String> appNameMap = new HashMap<Integer, String>();
        final List<PackageInfoData> apps = Api.getApps(ctx, null);


        int pos = 0;
        try {
            while ((pos = result.indexOf(pattern, pos)) > -1) {
                if (result.indexOf(pattern) == -1)
                    continue;

                if (((start = result.indexOf("UID=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    strUid = Integer.parseInt(result.substring(start + 4, end));
                    if (strUid != null) {
                        uid = strUid;
                        logInfo.uid = strUid;
                    }
                }
                //logInfo = new LogInfo();
                if (((start = result.indexOf("DST=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    dst = result.substring(start + 4, end);
                    logInfo.dst = dst;
                }

                if (((start = result.indexOf("DPT=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    dpt = result.substring(start + 4, end);
                    logInfo.dpt = Integer.parseInt(dpt);
                }

                if (((start = result.indexOf("SPT=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    spt = result.substring(start + 4, end);
                    logInfo.spt = Integer.parseInt(spt);
                }

                if (((start = result.indexOf("PROTO=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    proto = result.substring(start + 6, end);
                    logInfo.proto = proto;
                }

                if (((start = result.indexOf("LEN=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    len = result.substring(start + 4, end);
                    logInfo.len = Integer.parseInt(len);
                }

                if (((start = result.indexOf("SRC=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    src = result.substring(start + 4, end);
                    logInfo.src = src;
                }

                if (((start = result.indexOf("OUT=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    out = result.substring(start + 4, end);
                    logInfo.out = out;
                }

                if (uid == android.os.Process.myUid()) {
                    return null;
                }
                String appName = "";
                if(logInfo.proto != null && logInfo.proto.toLowerCase().startsWith("icmp")) {
                    appName = "ICMP";
                    logInfo.uid = 0;
                } else if(uid == -11) {
                    appName = ctx.getString(R.string.kernel_item);
                    logInfo.uid = uid;
                } else {
                    if (uid < 2000) {
                        appName = Api.getSpecialAppName(uid);
                        if(uid == 1000) {
                            appName = ctx.getString(R.string.android_system);
                        }
                    } else {
                        //system level packages
                        try {
                            if (!appNameMap.containsKey(uid)) {
                                appName = ctx.getPackageManager().getNameForUid(uid);
                                for (PackageInfoData app : apps) {
                                    if (app.uid == uid) {
                                        appName = app.names.get(0);
                                        break;
                                    }
                                }
                            } else {
                                appName = appNameMap.get(uid);
                            }
                        } catch (Exception e) {
                            //could be kernel
                            Log.e(Api.TAG, "Exception in LogInfo when trying to find name for uid " + uid + "");
                            logInfo.uid = uid;
                            appName = ctx.getString(R.string.unknown_item);
                        }
                    }
                }

                logInfo.appName = appName;
                address = new StringBuilder();
                //address.append(ctx.getString(R.string.blocked));
                //address.append(" ");
                address.append(appName);
                address.append("(" + uid + ") ");
                address.append(logInfo.dst);
                address.append(":");
                address.append(logInfo.dpt);
                logInfo.type = type;
                if (G.showHost()) {
                    try {
                        String add  = InetAddress.getByName(logInfo.dst).getHostName();
                        if (add != null) {
                            logInfo.host = add;
                            address.append("(" + add + ") ");
                        }
                    } catch (Exception e) {
                    }
                }
                address.append("\n");
                logInfo.uidString = address.toString();
                return logInfo;
            }
        } catch (Exception e) {
            Log.e(Api.TAG, "Exception in LogService", e);
        }
        return logInfo;
    }
}
