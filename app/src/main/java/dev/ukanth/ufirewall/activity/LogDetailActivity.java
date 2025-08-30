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
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private static List<LogData> fullLogDataList; // Store full dataset
    
    // Pagination
    private static final int PAGE_SIZE = 100; // Load 100 items at a time
    private int currentPage = 0;
    private boolean isLoading = false;
    
    // Summary views
    private TextView totalBlocks;
    private TextView uniqueDestinations;
    private TextView timePeriod;
    private TextView mostBlockedDestination;
    private TextView loadingMoreIndicator;

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
        
        // Initialize summary views
        totalBlocks = findViewById(R.id.total_blocks);
        uniqueDestinations = findViewById(R.id.unique_destinations);
        timePeriod = findViewById(R.id.time_period);
        mostBlockedDestination = findViewById(R.id.most_blocked_destination);
        loadingMoreIndicator = findViewById(R.id.loading_more_indicator);

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
            menu.add(0, v.getId(), 9, "Block this destination permanently");
            menu.add(0, v.getId(), 10, "Whitelist this destination");
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
            case 9: // Block destination permanently
                showBlockDestinationDialog();
                break;
            case 10: // Whitelist destination
                showWhitelistDestinationDialog();
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
    
    private List<LogData> getPagedLogData(final int uid, int page, int pageSize) {
        int offset = page * pageSize;
        return SQLite.select()
                .from(LogData.class)
                .where(LogData_Table.uid.eq(uid))
                .orderBy(LogData_Table.timestamp, false)
                .limit(pageSize)
                .offset(offset)
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
            try {
                // First, get total count for statistics
                int totalCount = getCount();
                publishProgress(totalCount);
                
                if (totalCount > PAGE_SIZE) {
                    // Large dataset - use pagination
                    fullLogDataList = getLogData(uid); // Get full list for statistics
                    logDataList = getPagedLogData(uid, 0, PAGE_SIZE); // Get first page
                    currentPage = 0;
                } else {
                    // Small dataset - load all
                    logDataList = getLogData(uid);
                    fullLogDataList = logDataList;
                }
                
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
                
                // Update title with log count
                updateTitleWithLogCount();
                
                // Update summary statistics using full dataset
                updateSummaryStatistics();
                
                // Setup load more functionality for large datasets
                if (fullLogDataList != null && fullLogDataList.size() > PAGE_SIZE) {
                    setupLoadMoreFunctionality();
                }
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
    
    private void updateTitleWithLogCount() {
        if (logDataList != null && logDataList.size() > 0) {
            String appName = "";
            if (logDataList.get(0).getAppName() != null) {
                appName = logDataList.get(0).getAppName();
            }
            String title = appName + " (" + logDataList.size() + " blocked)";
            setTitle(title);
        }
    }
    
    private void showBlockDestinationDialog() {
        if (current_selected_logData == null) return;
        
        new MaterialDialog.Builder(this)
            .title("Block Destination")
            .content("Add a permanent rule to block all connections to " + 
                    current_selected_logData.getDst() + ":" + current_selected_logData.getDpt() + "?")
            .positiveText("Block")
            .negativeText("Cancel")
            .onPositive((dialog, which) -> {
                // Here you would integrate with AFWall's custom rule system
                // This is a placeholder for the actual implementation
                Api.toast(this, "Feature requires integration with custom rules system");
            })
            .show();
    }
    
    private void showWhitelistDestinationDialog() {
        if (current_selected_logData == null) return;
        
        new MaterialDialog.Builder(this)
            .title("Whitelist Destination")
            .content("Add a permanent rule to allow all connections to " + 
                    current_selected_logData.getDst() + ":" + current_selected_logData.getDpt() + "?")
            .positiveText("Allow")
            .negativeText("Cancel")
            .onPositive((dialog, which) -> {
                // Here you would integrate with AFWall's custom rule system
                // This is a placeholder for the actual implementation
                Api.toast(this, "Feature requires integration with custom rules system");
            })
            .show();
    }
    
    private void updateSummaryStatistics() {
        if (fullLogDataList == null || fullLogDataList.isEmpty()) {
            return;
        }
        
        // Show basic count immediately for better UX (full dataset count)
        totalBlocks.setText(String.valueOf(fullLogDataList.size()));
        
        // Process complex statistics in background thread using full dataset
        new Thread(() -> {
            // Calculate unique destinations and most blocked
            java.util.Map<String, Integer> destinationCounts = new java.util.HashMap<>();
            java.util.Set<String> uniqueDests = new java.util.HashSet<>();
            
            long oldestTimestamp = Long.MAX_VALUE;
            long newestTimestamp = Long.MIN_VALUE;
            
            for (LogData logData : fullLogDataList) {
                String destination = logData.getDst() + ":" + logData.getDpt();
                uniqueDests.add(destination);
                destinationCounts.put(destination, destinationCounts.getOrDefault(destination, 0) + 1);
                
                // Track time range
                oldestTimestamp = Math.min(oldestTimestamp, logData.getTimestamp());
                newestTimestamp = Math.max(newestTimestamp, logData.getTimestamp());
            }
            
            final int uniqueCount = uniqueDests.size();
            final String period = (oldestTimestamp != Long.MAX_VALUE && newestTimestamp != Long.MIN_VALUE) ? 
                    formatTimePeriod(newestTimestamp - oldestTimestamp) : "0s";
            
            // Find most blocked destination
            String mostBlocked = "No data";
            int maxCount = 0;
            for (java.util.Map.Entry<String, Integer> entry : destinationCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    mostBlocked = entry.getKey() + " (" + maxCount + "x)";
                }
            }
            final String finalMostBlocked = mostBlocked;
            
            // Update UI on main thread
            runOnUiThread(() -> {
                uniqueDestinations.setText(String.valueOf(uniqueCount));
                timePeriod.setText(period);
                mostBlockedDestination.setText(finalMostBlocked);
            });
        }).start();
    }
    
    private void setupLoadMoreFunctionality() {
        // Add scroll listener to load more data when user reaches bottom
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    
                    // Load more when we're near the bottom
                    if ((visibleItemCount + firstVisibleItem) >= totalItemCount - 10) {
                        loadMoreData();
                    }
                }
            }
        });
    }
    
    private void loadMoreData() {
        if (isLoading || fullLogDataList == null) return;
        
        int totalAvailable = fullLogDataList.size();
        int currentLoaded = (currentPage + 1) * PAGE_SIZE;
        
        if (currentLoaded >= totalAvailable) {
            return; // No more data to load
        }
        
        isLoading = true;
        currentPage++;
        
        // Show loading indicator
        loadingMoreIndicator.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            try {
                List<LogData> newData = getPagedLogData(uid, currentPage, PAGE_SIZE);
                if (newData != null && !newData.isEmpty()) {
                    Collections.sort(newData, new DateComparator());
                    
                    runOnUiThread(() -> {
                        // Add new data to existing list
                        logDataList.addAll(newData);
                        recyclerViewAdapter.notifyItemRangeInserted(
                            logDataList.size() - newData.size(), 
                            newData.size()
                        );
                        loadingMoreIndicator.setVisibility(View.GONE);
                        isLoading = false;
                    });
                } else {
                    runOnUiThread(() -> {
                        loadingMoreIndicator.setVisibility(View.GONE);
                        isLoading = false;
                    });
                }
            } catch (Exception e) {
                Log.e(Api.TAG, "Error loading more data", e);
                runOnUiThread(() -> {
                    loadingMoreIndicator.setVisibility(View.GONE);
                    isLoading = false;
                });
            }
        }).start();
    }
    
    private String formatTimePeriod(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d";
        } else if (hours > 0) {
            return hours + "h";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
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
