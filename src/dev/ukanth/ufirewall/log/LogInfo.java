/**
 * Capture Logs from dmesg and return the formatted string
 * 
 * 
 * Copyright (C) 2014  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.log;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Context;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.Api.PackageInfoData;
import dev.ukanth.ufirewall.R;

public class LogInfo {
	String uidString;
	String in;
	String out;
	String proto;
	String spt;
	String dst;
	String len;
	String src;
	String dpt;
	String timestamp;
	int totalBlocked; 
	
	
	public static String parseLog(Context ctx, String dmesg) {
		final BufferedReader r = new BufferedReader(new StringReader(dmesg.toString()));
		final Integer unknownUID = -99;
		StringBuilder res = new StringBuilder();
		String line;
		int start, end;
		Integer uid;
		String out, src, dst, proto, spt, dpt, len;
		ArrayList<LogInfo> logList = new ArrayList<LogInfo>();
		HashMap<String,Integer> totalCount = new HashMap<String, Integer>();
		HashMap<Integer,ArrayList<LogInfo>> logEntityMap = new HashMap<Integer, ArrayList<LogInfo>>();
		LogInfo logInfo = null;
		try {
			while ((line = r.readLine()) != null) {
				if (line.indexOf("{AFL}") == -1) continue;
				uid = unknownUID;
				if (((start=line.indexOf("UID=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					uid = Integer.parseInt(line.substring(start+4, end));
				}
				if (logEntityMap.get(uid) == null || logEntityMap.get(uid).size() == 0) {
					logInfo = new LogInfo();
					logList = new ArrayList<LogInfo>();
				} else {
					logList = logEntityMap.get(uid);
				}
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
				
				logList.add(logInfo);
				String uniqueKey = uid + ":" + logInfo.dst + ":" +  logInfo.dpt;
				if(!totalCount.containsKey(uniqueKey)) {
					totalCount.put(uniqueKey, 1);
				} else {
					int count = totalCount.get(uniqueKey);
					totalCount.put(uniqueKey, (count + 1));
				}
				logEntityMap.put(uid, logList);
			}
			final List<PackageInfoData> apps = Api.getApps(ctx,null);
			String appName = "";
			int appId = -99;
			for (Map.Entry<Integer, ArrayList<LogInfo>> entry : logEntityMap.entrySet())
			{
				appId = entry.getKey();
				if(appId != -99) {
					appName = ctx.getPackageManager().getNameForUid(appId);
					for (PackageInfoData app : apps) {
						if (app.uid == appId) {
							appName = app.names.get(0);
							break;
						}
					}
				} else {
					appName = ctx.getString(R.string.kernel_item);
				}
				
				StringBuilder address = new StringBuilder();
				res.append("Application = " +  appName + "(" + appId  + ")" + "\n");
				address.append("\n");
				Set<String> addedEntry = new HashSet<String>();
				for(LogInfo info : entry.getValue()) {
					String uniqueKey = appId + ":" + info.dst + ":" +  info.dpt;
					if(totalCount.containsKey(uniqueKey)) {
						if(!addedEntry.contains(uniqueKey)) {
							address.append( "[" + info.proto + "]" + info.dst + ":" +  info.dpt + "(" +  totalCount.get(uniqueKey) + ")" + "\n" );
							addedEntry.add(uniqueKey);
							info.totalBlocked = totalCount.get(uniqueKey); 
						}
					} else {
						if(!addedEntry.contains(uniqueKey)) {
							address.append( "[" + info.proto + "]" + info.dst + ":" +  info.dpt + "(" +  1 + ")" + "\n" );
							addedEntry.add(uniqueKey);
						}
					}
				}
				res.append(address.toString());
				res.append("\n\t-------------------------\n");
			}
		} catch (Exception e) {
			return null;
		}
		if (res.length() == 0) {
			res.append(ctx.getString(R.string.no_log));
		}
		return res.toString();
	}
}
