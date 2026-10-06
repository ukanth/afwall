/**
 * Custom scripts activity.
 * This screen is displayed to change the custom scripts.
 * <p>
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
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
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */
package dev.ukanth.ufirewall.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.CustomScript;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ThemeHelper;

/**
 * Custom scripts activity.
 * This screen is displayed to change the custom scripts.
 */
public class CustomScriptActivity extends AppCompatActivity implements OnClickListener {
    private static final int MENU_SNIPPETS = 1;
    // how long typing has to pause before the lines are checked
    private static final long CHECK_DELAY_MS = 400;
    private static final int PROBLEM_LINE_COLOR = 0x55C62828;
    // problems listed under a script box; the rest are counted
    private static final int MAX_LISTED_PROBLEMS = 3;

    /**
     * Ready-made lines for things only a script can do. Each: title, startup lines, shutdown
     * lines (null: none). Addresses and ports are examples to edit.
     */
    private static final Object[][] SNIPPETS = {
            {R.string.snippet_inbound,
                    "# Block incoming connections to port 22 (SSH)\n"
                            + "$IPTABLES -A INPUT -p tcp --dport 22 -j DROP", null},
            {R.string.snippet_dns,
                    "# Send all DNS lookups to 192.168.1.2 (allow that address too)\n"
                            + "iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination 192.168.1.2:53\n"
                            + "iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination 192.168.1.2:53", null},
            {R.string.snippet_chain,
                    "# My own chain: -F empties it, so removed lines don't stay behind\n"
                            + "$IPTABLES -N my-rules\n"
                            + "$IPTABLES -F my-rules\n"
                            + "$IPTABLES -A my-rules -p tcp --dport 25 -j REJECT\n"
                            + "$IPTABLES -A \"$AFWALL_CHAIN\" -j my-rules", null},
            {R.string.snippet_keep_block,
                    "# Remove the block the shutdown script adds\n"
                            + "iptables -D OUTPUT -d 203.0.113.50 -j REJECT",
                    "# Keep 203.0.113.50 blocked while the firewall is off\n"
                            + "iptables -A OUTPUT -d 203.0.113.50 -j REJECT"},
    };

    private TextInputEditText script;
    private TextInputEditText script2;
    private final Handler checkHandler = new Handler(Looper.getMainLooper());

