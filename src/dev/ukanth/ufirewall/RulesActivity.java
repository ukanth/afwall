/**
 * Display firewall rules and interface info
 * 
 * Copyright (C) 2011-2013  Kevin Cernekee
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
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall;

import java.io.File;
import java.util.Map;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;

import dev.ukanth.ufirewall.RootShell.RootCommand;

public class RulesActivity extends DataDumpActivity {

	protected static final int MENU_FLUSH_RULES = 12;
	protected static final int MENU_IPV6_RULES = 19;
	protected static final int MENU_IPV4_RULES = 20;
	protected static final int MENU_SEND_REPORT = 25;

	protected boolean showIPv6 = false;
	protected static StringBuilder result;

	@Override
    protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(getString(R.string.showrules_title));
		sdDumpFile = "rules.log";
	}

	protected void populateMenu(SubMenu sub) {
		if (G.enableIPv6()) {
			sub.add(0, MENU_IPV6_RULES, 0, R.string.switch_ipv6).setIcon(R.drawable.rules);
			sub.add(0, MENU_IPV4_RULES, 0, R.string.switch_ipv4).setIcon(R.drawable.rules);
		}
		sub.add(0, MENU_FLUSH_RULES, 0, R.string.flush).setIcon(R.drawable.clearlog);
		sub.add(0, MENU_SEND_REPORT, 0, R.string.send_report).setIcon(R.drawable.ic_dialog_email);
	}

	private void writeHeading(StringBuilder res, boolean initialNewline, String title) {
		StringBuilder eq = new StringBuilder();

		for (int i = 0; i < title.length(); i++) {
			eq.append('=');
		}

		if (initialNewline) {
			res.append("\n");
		}
		res.append(eq + "\n" + title + "\n" + eq + "\n\n");
	}

	protected void appendPreferences(final Context ctx) {
		// Fifth section: "Preferences"
		writeHeading(result, true, "Preferences");

		try {
			Map<String,?> prefs = G.gPrefs.getAll();
			for (String s : new TreeSet<String>(prefs.keySet())) {
				Object entry = prefs.get(s);
				result.append(s + ": " + entry.toString() + "\n");
			}
		} catch (NullPointerException e) {
			result.append("Error retrieving preferences\n");
		}

		// Sixth section: "Logcat"
		writeHeading(result, true, "Logcat");
		result.append(Log.getLog());

		// finished: post result to the user
		setData(result.toString());
	}

	protected String getFileInfo(String filename) {
		File f = new File(filename);
		if (f.exists() && f.isFile()) {
			return filename + ": " +
					f.length() + " bytes\n";
		} else {
			return filename + ": not present\n";
		}
	}

	protected String getSuInfo(PackageManager pm) {
		String suPackages[] = { "com.koushikdutta.superuser",
				"com.noshufou.android.su",
				"com.noshufou.android.su.elite",
				"com.miui.uac",
				"eu.chainfire.supersu",
				"eu.chainfire.supersu.pro" };
		String found = "none found";

		for (String s : suPackages) {
			try {
				PackageInfo info = pm.getPackageInfo(s, 0);
				found = s + " v" + info.versionName;
				break;
			} catch (NameNotFoundException e) {
			}
		}

		return found;
	}

	protected void appendSystemInfo(final Context ctx) {
		// Fourth section: "System info"
		writeHeading(result, true, "System info");

		InterfaceDetails cfg = InterfaceTracker.getCurrentCfg(ctx);

		result.append("Android version: " + android.os.Build.VERSION.RELEASE + "\n");
		result.append("Manufacturer: " + android.os.Build.MANUFACTURER + "\n");
		result.append("Model: " + android.os.Build.MODEL + "\n");
		result.append("Build: " + android.os.Build.DISPLAY + "\n");

		if (cfg.netType == ConnectivityManager.TYPE_MOBILE) {
			result.append("Active interface: mobile\n");
		} else if (cfg.netType == ConnectivityManager.TYPE_WIFI) {
			result.append("Active interface: wifi\n");
		} else {
			result.append("Active interface: unknown\n");
		}
		result.append("Tether status: " + (cfg.tetherStatusKnown ? (cfg.isTethered ? "yes" : "no") : "unknown") + "\n");
		result.append("Roam status: " + (cfg.isRoaming ? "yes" : "no") + "\n");
		result.append("IPv4 subnet: " + cfg.lanMaskV4 + "\n");
		result.append("IPv6 subnet: " + cfg.lanMaskV6 + "\n");

		// filesystem calls can block, so run in another thread
		new AsyncTask<Void, Void, String>() {
			@Override
			public String doInBackground(Void... args) {
				StringBuilder ret = new StringBuilder();

				ret.append(getFileInfo("/system/bin/su"));
				ret.append(getFileInfo("/system/xbin/su"));
				ret.append(getFileInfo("/system/app/Superuser.apk"));

				PackageManager pm = ctx.getPackageManager();
				ret.append("Superuser: " + getSuInfo(pm));
				ret.append("\n");

				return ret.toString();
			}

			@Override
			public void onPostExecute(String suInfo) {
				result.append(suInfo);
				appendPreferences(ctx);
			}
		}.execute();

	}

	protected void appendIfconfig(final Context ctx) {
		// Third section: "ifconfig" (for interface info obtained through busybox)
		writeHeading(result, true, "ifconfig");
		Api.runIfconfig(ctx, new RootCommand()
			.setLogging(true)
			.setCallback(new RootCommand.Callback() {
				public void cbFunc(RootCommand state) {
					result.append(state.res);
					appendSystemInfo(ctx);
				}
			}));
	}

	protected void appendNetworkInterfaces(final Context ctx) {
		// Second section: "Network Interfaces" (for interface info obtained through Android APIs)
		writeHeading(result, true, "Network interfaces");
		new AsyncTask<Void, Void, String>() {
			@Override
			public String doInBackground(Void... args) {
				StringBuilder ret = new StringBuilder();

				// filesystem calls can block, so run in another thread
				for (String s : Api.interfaceInfo(true)) {
					ret.append(s + "\n");
				}
				return ret.toString();
			}

			@Override
			public void onPostExecute(String ifaceList) {
				result.append(ifaceList);
				appendIfconfig(ctx);
			}
		}.execute();
	}

	protected void populateData(final Context ctx) {
		result = new StringBuilder();

		// First section: "IPxx Rules"
		writeHeading(result, false, showIPv6 ? "IPv6 Rules" : "IPv4 Rules");
		Api.fetchIptablesRules(ctx, showIPv6, new RootCommand()
			.setLogging(true)
			.setReopenShell(true)
			.setFailureToast(R.string.error_fetch)
			.setCallback(new RootCommand.Callback() {
				public void cbFunc(RootCommand state) {
					result.append(state.res);
					appendNetworkInterfaces(ctx);
				}
			}));
	}

    @Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		final Context ctx = this;

    	switch (item.getItemId()) {
    	case MENU_FLUSH_RULES:
    		flushAllRules(ctx);
			return true;
    	case MENU_IPV6_RULES:
    		showIPv6 = true;
    		populateData(this);
    		return true;
    	case MENU_IPV4_RULES:
    		showIPv6 = false;
    		populateData(this);
    		return true;
    	case MENU_SEND_REPORT:
    		String ver;
    		try {
    			ver = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
    		} catch (NameNotFoundException e) {
    			ver = "???";
    		}
    		String body = dataText + "\n\n" + getString(R.string.enter_problem) + "\n\n";
    		/*String uriText = "mailto:afwall-report@googlegroups.com?subject=" +
    				Uri.encode("AFWall+ problem report - v" + ver) + "&body=" +
    				Uri.encode(body);*/
    		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
    		//email.setData(Uri.parse(uriText));
    		
    		emailIntent.setType("plain/text");
    		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"afwall-report@googlegroups.com"});
    		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "AFWall+ problem report - v" + ver);
    		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
    		startActivity(Intent.createChooser(emailIntent, getString(R.string.send_mail)));

    		// this shouldn't be necessary, but the default Android email client overrides
    		// "body=" from the URI.  See MessageCompose.initFromIntent()
    		//email.putExtra(Intent.EXTRA_TEXT, body);

    		//startActivity(Intent.createChooser(email, getString(R.string.send_mail)));
    		return true;
    	}
    	return super.onMenuItemSelected(featureId, item);
    }

	private void flushAllRules(final Context ctx) {
		new AlertDialog.Builder(ctx)
			.setMessage(getString(R.string.flushRulesConfirm))
			.setCancelable(false)
			.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   	dialog.cancel();
						Api.flushAllRules(ctx, new RootCommand()
						.setReopenShell(true)
						.setSuccessToast(R.string.flushed)
						.setFailureToast(R.string.error_purge)
						.setCallback(new RootCommand.Callback() {
							public void cbFunc(RootCommand state) {
								populateData(ctx);
							}
						}));
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
	           }
	    }).create().show();
	}
}
