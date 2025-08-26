/**
 * Common framework for LogActivity and RulesActivity
 * <p>
 * Copyright (C) 2011-2012  Umakanthan Chandran
 * Copyright (C) 2011-2013  Kevin Cernekee
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.util.G;


public abstract class DataDumpActivity extends AppCompatActivity {

    public static final String TAG = "AFWall";

    protected static final int MENU_TOGGLE = -3;
    protected static final int MENU_COPY = 16;
    protected static final int MENU_EXPORT_LOG = 17;
    protected static final int MENU_REFRESH = 13;

    protected static final int MENU_ZOOM_IN = 22;
    protected static final int MENU_ZOOM_OUT = 23;
    TextView scaleGesture;
    ScrollView mScrollView;

    protected Menu mainMenu;
    protected static String dataText;

    // to be filled in by subclasses
    protected static String sdDumpFile =  "iptables.log";

    protected abstract void populateMenu(SubMenu sub);

    protected abstract void populateData(final Context ctx);

    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;

    protected void setData(final String data) {
        dataText = data;
        Handler refresh = new Handler(Looper.getMainLooper());
        refresh.post(() -> {
            scaleGesture = findViewById(R.id.rules);
            scaleGesture.setText(data);
            scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, G.ruleTextSize());
        });
    }

    private void initTheme() {
        switch (G.getSelectedTheme()) {
            case "D" -> setTheme(R.style.AppDarkTheme);
            case "L" -> setTheme(R.style.AppLightTheme);
            case "B" -> setTheme(R.style.AppBlackTheme);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);

        initTheme();
        setContentView(R.layout.rules);

        Toolbar toolbar = findViewById(R.id.rule_toolbar);
        //toolbar.setTitle(getString(R.string.showrules_title));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setSupportActionBar(toolbar);

        mScrollView = findViewById(R.id.ruleScrollView);

        // Load partially transparent black background
        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setData("");
        populateData(this);

        Api.updateLanguage(getApplicationContext(), G.locale());
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        // Common options: Copy, Export to SD Card, Refresh
        SubMenu sub = menu.addSubMenu(0, MENU_TOGGLE, 0, "").setIcon(R.drawable.ic_flow);
        sub.add(0, MENU_ZOOM_IN, 0, getString(R.string.label_zoomin)).setIcon(R.drawable.zoomin).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        sub.add(0, MENU_ZOOM_OUT, 0, getString(R.string.label_zoomout)).setIcon(R.drawable.zoomout).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        sub.add(0, MENU_COPY, 0, R.string.copy).setIcon(R.drawable.ic_copy);
        sub.add(0, MENU_EXPORT_LOG, 0, R.string.export_to_sd).setIcon(R.drawable.ic_export);
        sub.add(0, MENU_REFRESH, 0, R.string.refresh).setIcon(R.drawable.ic_refresh);

        populateMenu(sub);

        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);


        super.onCreateOptionsMenu(menu);
        mainMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        float newSize;
        switch (item.getItemId()) {
            case MENU_COPY -> {
                copy();
                return true;
            }
            case MENU_EXPORT_LOG -> {
                exportToSD();
                return true;
            }
            case MENU_REFRESH -> {
                populateData(this);
                return true;
            }
            case MENU_ZOOM_IN -> {
                newSize = scaleGesture.getTextSize() + 2.0f;
                scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
                G.ruleTextSize((int) newSize);
                return false;
            }
            case MENU_ZOOM_OUT -> {
                newSize = scaleGesture.getTextSize() - 2.0f;
                scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
                G.ruleTextSize((int) newSize);
                return false;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private static class Task implements Runnable {
        public String filename = "";
        private final Context ctx;
        private final WeakReference<DataDumpActivity> activityReference;
        private final Handler handler = new Handler(Looper.getMainLooper());

        // only retain a weak reference to the activity
        Task(DataDumpActivity context) {
            this.ctx = context;
            activityReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            boolean res = false;

            try {
                File file;
                if(Build.VERSION.SDK_INT  < Build.VERSION_CODES.Q ){
                    File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" );
                    dir.mkdirs();
                    file = new File(dir, sdDumpFile);
                } else{
                    file = new File(ctx.getExternalFilesDir(null) + "/" + sdDumpFile) ;
                }
                output = new FileOutputStream(file);
                output.write(dataText.getBytes());
                filename = file.getAbsolutePath();
                res = true;
            } catch (IOException e) {
                Log.e(TAG,e.getMessage(),e);
            } finally {
                try {
                    if (output != null) {
                        output.flush();
                        output.close();
                    }
                } catch (IOException ex) {
                    Log.e(TAG,ex.getMessage(),ex);
                }
            }

            final boolean result = res;
            handler.post(() -> {
                DataDumpActivity activity = activityReference.get();
                if (activity == null || activity.isFinishing()) return;

                if (result) {
                    Api.toast(ctx, ctx.getString(R.string.export_rules_success) + filename, Toast.LENGTH_LONG);
                } else {
                    Api.toast(ctx, ctx.getString(R.string.export_logs_fail), Toast.LENGTH_LONG);
                }
            });
        }
    }

    private void exportToSD() {

        if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.Q ){
            // Do some stuff
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(new Task(this));
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                // permissions have not been granted.
                ActivityCompat.requestPermissions(DataDumpActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
            } else{
                new Task(this).run();
            }
        }
    }

    private void copy() {
        try {
            TextView rulesText = findViewById(R.id.rules);
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData
                    .newPlainText("", rulesText.getText().toString());
            clipboard.setPrimaryClip(clip);
            Api.toast(this, this.getString(R.string.copied));
        } catch (Exception e) {
            Log.d("AFWall+", "Exception in Clipboard" + e);
        }
        Api.toast(this, this.getString(R.string.copied));
    }
}
