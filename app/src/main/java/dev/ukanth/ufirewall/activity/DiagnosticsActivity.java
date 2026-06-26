package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.os.Bundle;

import dev.ukanth.ufirewall.R;

public class DiagnosticsActivity extends RulesActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.log_hub_diagnostics));
    }

    @Override
    protected void populateData(final Context ctx) {
        result = new StringBuilder();
        updateLoadingState(getString(R.string.loading_network_info));
        appendNetworkInterfaces(ctx);
    }

    @Override
    protected boolean includeApplicationLog() {
        return false;
    }
}
