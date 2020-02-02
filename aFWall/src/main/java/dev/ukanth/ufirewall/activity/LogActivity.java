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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.log.LogDatabase;
import dev.ukanth.ufirewall.log.LogRecyclerViewAdapter;
import dev.ukanth.ufirewall.log.RecyclerItemClickListener;
import dev.ukanth.ufirewall.util.DateComparator;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;

public class LogActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private RecyclerView recyclerView;
    private LogRecyclerViewAdapter recyclerViewAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout mSwipeLayout;
    protected Menu mainMenu;

    protected  static final int MENU_TOGGLE = -4;
    protected static final int MENU_CLEAR = 40;
    protected static final int MENU_SWITCH_OLD = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setContentView(R.layout.log_view);
        Toolbar toolbar = (Toolbar) findViewById(R.id.rule_toolbar);
        setTitle(getString(R.string.showlog_title));
        //toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setSupportActionBar(toolbar);

        // Load partially transparent black background
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            Object data = bundle.get("validate");
            if(data != null){
                String check = (String) data;
                if(check.equals("yes")) {
                    new SecurityUtil(LogActivity.this).passCheck();
                }
            }
        }

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        mSwipeLayout.setOnRefreshListener(this);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        emptyView = (TextView) findViewById(R.id.empty_view);

        initializeRecyclerView(getApplicationContext());

        if(G.enableLogService()) {
            (new CollectLog()).setContext(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            recyclerView.setVisibility(View.GONE);
            mSwipeLayout.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
    }

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
    private void initializeRecyclerView(final Context ctx) {
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        recyclerViewAdapter = new LogRecyclerViewAdapter(getApplicationContext(), logData -> {
            if(G.isDoKey(ctx) || G.isDonate()) {
                Intent intent = new Intent(ctx, LogDetailActivity.class);
                intent.putExtra("DATA",logData.getUid());
                startActivity(intent);
            } else {
                Api.donateDialog(LogActivity.this,false);
            }
        });
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    private List<LogData> getLogData() {
        //load 3 day data
        long loadInterval = System.currentTimeMillis() - 259200000;

        List<LogData> logData = SQLite.select()
                .from(LogData.class)
                .where(LogData_Table.timestamp.greaterThan(loadInterval))
                .orderBy(LogData_Table.timestamp,true)
                .queryList();
        //auto purge old data - > week old data
        Api.purgeOldLog();
        return logData;
    }

    private int getCount() {
        long l = SQLite.selectCountOf().from(LogData.class).count();
        return (int) l;
    }


    private class CollectLog extends AsyncTask<Void, Integer, Boolean> {
        private Context context = null;
        MaterialDialog loadDialog = null;

        public CollectLog() {
        }

        public CollectLog setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            loadDialog = new MaterialDialog.Builder(context).cancelable(false)
                    .title(getString(R.string.working))
                    .cancelable(false)
                    .content(getString(R.string.loading_data))
                    .progress(true, 0).show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            List<LogData> logData = getLogData();
            try {
                if(logData != null && logData.size() > 0) {
                    logData = updateMap(logData,this);
                    Collections.sort(logData, new DateComparator());
                    recyclerViewAdapter.updateData(logData);
                    return true;
                } else {
                    return false;
                }
            } catch(Exception e) {
                Log.e(Api.TAG,"Exception while retrieving  data" + e.getLocalizedMessage());
                return null;
            }
        }

        /*@Override
        protected void onProgressUpdate(Integer... progress) {

            if (progress[0] == 0 ||  progress[0] == -1) {
                //do nothing
            } else {
                loadDialog.incrementProgress(progress[0]);
            }
        }*/

        @Override
        protected void onPostExecute(Boolean logPresent) {
            super.onPostExecute(logPresent);
            //doProgress(-1);
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
                recyclerViewAdapter.notifyDataSetChanged();
                recyclerView.setVisibility(View.VISIBLE);
                mSwipeLayout.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            } else {
                mSwipeLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }
            Log.i(Api.TAG,"Ended Loading: " + System.currentTimeMillis());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Common options: Copy, Export to SD Card, Refresh
        SubMenu sub = menu.addSubMenu(0, MENU_TOGGLE, 0, "").setIcon(R.drawable.ic_flow);
        sub.add(0, MENU_CLEAR, 0, R.string.clear_log).setIcon(R.drawable.ic_clearlog);
        sub.add(0, MENU_SWITCH_OLD, 0, R.string.switch_old).setIcon(R.drawable.ic_log);
        //populateMenu(sub);
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS| MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        super.onCreateOptionsMenu(menu);
        mainMenu = menu;
        return true;
    }


    static <T> List<List<T>> split(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<List<T>>();
        final int N = list.size();
        for (int i = 0; i < N; i += L) {
            parts.add(new ArrayList<T>(
                    list.subList(i, Math.min(N, i + L)))
            );
        }
        return parts;
    }


    private List<LogData> updateMap(final List<LogData> logDataList, CollectLog collectLog) {
        final HashMap<Integer, LogData> logMap = new HashMap<>();
        final HashMap<Integer, Integer> count = new HashMap<>();
        final HashMap<Integer, Long> lastBlocked = new HashMap<>();
        List<LogData> analyticsList = new ArrayList();

        //int counter = 0;
        final int size = logDataList.size();
        //List<List<LogData>> parts = split(logDataList, 10);
        /*for(List listLog: parts) {
            Thread t = new Thread() {*/
                LogData tmpData,data;
               // public void run() {
                    for (int i=0; i<size; i++) {
                        //collectLog.doProgress(counter++);
                        tmpData = logDataList.get(i);
                        data = logDataList.get(i);
                        if (logMap.containsKey(data.getUid())) {
                            if (data.getTimestamp() > lastBlocked.get(data.getUid())) {
                                lastBlocked.put(data.getUid(), data.getTimestamp());
                                tmpData.setTimestamp(data.getTimestamp());
                            } else {
                                tmpData.setTimestamp(lastBlocked.get(data.getUid()));
                            }
                            //data already Present. Update the template here
                            count.put(data.getUid(), count.get(data.getUid()).intValue() + 1);
                            tmpData.setCount(count.get(data.getUid()).intValue());
                            logMap.put(data.getUid(), tmpData);
                        } else {
                            //process template here
                            count.put(data.getUid(), 1);
                            tmpData.setCount(1);
                            lastBlocked.put(data.getUid(), data.getTimestamp());
                            logMap.put(data.getUid(), tmpData);
                        }
                    }
                //}
            //};
            //t.start();
        //}

        for (Map.Entry<Integer, LogData> entry : logMap.entrySet()) {
            analyticsList.add(entry.getValue());
        }
        return analyticsList;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case MENU_CLEAR:
                clearDatabase(LogActivity.this);
                return true;
            /*case MENU_EXPORT_LOG:
                //exportToSD();
                return true;*/
            case MENU_SWITCH_OLD:
                Intent i = new Intent(this, OldLogActivity.class);
                G.oldLogView(true);
                startActivity(i);
                finish();
                return true;
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
                        Toast.makeText(ctx, ctx.getString(R.string.log_cleared), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        (new CollectLog()).setContext(LogActivity.this).execute();
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
        (new CollectLog()).setContext(this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

	/*@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// setupLogMenuItem(menu, G.enableFirewallLog());
		return super.onPrepareOptionsMenu(menu);
	}*/


}
