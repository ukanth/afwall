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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogDetailRecyclerViewAdapter;
import dev.ukanth.ufirewall.util.DateComparator;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.LogNetUtil;

public class LogDetailActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private RecyclerView recyclerView;
    private LogDetailRecyclerViewAdapter recyclerViewAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout mSwipeLayout;
    protected Menu mainMenu;
    private LogData current_selected_logData;

    private int uid;
    protected static final int MENU_TOGGLE = -4;
    protected static final int MENU_CLEAR = 40;

    final String TAG = "AFWall";

    private void initTheme() {
        switch(G.getSelectedTheme()) {
            case "D":
                setTheme(R.style.AppDarkTheme);
                break;
            case "L":
                setTheme(R.style.AppLightTheme);
                break;
            case "B":
                setTheme(R.style.AppBlackTheme);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setContentView(R.layout.logdetail_view);
        Toolbar toolbar = findViewById(R.id.rule_toolbar);
        setTitle(getString(R.string.showlogdetail_title));
        toolbar.setNavigationOnClickListener(v -> finish());

        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        uid = intent.getIntExtra("DATA", -1);

        // Load partially transparent black background
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSwipeLayout = findViewById(R.id.swipedetailContainer);
        mSwipeLayout.setOnRefreshListener(this);

        recyclerView = findViewById(R.id.detailrecyclerview);
        emptyView = findViewById(R.id.emptydetail_view);

        initializeRecyclerView(getApplicationContext());

        (new CollectDetailLog()).setContext(this).execute();
    }

    private void initializeRecyclerView(final Context ctx) {
        recyclerView.hasFixedSize();
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        recyclerViewAdapter = new LogDetailRecyclerViewAdapter(getApplicationContext(), logData -> {
            current_selected_logData = logData;
            recyclerView.showContextMenu();
        });
        recyclerView.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            menu.setHeaderTitle(R.string.select_the_action);
            //groupId, itemId, order, title
            //menu.add(0, v.getId(), 0, R.string.add_ip_rule);
            menu.add(0, v.getId(), 1, R.string.show_destination_address);
            menu.add(0, v.getId(), 2, R.string.show_source_address);
            menu.add(0, v.getId(), 3, R.string.ping_destination);
            menu.add(0, v.getId(), 4, R.string.ping_source);
            menu.add(0, v.getId(), 5, R.string.resolve_destination);
            menu.add(0, v.getId(), 6, R.string.resolve_source);
        });
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {

        switch (item.getOrder()) {

            case 0: // Destination to clipboard
                String[] items = {current_selected_logData.getDst(), current_selected_logData.getSrc()};
                new MaterialDialog.Builder(this)
                        .items(items)
                        .itemsCallbackSingleChoice(-1, (dialog, view, which, text) -> true)
                        .positiveText(R.string.choose)
                        .show();
                break;
            case 1: // Destination to clipboard

                new MaterialDialog.Builder(this)
                        .content(current_selected_logData.getDst() + ":" + current_selected_logData.getDpt())
                        .title(R.string.destination_address)
                        .neutralText(R.string.OK)
                        .positiveText(R.string.copy_text)
                        .onPositive((dialog, which) -> {
                            Api.copyToClipboard(LogDetailActivity.this, current_selected_logData.getDst() + ":" + current_selected_logData.getDpt());
                            Api.toast(LogDetailActivity.this, getString(R.string.destination_copied));
                        })
                        .show();

                break;

            case 2: // Source to clipboard
                new MaterialDialog.Builder(this)
                        .content(current_selected_logData.getSrc() + ":" + current_selected_logData.getSpt())
                        .title(R.string.source_address)
                        .neutralText(R.string.OK)
                        .positiveText(R.string.copy_text)
                        .onPositive((dialog, which) -> {
                            Api.copyToClipboard(LogDetailActivity.this, current_selected_logData.getSrc() + ":" + current_selected_logData.getSpt());
                            Api.toast(LogDetailActivity.this, getString(R.string.source_copied));
                        })
                        .show();
                break;

            case 3: // Ping Destination
                new LogNetUtil.NetTask(this).execute(
                        new LogNetUtil.NetParam(LogNetUtil.JobType.PING, current_selected_logData.getDst())
                );

                break;

            case 4: // Ping Source
                new LogNetUtil.NetTask(this).execute(
                        new LogNetUtil.NetParam(LogNetUtil.JobType.PING, current_selected_logData.getSrc())
                );
                break;

            case 5: // Resolve Destination
                new LogNetUtil.NetTask(this).execute(
                        new LogNetUtil.NetParam(LogNetUtil.JobType.RESOLVE, current_selected_logData.getDst())
                );
                break;

            case 6: // Resolve Source
                new LogNetUtil.NetTask(this).execute(
                        new LogNetUtil.NetParam(LogNetUtil.JobType.RESOLVE, current_selected_logData.getSrc())
                );
                break;
        }
        return super.onContextItemSelected(item);
    }

   /* public class NetTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... params) {
            InetAddress addr = null;
            try {
                addr = InetAddress.getByName(params[0]);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Exception(09): " + e.getMessage());
            }
            return addr.getCanonicalHostName().toString();
        }
    }
*/

    private List<LogData> getLogData(final int uid) {
        return SQLite.select()
                .from(LogData.class)
                .where(LogData_Table.uid.eq(uid))
                .orderBy(LogData_Table.timestamp, false)
                .queryList();
    }

    private int getCount() {
        long l = SQLite.selectCountOf().from(LogData.class).where(LogData_Table.uid.eq(uid)).count();
        return (int) l;
    }


    private class CollectDetailLog extends AsyncTask<Void, Integer, Boolean> {
        private Context context = null;
        MaterialDialog loadDialog = null;

        public CollectDetailLog() {
        }
        //private boolean suAvailable = false;

        public CollectDetailLog setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            loadDialog = new MaterialDialog.Builder(context).cancelable(false).
                    title(getString(R.string.loading_data)).progress(false, getCount(), true).show();
            doProgress(0);
        }

        public void doProgress(int value) {
            publishProgress(value);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            List<LogData> logData = getLogData(uid);
            try {
                if (logData != null && logData.size() > 0) {
                    Collections.sort(logData, new DateComparator());
                    recyclerViewAdapter.updateData(logData);
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                Log.e(Api.TAG, "Exception while retrieving  data" + e.getLocalizedMessage());
                return null;
            }

        }

        @Override
        protected void onProgressUpdate(Integer... progress) {

            if (progress[0] == 0 || progress[0] == -1) {
                //do nothing
            } else {
                loadDialog.incrementProgress(progress[0]);
            }
        }

        @Override
        protected void onPostExecute(Boolean logPresent) {
            super.onPostExecute(logPresent);
            doProgress(-1);
            try {
                if ((loadDialog != null) && loadDialog.isShowing()) {
                    loadDialog.dismiss();
                }
            } catch (IllegalArgumentException e) {
                // Handle or log or ignore
            } catch (final Exception e) {
                // Handle or log or ignore
            } finally {
                loadDialog = null;
            }

            mSwipeLayout.setRefreshing(false);

            if (logPresent != null && logPresent) {
                recyclerView.setVisibility(View.VISIBLE);
                mSwipeLayout.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                recyclerViewAdapter.notifyDataSetChanged();
            } else {
                mSwipeLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Common options: Copy, Export to SD Card, Refresh
        SubMenu sub = menu.addSubMenu(0, MENU_TOGGLE, 0, "").setIcon(R.drawable.ic_flow);
        sub.add(0, MENU_CLEAR, 0, R.string.clear_log).setIcon(R.drawable.ic_clearlog);
        //sub.add(0, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.exportr);
        //populateMenu(sub);
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        super.onCreateOptionsMenu(menu);
        mainMenu = menu;
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case MENU_CLEAR:
                clearDatabase(getApplicationContext());
                return true;
            /*case MENU_EXPORT_LOG:
                //exportToSD();
                return true;*/
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDatabase(final Context ctx) {
        new MaterialDialog.Builder(this)
                .title(getApplicationContext().getString(R.string.clear_log) + " ?")
                .cancelable(true)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        //SQLite.delete(LogData_Table.class);
                        FlowManager.getDatabase(LogDatabase.NAME).reset();
                        Toast.makeText(getApplicationContext(), ctx.getString(R.string.log_cleared), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                })
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .show();
    }


    @Override
    public void onRefresh() {
        (new CollectDetailLog()).setContext(this).execute();
    }

	/*@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		// setupLogMenuItem(menu, G.enableFirewallLog());
		return super.onPrepareOptionsMenu(menu);
	}*/


}
