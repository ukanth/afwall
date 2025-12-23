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
import android.annotation.SuppressLint;
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
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

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
    View mScrollView;  // Can be either ScrollView or NestedScrollView
    
    // Modern layout components
    private TextView rulesTitle;
    private TextView rulesStatus;
    private TextView rulesContent;
    private TextView interfacesContent;
    private TextView systemContent;
    private TextView preferencesContent;
    private TextView logcatContent;
    private CardView interfacesCard;
    private CardView systemCard;
    private CardView preferencesCard;
    private CardView logcatCard;
    private boolean useModernLayout = true;

    protected Menu mainMenu;
    protected static String dataText;

    // to be filled in by subclasses
    protected static String sdDumpFile =  "iptables.log";

    protected abstract void populateMenu(SubMenu sub);

    protected abstract void populateData(final Context ctx);

    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 1;

    private void initModernViews() {
        rulesTitle = findViewById(R.id.rules_title);
        rulesStatus = findViewById(R.id.rules_status);
        rulesContent = findViewById(R.id.rules_content);
        interfacesContent = findViewById(R.id.interfaces_content);
        systemContent = findViewById(R.id.system_content);
        preferencesContent = findViewById(R.id.preferences_content);
        logcatContent = findViewById(R.id.logcat_content);
        interfacesCard = findViewById(R.id.interfaces_card);
        systemCard = findViewById(R.id.system_card);
        preferencesCard = findViewById(R.id.preferences_card);
        logcatCard = findViewById(R.id.logcat_card);
    }

    protected void setData(final String data) {
        dataText = data;
        Handler refresh = new Handler(Looper.getMainLooper());
        refresh.post(() -> {
            if (useModernLayout) {
                parseAndDisplayModernData(data);
            } else {
                scaleGesture = findViewById(R.id.rules);
                scaleGesture.setText(data);
                scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, G.ruleTextSize());
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void parseAndDisplayModernData(String data) {
        // Initialize all sections as empty and hide cards
        rulesContent.setText("");
        interfacesContent.setText("");
        systemContent.setText("");
        preferencesContent.setText("");
        logcatContent.setText("");
        
        interfacesCard.setVisibility(View.GONE);
        systemCard.setVisibility(View.GONE);
        preferencesCard.setVisibility(View.GONE);
        logcatCard.setVisibility(View.GONE);

        // Parse sections more intelligently by looking for section headers
        String[] lines = data.split("\n");
        StringBuilder currentSection = new StringBuilder();
        String currentSectionType = null;
        
        for (String line : lines) {
            // Check if this is a section header (starts with =, contains title, ends with =)
            if (line.matches("^=+$")) {
                // Skip separator lines
                continue;
            }
            
            // Check if this line is a section title
            String sectionType = detectSectionType(line.trim());
            
            if (sectionType != null) {
                // Process previous section if it exists
                if (currentSectionType != null && currentSection.length() > 0) {
                    processSectionContent(currentSectionType, currentSection.toString());
                }
                
                // Start new section
                currentSectionType = sectionType;
                currentSection = new StringBuilder();
                continue;
            }
            
            // Add line to current section
            if (currentSectionType != null) {
                currentSection.append(line).append("\n");
            }
        }
        
        // Process the last section
        if (currentSectionType != null && currentSection.length() > 0) {
            processSectionContent(currentSectionType, currentSection.toString());
        }
        
        // Set font sizes for all content views
        float textSize = G.ruleTextSize();
        rulesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        interfacesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        systemContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        preferencesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        logcatContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

        // Keep the hidden TextView updated for backward compatibility (export, copy functions)
        TextView hiddenRules = findViewById(R.id.rules);
        hiddenRules.setText(data);
    }
    
    private String detectSectionType(String line) {
        String trimmed = line.trim();
        if (trimmed.equals(getString(R.string.ipv4_rules_title))) {
            return "ipv4_rules";
        } else if (trimmed.equals(getString(R.string.ipv6_rules_title))) {
            return "ipv6_rules";
        } else if (trimmed.contains("Network interfaces")) {
            return "interfaces";
        } else if (trimmed.contains("ifconfig")) {
            return "ifconfig";
        } else if (trimmed.contains("System info")) {
            return "system";
        } else if (trimmed.contains("Preferences")) {
            return "preferences";
        } else if (trimmed.contains("Logcat")) {
            return "logcat";
        }
        return null;
    }
    
    private void processSectionContent(String sectionType, String content) {
        String trimmedContent = content.trim();
        if (trimmedContent.isEmpty()) return;
        
        switch (sectionType) {
            case "ipv4_rules":
                rulesTitle.setText(getString(R.string.ipv4_rules_title));
                rulesStatus.setText(getString(R.string.ready));
                rulesContent.setText(trimmedContent);
                break;
                
            case "ipv6_rules":
                rulesTitle.setText(getString(R.string.ipv6_rules_title));
                rulesStatus.setText(getString(R.string.ready));
                rulesContent.setText(trimmedContent);
                break;
                
            case "interfaces":
            case "ifconfig":
                interfacesCard.setVisibility(View.VISIBLE);
                String existingInterfaces = interfacesContent.getText().toString();
                if (!existingInterfaces.isEmpty()) {
                    interfacesContent.setText(existingInterfaces + "\n\n" + trimmedContent);
                } else {
                    interfacesContent.setText(trimmedContent);
                }
                break;
                
            case "system":
                systemCard.setVisibility(View.VISIBLE);
                systemContent.setText(trimmedContent);
                break;
                
            case "preferences":
                preferencesCard.setVisibility(View.VISIBLE);
                String existingPrefs = preferencesContent.getText().toString();
                if (!existingPrefs.isEmpty()) {
                    preferencesContent.setText(existingPrefs + "\n\n" + trimmedContent);
                } else {
                    preferencesContent.setText(trimmedContent);
                }
                break;
                
            case "logcat":
                logcatCard.setVisibility(View.VISIBLE);
                logcatContent.setText(trimmedContent);
                break;
        }
    }

    private void updateModernTextSize(float sizeDelta) {
        float currentSize = G.ruleTextSize();
        float newSize = currentSize + sizeDelta;
        
        if (newSize < 8.0f) newSize = 8.0f;  // Minimum size
        if (newSize > 30.0f) newSize = 30.0f; // Maximum size
        
        G.ruleTextSize((int) newSize);
        
        if (rulesContent != null) rulesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
        if (interfacesContent != null) interfacesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
        if (systemContent != null) systemContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
        if (preferencesContent != null) preferencesContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
        if (logcatContent != null) logcatContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
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
        if (useModernLayout) {
            setContentView(R.layout.rules_modern);
            initModernViews();
        } else {
            setContentView(R.layout.rules);
        }

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
                if (useModernLayout) {
                    updateModernTextSize(2.0f);
                } else {
                    newSize = scaleGesture.getTextSize() + 2.0f;
                    scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
                    G.ruleTextSize((int) newSize);
                }
                return false;
            }
            case MENU_ZOOM_OUT -> {
                if (useModernLayout) {
                    updateModernTextSize(-2.0f);
                } else {
                    newSize = scaleGesture.getTextSize() - 2.0f;
                    scaleGesture.setTextSize(TypedValue.COMPLEX_UNIT_PX, newSize);
                    G.ruleTextSize((int) newSize);
                }
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
            executor.shutdown();
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
