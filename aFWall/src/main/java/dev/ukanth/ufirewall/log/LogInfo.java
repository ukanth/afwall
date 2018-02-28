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

import java.util.HashMap;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.R;

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
    public String timestamp;
    int totalBlocked;

    private HashMap<String, Integer> dstBlocked; // Number of packets blocked per destination IP address

    public LogInfo() {
        this.dstBlocked = new HashMap<String, Integer>();
    }


    public static String parseLog(Context ctx, List<LogData> listLogData) {

        //final BufferedReader r = new BufferedReader(new StringReader(dmesg.toString()));
        final Integer unknownUID = -11;
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
                if (id != unknownUID) {
                    for (PackageInfoData app : apps) {
                        if (app.uid == id) {
                            appId = id;
                            appName = app.names.get(0);
                            break;
                        }
                    }
                } else {
                    appName = ctx.getString(R.string.kernel_item);
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

	/*public static void parseLog(Context ctx, String dmesg, TextView textView) {
        final BufferedReader r = new BufferedReader(new StringReader(dmesg.toString()));
		final Integer unknownUID = -1;
		StringBuilder address = new StringBuilder();
		String line;
		int start, end;
		Integer uid;
		String out, src, dst, proto, spt, dpt, len;
		HashMap<Integer,String> appNameMap = new HashMap<Integer, String>();
		LogInfo logInfo = null;
		
		final List<PackageInfoData> apps = Api.getApps(ctx,null);
		try {
			while ((line = r.readLine()) != null) {
				if (line.indexOf("{AFL}") == -1) continue;
				uid = unknownUID;
				if (((start=line.indexOf("UID=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					uid = Integer.parseInt(line.substring(start+4, end));
					if(uid != null) logInfo.uid = uid+"";
				}
				
				logInfo = new LogInfo();
				
				if (((start=line.indexOf("DST=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					dst = line.substring(start+4, end);
					logInfo.dst = dst;
				}
				
				
				if (((start=line.indexOf("DPT=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					dpt = line.substring(start+4, end);
					logInfo.dpt = dpt;
				}
				
				if (((start=line.indexOf("SPT=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					spt = line.substring(start+4, end);
					logInfo.spt = spt;
				}
				
				if (((start=line.indexOf("PROTO=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					proto = line.substring(start+6, end);
					logInfo.proto = proto;
				}
				
				if (((start=line.indexOf("LEN=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					len = line.substring(start+4, end);
					logInfo.len = len;
				}
				
				if (((start=line.indexOf("SRC=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					src = line.substring(start+4, end);
					logInfo.src = src;
				}
				
				if (((start=line.indexOf("OUT=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					out = line.substring(start+4, end);
					logInfo.out = out;
				}

				String appName = "";
				if(uid != -1) {
					if(!appNameMap.containsKey(uid)) {
						appName = ctx.getPackageManager().getNameForUid(uid);
						for (PackageInfoData app : apps) {
							if (app.uid == uid) {
								appName = app.names.get(0);
								break;
							}
						}
						appNameMap.put(uid, appName);
					} else {
						appName = appNameMap.get(uid);
					}
				} else {
					appName = ctx.getString(R.string.unknown_item);
				}
				logInfo.appName = appName;
				address = new StringBuilder();
				address.append(" " + appName + "(" + uid  + ")" + " ");
				address.append(logInfo.dst + ":" +  logInfo.dpt + "\n" );
				textView.append(address.toString());
			}
		} catch (Exception e) {
			Log.e(Api.TAG, e.getMessage());
		}
		
	}*/


    public static LogInfo parseLogs(String result, final Context ctx) {

        final int unknownUID = -11;
        StringBuilder address;
        int start, end;
        Integer uid;
        String out, src, dst, proto, spt, dpt, len;
        LogInfo logInfo = new LogInfo();

        HashMap<Integer, String> appNameMap = new HashMap<Integer, String>();
        final List<PackageInfoData> apps = Api.getApps(ctx, null);


        int pos = 0;
        try {
            while ((pos = result.indexOf("{AFL}", pos)) > -1) {
                if (result.indexOf("{AFL}") == -1)
                    continue;
                uid = unknownUID;

                if (((start = result.indexOf("UID=")) != -1)
                        && ((end = result.indexOf(" ", start)) != -1)) {
                    uid = Integer.parseInt(result.substring(start + 4, end));
                    if (uid != null) logInfo.uid = uid;
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
                String appName = "";
                if (uid.intValue() != unknownUID) {
                    if (uid == 1020) {
                        appName = "mDNS";
                    } else {
                        //system level packages
                        if (!appNameMap.containsKey(uid)) {
                            appName = ctx.getPackageManager().getNameForUid(uid);
                            for (PackageInfoData app : apps) {
                                if (app.uid == uid) {
                                    appName = app.names.get(0);
                                    break;
                                }
                            }
                            appNameMap.put(uid, appName);
                        } else {
                            appName = appNameMap.get(uid);
                        }
                    }

                } else {
                    appName = ctx.getString(R.string.kernel_item);
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
                address.append("\n");
                logInfo.uidString = address.toString();
                return logInfo;
                //return address.toString();

            }
        } catch (Exception e) {
            Log.e(Api.TAG, e.getMessage());
        }
        return logInfo;
    }
}
