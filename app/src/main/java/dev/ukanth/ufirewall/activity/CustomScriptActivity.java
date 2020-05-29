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
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.util.G;

/**
 * Custom scripts activity.
 * This screen is displayed to change the custom scripts.
 */
public class CustomScriptActivity extends AppCompatActivity implements OnClickListener {
    private EditText script;
    private EditText script2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initTheme();
        final View view = getLayoutInflater().inflate(R.layout.customscript, null);

        view.findViewById(R.id.customscript_ok).setOnClickListener(this);
        view.findViewById(R.id.customscript_cancel).setOnClickListener(this);
        ((TextView) view.findViewById(R.id.customscript_link)).setMovementMethod(LinkMovementMethod.getInstance());

        final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
        this.script = view.findViewById(R.id.customscript);
        this.script.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT, ""));
        this.script2 = view.findViewById(R.id.customscript2);
        this.script2.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT2, ""));

        setTitle(R.string.set_custom_script);
        setContentView(view);

        Toolbar toolbar = findViewById(R.id.custom_toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Set the activity result to RESULT_OK and terminate this activity.
     */
    private void resultOk() {
        final Intent response = new Intent(Api.CUSTOM_SCRIPT_MSG);
        response.putExtra(Api.SCRIPT_EXTRA, script.getText().toString());
        response.putExtra(Api.SCRIPT2_EXTRA, script2.getText().toString());
        setResult(RESULT_OK, response);
        finish();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.customscript_ok) {
            resultOk();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        // Handle the back button when dirty
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
            if (script.getText().toString().equals(prefs.getString(Api.PREF_CUSTOMSCRIPT, ""))
                    && script2.getText().toString().equals(prefs.getString(Api.PREF_CUSTOMSCRIPT2, ""))) {
                // Nothing has been changed, just return
                return super.onKeyDown(keyCode, event);
            }
            new MaterialDialog.Builder(this)
                    .title(R.string.unsaved_changes)
                    .content(R.string.unsaved_changes_message)
                    .positiveText(R.string.apply)
                    .negativeText(R.string.discard)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            resultOk();
                        }
                    })

                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            onBackPressed();
                            findViewById(R.id.customscript_cancel).performClick();
                        }
                    })
                    .show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
