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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
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
import dev.ukanth.ufirewall.log.LogRecyclerViewAdapter;
import dev.ukanth.ufirewall.util.DateComparator;
import dev.ukanth.ufirewall.util.G;

public class LogActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    protected static final int MENU_CLEARLOG = 7;

    RecyclerView recyclerView;
    LogRecyclerViewAdapter recyclerViewAdapter;
    private TextView emptyView;
    private SwipeRefreshLayout mSwipeLayout;


    //protected static final int MENU_TOGGLE_LOG = 27;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        mSwipeLayout.setOnRefreshListener(this);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        emptyView = (TextView) findViewById(R.id.empty_view);

        initializeRecyclerView();

        if(G.enableLogService()) {
            (new CollectLog()).setContext(this).execute();

        } else {
            recyclerView.setVisibility(View.GONE);
            mSwipeLayout.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void initializeRecyclerView() {
        recyclerView.hasFixedSize();
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        recyclerViewAdapter = new LogRecyclerViewAdapter(getApplicationContext());
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    private List<LogData> getLogData() {
        return SQLite.select()
                .from(LogData.class)
                //.orderBy(LogData_Table.timestamp, true)
                .queryList();
    }


    private class CollectLog extends AsyncTask<Void, Void, List<LogData>> {
        private Context context = null;
        MaterialDialog loadDialog = null;

        public CollectLog() {
        }
        //private boolean suAvailable = false;

        public CollectLog setContext(Context context) {
            this.context = context;
            return this;
        }

        @Override
        protected void onPreExecute() {
            loadDialog = new MaterialDialog.Builder(context).
                    cancelable(false).title(getString(R.string.loading_data_title)).progress(true, 0).content(context.getString(R.string.loading_data))
                    .show();
        }

        @Override
        protected List<LogData> doInBackground(Void... params) {
            List<LogData> logData = getLogData();
            try {
                if(logData != null && logData.size() > 0) {
                    logData = updateMap(logData);
                    Collections.sort(logData, new DateComparator());
                }
                return logData;
            } catch(Exception e) {
                Log.e(Api.TAG,"Exception while retrieving  data" + e.getLocalizedMessage());
                return null;
            }

        }

        @Override
        protected void onPostExecute(List<LogData> logData) {
            super.onPostExecute(logData);
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

            if (logData == null || logData.isEmpty()) {
                mSwipeLayout.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerViewAdapter.updateData(logData);
                recyclerView.setVisibility(View.VISIBLE);
                mSwipeLayout.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }

        }
    }



    private List<LogData> updateMap(List<LogData> logDataList) {
        HashMap<String, LogData> logMap = new HashMap<>();
        HashMap<String, Integer> count = new HashMap<>();
        HashMap<String, Long> lastBlocked = new HashMap<>();
        List<LogData> analyticsList = new ArrayList();
        LogData tmpData;
        for (LogData data : logDataList) {
            tmpData = data;
            if (logMap.containsKey(data.getUid())) {
                if (Long.parseLong(data.getTimestamp()) > lastBlocked.get(data.getUid())) {
                    lastBlocked.put(data.getUid(), Long.parseLong(data.getTimestamp()));
                    tmpData.setTimestamp(data.getTimestamp());
                } else {
                    tmpData.setTimestamp(lastBlocked.get(data.getUid()) + "");
                }
                //data already Present. Update the template here
                count.put(data.getUid(), count.get(data.getUid()).intValue() + 1);
                tmpData.setCount(count.get(data.getUid()).intValue());
                logMap.put(data.getUid(), tmpData);
            } else {
                //process template here
                count.put(data.getUid(), 1);
                tmpData.setCount(1);
                lastBlocked.put(data.getUid(), Long.parseLong(data.getTimestamp()));
                logMap.put(data.getUid(), tmpData);
            }
        }
        for (Map.Entry<String, LogData> entry : logMap.entrySet()) {
            analyticsList.add(entry.getValue());
        }
        return analyticsList;
    }


    protected void populateMenu(SubMenu sub) {
        sub.add(0, MENU_CLEARLOG, 0, R.string.clear_log).setIcon(
                R.drawable.clearlog);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Context ctx = this;

        switch (item.getItemId()) {

            case android.R.id.home: {
                onBackPressed();
                return true;
            }
            case MENU_CLEARLOG:
            /*Api.clearLog(ctx,
					new RootCommand().setReopenShell(true)
							.setSuccessToast(R.string.log_cleared)
							.setFailureToast(R.string.log_clear_error)
							.setCallback(new RootCommand.Callback() {
								public void cbFunc(RootCommand state) {
									populateData(ctx);
								}
							}));*/
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onRefresh() {
        (new CollectLog()).setContext(this).execute();
    }

	/*@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// setupLogMenuItem(menu, G.enableFirewallLog());
		return super.onPrepareOptionsMenu(menu);
	}*/


}
