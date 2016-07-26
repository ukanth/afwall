/**
 * Display/purge logs and toggle logging
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

package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.LogData;
import dev.ukanth.ufirewall.log.LogData_Table;
import dev.ukanth.ufirewall.log.LogRecyclerViewAdapter;
import dev.ukanth.ufirewall.service.NflogService;
import dev.ukanth.ufirewall.util.G;

public class LogActivity extends AppCompatActivity {

	protected static final int MENU_CLEARLOG = 7;

	RecyclerView recyclerView;
	LogRecyclerViewAdapter recyclerViewAdapter;

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
		List<LogData> logData = SQLite.select()
				.from(LogData.class)
				.orderBy(LogData_Table.timestamp,false)
				.queryList();

		Resources res = getResources();

		recyclerView = (RecyclerView)findViewById(R.id.recyclerview);


		recyclerViewAdapter = new LogRecyclerViewAdapter(this, updateMap(logData,res));
		recyclerView.hasFixedSize();
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.setAdapter(recyclerViewAdapter);


	}

	private List<LogData> updateMap(List<LogData> logDataList, Resources res) {
		HashMap<String,LogData> logMap = new HashMap<>();
		HashMap<String,Integer> count = new HashMap<>();
		List<LogData> analyticsList = new ArrayList();
		LogData tmpData;
		for(LogData data: logDataList) {
			tmpData = data;
			if(logMap.containsKey(data.getUid())) {
				//data already Present. Update the template here
				count.put(data.getUid(),count.get(data.getUid()).intValue() + 1);
				tmpData.setCount(count.get(data.getUid()).intValue());
				logMap.put(data.getUid(),tmpData);
			} else {
				//process template here
				count.put(data.getUid(),1);
				tmpData.setCount(1);
				logMap.put(data.getUid(),tmpData);
			}
		}
		for (Map.Entry<String, LogData> entry : logMap.entrySet())
		{
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
			if (G.logTarget().equals("NFLOG")) {
				NflogService.clearLog();
				//populateData(ctx);
				return true;
			}
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
		return super.onOptionsItemSelected( item);
	}

	/*@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// setupLogMenuItem(menu, G.enableFirewallLog());
		return super.onPrepareOptionsMenu(menu);
	}*/

	
}
