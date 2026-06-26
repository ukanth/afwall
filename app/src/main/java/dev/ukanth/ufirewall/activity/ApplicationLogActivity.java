package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.os.Bundle;

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
    protected void populateData(final Context ctx) {
        result = new StringBuilder();
        updateLoadingState(getString(R.string.loading));
        writeHeading(result, false, "Logcat");
        result.append(Log.getApplicationLog());
        updateLoadingState(getString(R.string.ready));
        setData(result.toString());
    }
}