    /** Marks a line that won't be run. */
    private static final class ProblemSpan extends BackgroundColorSpan {
        ProblemSpan() {
            super(PROBLEM_LINE_COLOR);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initTheme();
        setContentView(R.layout.customscript);

        findViewById(R.id.customscript_ok).setOnClickListener(this);
        findViewById(R.id.customscript_cancel).setOnClickListener(this);
        ((TextView) findViewById(R.id.customscript_link)).setMovementMethod(LinkMovementMethod.getInstance());

        final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
        this.script = findViewById(R.id.customscript);
        this.script.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT, ""));
        this.script2 = findViewById(R.id.customscript2);
        this.script2.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT2, ""));

        setTitle(R.string.set_custom_script);
        watch(script, findViewById(R.id.customscript_problems));
        watch(script2, findViewById(R.id.customscript2_problems));

        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }


    /** Check the lines of {@code field} shortly after each change, and once now. */
    private void watch(TextInputEditText field, TextView problems) {
        final Runnable check = () -> check(field, problems);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                checkHandler.removeCallbacks(check);
                checkHandler.postDelayed(check, CHECK_DELAY_MS);
            }
        });
        checkHandler.post(check);
    }

    /** Highlight the lines that won't be run, and say why under the box. */
    private void check(TextInputEditText field, TextView problems) {
        Editable text = field.getText();
        if (text == null) {
            return;
        }
        for (ProblemSpan span : text.getSpans(0, text.length(), ProblemSpan.class)) {
            text.removeSpan(span);
        }
        // the chain name only matters for the commands, not for the check
        CustomScript.Result result = CustomScript.parse(text.toString(), "afwall");
        if (result.rejected.isEmpty()) {
            problems.setVisibility(View.GONE);
            return;
        }
        String[] lines = text.toString().split("\n", -1);
        int[] lineStarts = new int[lines.length];
        for (int i = 1; i < lines.length; i++) {
            lineStarts[i] = lineStarts[i - 1] + lines[i - 1].length() + 1;
        }
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < result.rejected.size(); i++) {
            CustomScript.Rejected r = result.rejected.get(i);
            int index = r.lineNumber - 1;
            if (index < lines.length && !lines[index].isEmpty()) {
                text.setSpan(new ProblemSpan(), lineStarts[index], lineStarts[index] + lines[index].length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < MAX_LISTED_PROBLEMS) {
                if (message.length() > 0) {
                    message.append('\n');
                }
                message.append(Api.describeCustomScriptProblem(this, r));
            }
        }
        if (result.rejected.size() > MAX_LISTED_PROBLEMS) {
            message.append('\n').append(getResources().getQuantityString(R.plurals.custom_script_more_problems,
                    result.rejected.size() - MAX_LISTED_PROBLEMS, result.rejected.size() - MAX_LISTED_PROBLEMS));
        }
        problems.setText(message);
        problems.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_SNIPPETS, Menu.NONE, R.string.custom_script_snippets)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return super.onCreateOptionsMenu(menu);
    }

    private void showSnippets() {
        String[] titles = new String[SNIPPETS.length + 1];
        titles[0] = getString(R.string.snippet_global_rules);
        for (int i = 0; i < SNIPPETS.length; i++) {
            titles[i + 1] = getString((Integer) SNIPPETS[i][0]);
        }
        new MaterialDialog.Builder(this)
                .title(R.string.custom_script_snippets)
                .items((CharSequence[]) titles)
                .itemsCallback((dialog, view, which, text) -> {
                    if (which == 0) {
                        startActivity(AppRulesActivity.globalRulesIntent(this));
                        return;
                    }
                    Object[] snippet = SNIPPETS[which - 1];
                    insertLines(script, (String) snippet[1]);
                    if (snippet[2] != null) {
                        insertLines(script2, (String) snippet[2]);
                    }
                })
                .show();
    }

    /** Insert whole lines at the cursor (at the end if the box doesn't have the cursor). */
    private void insertLines(TextInputEditText field, String lines) {
        Editable text = field.getText();
        if (text == null) {
            return;
        }
        int at = field.hasFocus() ? Math.max(field.getSelectionStart(), 0) : text.length();
        // start and end on a line of their own
        int lineStart = at;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        boolean emptyLine = lineStart == at && (at == text.length() || text.charAt(at) == '\n');
        String insert = lines;
        if (!emptyLine) {
            // move to the end of the current line
            while (at < text.length() && text.charAt(at) != '\n') {
                at++;
            }
            insert = "\n" + insert;
        }
        boolean linesAfter = at < text.length();
        if (linesAfter) {
            insert = insert + "\n";
        }
        text.insert(at, insert);
        field.requestFocus();
        // cursor at the end of the inserted lines
        field.setSelection(at + insert.length() - (linesAfter ? 1 : 0));
    }

    private void initTheme() {
        ThemeHelper.applyTheme(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case MENU_SNIPPETS:
                showSnippets();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Save, after showing the lines that won't be run (if any) with the choice to fix them first.
     */
    private void checkAndSave() {
        StringBuilder problems = new StringBuilder();
        appendProblems(problems, R.string.custom_script_check_startup, script.getText().toString());
        appendProblems(problems, R.string.custom_script_check_shutdown, script2.getText().toString());
        if (problems.length() == 0) {
            resultOk();
            return;
        }
        new MaterialDialog.Builder(this)
                .title(R.string.custom_script_check_title)
                .content(problems.toString().trim())
                .positiveText(R.string.custom_script_keep_editing)
                .negativeText(R.string.custom_script_save_anyway)
                .onNegative((dialog, which) -> resultOk())
                .show();
    }

    private void appendProblems(StringBuilder out, int titleRes, String text) {
        // the chain name only matters for the commands, not for the check
        CustomScript.Result result = CustomScript.parse(text, "afwall");
        if (result.rejected.isEmpty()) {
            return;
        }
        out.append(getString(titleRes)).append('\n');
        for (CustomScript.Rejected r : result.rejected) {
            out.append(Api.describeCustomScriptProblem(this, r)).append("\n\n");
        }
    }

    /**
     * Set the activity result to RESULT_OK and terminate this activity.
     */
    private void resultOk() {
        if (getCallingActivity() == null) {
            // not opened by the main screen (e.g. from the global rules), which would save it
            saveScripts();
        }
        final Intent response = new Intent(Api.CUSTOM_SCRIPT_MSG);
        response.putExtra(Api.SCRIPT_EXTRA, script.getText().toString());
        response.putExtra(Api.SCRIPT2_EXTRA, script2.getText().toString());
        setResult(RESULT_OK, response);
        finish();
    }

    private void saveScripts() {
        String s1 = script.getText().toString().trim().replace("\r\n", "\n");
        String s2 = script2.getText().toString().trim().replace("\r\n", "\n");
        boolean saved = getSharedPreferences(Api.PREFS_NAME, 0).edit()
                .putString(Api.PREF_CUSTOMSCRIPT, s1)
                .putString(Api.PREF_CUSTOMSCRIPT2, s2)
                .commit();
        int msg = !saved ? R.string.custom_script_error
                : (s1.isEmpty() && s2.isEmpty() ? R.string.custom_script_removed : R.string.custom_script_defined);
        Api.toast(this, getString(msg));
        MainActivity.requireFullApply();
        Api.setRulesUpToDate(false);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.customscript_ok) {
            checkAndSave();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
        if (script.getText().toString().equals(prefs.getString(Api.PREF_CUSTOMSCRIPT, ""))
                && script2.getText().toString().equals(prefs.getString(Api.PREF_CUSTOMSCRIPT2, ""))) {
            // Nothing has been changed, just return
            super.onBackPressed();
            return;
        }
        new MaterialDialog.Builder(this)
                .title(R.string.unsaved_changes)
                .content(R.string.unsaved_changes_message)
                .positiveText(R.string.apply)
                .negativeText(R.string.discard)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        checkAndSave();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        findViewById(R.id.customscript_cancel).performClick();
                    }
                })
                .show();
    }
}
