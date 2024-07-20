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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afollestad.materialdialogs.MaterialDialog;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.log.LogDetailRecyclerViewAdapter;
import dev.ukanth.ufirewall.log.LogPreference;
import dev.ukanth.ufirewall.log.LogPreference_Table;
import dev.ukanth.ufirewall.util.DateComparator;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.LogNetUtil;

public class LogDetailActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;

    private RecyclerView recyclerView;
    protected static String logDumpFile = "log_dump.log";

    private LogDetailRecyclerViewAdapter recyclerViewAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout mSwipeLayout;
    protected Menu mainMenu;
    private LogData current_selected_logData;
    private static List<LogData> logDataList;

    protected static final int MENU_EXPORT_LOG = 100;

    private static int uid;
    protected final int MENU_TOGGLE = -4;
    protected final int MENU_CLEAR = 40;

    final String TAG = "AFWall";

    private void initTheme() {
        switch (G.getSelectedTheme()) {
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
            LogPreference logPreference = SQLite.select()
                    .from(LogPreference.class)
                    .where(LogPreference_Table.uid.eq(uid)).querySingle();

            if (logPreference != null) {
                if(logPreference.isDisable()) {
                    menu.add(0, v.getId(), 7, R.string.displayBlockNotification_enable);
                } else {
                    menu.add(0, v.getId(), 8, R.string.displayBlockNotification);
                }
            } else {
                menu.add(0, v.getId(), 8, R.string.displayBlockNotification);
            }


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
            case 7:
                G.updateLogNotification(uid, false);
                break;
            case 8:
                G.updateLogNotification(uid, true);
                break;

        }
        return super.onContextItemSelected(item);
    }


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
            logDataList = getLogData(uid);
            try {
                if (logDataList != null && logDataList.size() > 0) {
                    Collections.sort(logDataList, new DateComparator());
                    recyclerViewAdapter.updateData(logDataList);
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                Log.e(Api.TAG, "Exception while retrieving data" + e.getLocalizedMessage());
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
        //sub.add(0, MENU, 0, R.string.clear_log).setIcon(R.drawable.ic_clearlog);
        //sub.add(0, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.exportr);
        //populateMenu(sub);
        sub.add(1, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.ic_export);
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
            case MENU_EXPORT_LOG:
                exportToSD();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void clearDatabase(final Context ctx) {
        new MaterialDialog.Builder(this)
                .title(getApplicationContext().getString(R.string.clear_log) + " ?")
                .cancelable(true)
                .onPositive((dialog, which) -> {
                    //SQLite.delete(LogData_Table.class);
                   // FlowManager.getDatabase(LogDatabase.NAME).reset();
                    SQLite.delete(LogData.class)
                            .where(LogData_Table.uid.eq(uid))
                            .async()
                            .execute();
                    Toast.makeText(getApplicationContext(), ctx.getString(R.string.log_cleared), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .show();
    }


    @Override
    public void onRefresh() {
        (new CollectDetailLog()).setContext(this).execute();
    }

    private static class Task extends AsyncTask<Void, Void, Boolean> {
        public String filename = "";
        private final Context ctx;

        private final WeakReference<LogDetailActivity> activityReference;

        // only retain a weak reference to the activity
        Task(LogDetailActivity context) {
            this.ctx = context;
            activityReference = new WeakReference<>(context);
        }


        @Override
        public Boolean doInBackground(Void... args) {
            FileOutputStream output = null;
            boolean res = false;

            try {
                File file;
                if(Build.VERSION.SDK_INT  < Build.VERSION_CODES.Q ){
                    File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" );
                    dir.mkdirs();
                    file = new File(dir, logDumpFile);
                } else{
                    file = new File(ctx.getExternalFilesDir(null) + "/" + logDumpFile) ;
                }
                output = new FileOutputStream(file);
                StringBuilder builder = new StringBuilder();
                builder.append("uid: " + uid);

                for(LogData data: logDataList) {
                    builder.append("src:").append(data.getSrc()).append(",")
                            .append("dst:").append(data.getDst()).append(",")
                            .append("proto:").append(data.getProto()).append(",")
                            .append("sport:").append(data.getSpt()).append(",")
                            .append("dport:").append(data.getDpt());
                    builder.append("\n");
                }
                output.write(builder.toString().getBytes());
                filename = file.getAbsolutePath();
                res = true;
            } catch (FileNotFoundException e) {
                Log.e(G.TAG,e.getMessage(),e);
            } catch (IOException e) {
                Log.e(G.TAG,e.getMessage(),e);
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                } catch (IOException ex) {
                    Log.e(G.TAG,ex.getMessage(),ex);
                }
            }
            return res;
        }

        @Override
        public void onPostExecute(Boolean res) {
            LogDetailActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) return;

            if (res) {
                Api.toast(ctx, ctx.getString(R.string.export_rules_success) + filename, Toast.LENGTH_LONG);
            } else {
                Api.toast(ctx, ctx.getString(R.string.export_logs_fail), Toast.LENGTH_LONG);
            }
        }
    }

        private void exportToSD() {

            if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.Q ){
                // Do some stuff
                new Task(this).execute();
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // permissions have not been granted.
                    ActivityCompat.requestPermissions(LogDetailActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
                } else{
                    new Task(this).execute();
                }
            }
        }

	/*@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		// setupLogMenuItem(menu, G.enableFirewallLog());
		return super.onPrepareOptionsMenu(menu);
	}*/


}
