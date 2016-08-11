/**
 * Display/purge logs and toggle logging
 * <p/>
 * Copyright (C) 2011-2013  Kevin Cernekee
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.SubMenu;

import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogInfo;
import dev.ukanth.ufirewall.service.RootShell.RootCommand;
import dev.ukanth.ufirewall.util.G;

public class OldLogActivity extends DataDumpActivity {

    protected static final int MENU_CLEARLOG = 7;
    protected static final int MENU_SWITCH_NEW = 70;
    //protected static final int MENU_TOGGLE_LOG = 27;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.showlog_title));
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        sdDumpFile = "iptables.log";
    }

    protected void parseAndSet(List<LogData> logDataList) {
        String cooked = LogInfo.parseLog(OldLogActivity.this,logDataList);
        if (cooked == null) {
            setData(getString(R.string.log_parse_error));
        } else {
            setData(cooked);
        }
    }

    protected void populateData(final Context ctx) {
            parseAndSet(Api.fetchLogs());
    }

    protected void populateMenu(SubMenu sub) {
        sub.add(0, MENU_CLEARLOG, 0, R.string.clear_log).setIcon(R.drawable.clearlog);
        sub.add(0, MENU_SWITCH_NEW, 0, R.string.switch_new).setIcon(R.drawable.logs);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Context ctx = this;

        switch (item.getItemId()) {

            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case MENU_SWITCH_NEW:
                Intent i = new Intent(this, LogActivity.class);
                G.oldLogView(false);
                startActivity(i);
                finish();
                return true;
            case MENU_CLEARLOG:
                Api.clearLog(ctx,
                        new RootCommand().setReopenShell(true)
                                .setSuccessToast(R.string.log_cleared)
                                .setFailureToast(R.string.log_clear_error)
                                .setCallback(new RootCommand.Callback() {
                                    public void cbFunc(RootCommand state) {
                                        populateData(ctx);
                                    }
                                }));
                return true;
        }
        return super.onOptionsItemSelected( item);
    }

}
