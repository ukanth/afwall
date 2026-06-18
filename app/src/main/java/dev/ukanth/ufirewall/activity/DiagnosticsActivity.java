package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.SubMenu;

import dev.ukanth.ufirewall.R;

public class DiagnosticsActivity extends RulesActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.log_hub_diagnostics));
    }

    @Override
    protected void populateMenu(SubMenu sub) {
        sub.add(0, MENU_SEND_REPORT, 0, R.string.send_report).setIcon(R.drawable.ic_mail);
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
