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

import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;

import dev.ukanth.ufirewall.RootShell.RootCommand;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;

public class RulesActivity extends DataDumpActivity {

	protected static final int MENU_FLUSH_RULES = 12;
	protected static final int MENU_IPV6_RULES = 19;
	protected static final int MENU_IPV4_RULES = 20;

	protected boolean showIPv6 = false;
	protected StringBuilder result;

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

	protected void populateData(final Context ctx) {
		result = new StringBuilder();

		// first step: fetch iptables rules
		writeHeading(result, false, showIPv6 ? "IPv6 Rules" : "IPv4 Rules");
		Api.fetchIptablesRules(ctx, showIPv6, new RootCommand()
			.setLogging(true)
			.setReopenShell(true)
			.setFailureToast(R.string.error_fetch)
			.setCallback(new RootCommand.Callback() {
				public void cbFunc(RootCommand state) {
					result.append(state.res);

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

							writeHeading(result, true, "ifconfig");
				    		Api.runIfconfig(ctx, new RootCommand()
				    			.setLogging(true)
								.setCallback(new RootCommand.Callback() {
									public void cbFunc(RootCommand state) {
										result.append(state.res);
										setData(result.toString());
									}
								}));
			    		}
			    	}.execute();
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
