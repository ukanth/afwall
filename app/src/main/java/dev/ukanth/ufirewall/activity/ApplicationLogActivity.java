package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.SubMenu;

import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;

public class ApplicationLogActivity extends RulesActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.application_log_title));
        sdDumpFile = "application-log.log";
    }

    @Override
    protected void populateMenu(SubMenu sub) {
        sub.add(0, MENU_SEND_REPORT, 0, R.string.send_report).setIcon(R.drawable.ic_mail);
    }

    @Override
    protected void populateData(final Context ctx) {
        result = new StringBuilder();
        updateLoadingState(getString(R.string.loading));
        writeHeading(result, false, "Logcat");
        result.append(Log.getLog());
        updateLoadingState(getString(R.string.ready));
        setData(result.toString());
    }
}
