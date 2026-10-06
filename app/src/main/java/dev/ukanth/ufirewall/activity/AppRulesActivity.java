package dev.ukanth.ufirewall.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.customrules.CustomRule;
import dev.ukanth.ufirewall.customrules.CustomRule_Table;
import dev.ukanth.ufirewall.util.AppRuleHelper;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.ThemeHelper;

/**
 * Direct rules of one app, or the global rules ("any app"): allow or block a destination
 * address / protocol / port, on all networks or only one.
 */
public class AppRulesActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_LABEL = "label";

    private int uid;
    private String packageName;
    private String label;
    private RadioGroup action;
    private EditText destination;
    private TextView family;
    private EditText port;
    private Spinner protocol;
    private Spinner network;
    private final List<String> networkValues = new ArrayList<>();
    private LinearLayout rulesList;
    private TextView emptyView;
    private TextView orderHint;

    /** The global rules: the direct rules of "any app". */
    public static Intent globalRulesIntent(Context ctx) {
        Intent intent = new Intent(ctx, AppRulesActivity.class);
        intent.putExtra(EXTRA_UID, Api.SPECIAL_UID_ANY);
        // reuse an open global rules screen (it links to the script editor, which links here)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.app_rules);

        uid = getIntent().getIntExtra(EXTRA_UID, Api.SPECIAL_UID_ANY);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        boolean global = uid == Api.SPECIAL_UID_ANY;
        setTitle(global ? R.string.global_rules : R.string.direct_rules_title);

        Toolbar toolbar = findViewById(R.id.app_rules_toolbar);
        setSupportActionBar(toolbar);
        ThemeHelper.apply(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (!AppRuleHelper.supportsUid(uid)) {
            // kernel, tethering, NTP, ...: no UID iptables can match
            Toast.makeText(this, R.string.direct_rules_not_supported, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        TextView appTitle = findViewById(R.id.app_rules_app);
        if (global) {
            appTitle.setText(R.string.global_rules_subtitle);
        } else {
            if (TextUtils.isEmpty(label)) {
                label = packageName != null ? packageName : String.valueOf(uid);
            }
            appTitle.setText(label + " [" + uid + "]");
        }
        findViewById(R.id.direct_rules_disabled_warning)
                .setVisibility(G.enableCustomRules() ? View.GONE : View.VISIBLE);

        action = findViewById(R.id.direct_rule_action);
        destination = findViewById(R.id.direct_rule_destination);
        family = findViewById(R.id.direct_rule_family);
        port = findViewById(R.id.direct_rule_port);
        port.setKeyListener(DigitsKeyListener.getInstance("0123456789:"));
        protocol = findViewById(R.id.direct_rule_protocol);
        network = findViewById(R.id.direct_rule_network);
        rulesList = findViewById(R.id.direct_rules_list);
        emptyView = findViewById(R.id.direct_rules_empty);
        emptyView.setText(global ? R.string.global_rules_empty : R.string.direct_rules_empty);
        orderHint = findViewById(R.id.direct_rules_order_hint);
        Button add = findViewById(R.id.direct_rule_add);
        Button portSeparator = findViewById(R.id.direct_rule_port_separator);

        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Any", "TCP", "UDP"});
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocol.setAdapter(protocolAdapter);

        // VPN rules only do something with VPN control on
        List<String> networkLabels = new ArrayList<>();
        for (String value : G.enableVPN()
                ? new String[]{AppRuleHelper.NETWORK_ALL, AppRuleHelper.NETWORK_WIFI, AppRuleHelper.NETWORK_MOBILE, AppRuleHelper.NETWORK_VPN}
                : new String[]{AppRuleHelper.NETWORK_ALL, AppRuleHelper.NETWORK_WIFI, AppRuleHelper.NETWORK_MOBILE}) {
            networkValues.add(value);
            networkLabels.add(networkLabel(value));
        }
        ArrayAdapter<String> networkAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, networkLabels);
        networkAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        network.setAdapter(networkAdapter);

        destination.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showFamily(s.toString().trim());
            }
        });

        if (global) {
            TextView scriptLink = findViewById(R.id.direct_rules_script_link);
            scriptLink.setVisibility(View.VISIBLE);
            // back to the script editor if it opened this screen, instead of a second editor
            // that would leave the first one with the old text
            scriptLink.setOnClickListener(v -> startActivity(new Intent(this, CustomScriptActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)));
        }

        add.setOnClickListener(v -> addRule());
        portSeparator.setOnClickListener(v -> insertPortSeparator());
        refreshRules();
    }

    /** Which address family a destination limits the rule to. */
    private void showFamily(String value) {
        if (value.isEmpty() || !AppRuleHelper.isValidDestination(value)) {
            family.setVisibility(View.GONE);
            return;
        }
        family.setText(AppRuleHelper.isIpv6Destination(value) ? R.string.direct_rules_family_v6 : R.string.direct_rules_family_v4);
        family.setVisibility(View.VISIBLE);
    }

    private String networkLabel(String value) {
        switch (value) {
            case AppRuleHelper.NETWORK_WIFI:
                return getString(R.string.direct_rules_network_wifi);
            case AppRuleHelper.NETWORK_MOBILE:
                return getString(R.string.direct_rules_network_mobile);
            case AppRuleHelper.NETWORK_VPN:
                return getString(R.string.direct_rules_network_vpn);
            default:
                return getString(R.string.direct_rules_network_all);
        }
    }

    private void addRule() {
        String actionValue = action.getCheckedRadioButtonId() == R.id.direct_rule_action_block
                ? AppRuleHelper.ACTION_BLOCK : AppRuleHelper.ACTION_ALLOW;
        String networkValue = networkValues.get(Math.max(network.getSelectedItemPosition(), 0));
        String destinationValue = destination.getText().toString().trim();
        String portValue = port.getText().toString().trim();
        String protocolValue = protocol.getSelectedItem().toString().toLowerCase(Locale.US);

        // a global rule without any match would allow / block everything
        if (destinationValue.isEmpty() && portValue.isEmpty()) {
            Toast.makeText(this, R.string.direct_rules_need_match, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!destinationValue.isEmpty() && !AppRuleHelper.isValidDestination(destinationValue)) {
            Toast.makeText(this, R.string.direct_rules_invalid_destination, Toast.LENGTH_SHORT).show();
            return;
        }

        if (AppRuleHelper.isIpv6Destination(destinationValue) && !G.enableIPv6()) {
            Toast.makeText(this, R.string.direct_rules_ipv6_disabled, Toast.LENGTH_LONG).show();
            return;
        }

        if (!portValue.isEmpty()) {
            if ("any".equals(protocolValue)) {
                Toast.makeText(this, R.string.direct_rules_port_needs_protocol, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!AppRuleHelper.isValidPortRange(portValue)) {
                Toast.makeText(this, R.string.direct_rules_invalid_port, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String rule = Api.validateCustomRuleForStorage(AppRuleHelper.buildStoredRule(
                uid, actionValue, networkValue, destinationValue, protocolValue, portValue));
        if (rule == null) {
            Toast.makeText(this, R.string.direct_rules_invalid_rule, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = AppRuleHelper.buildRuleName(uid, actionValue, networkValue, destinationValue, protocolValue, portValue);
        if (SQLite.select().from(CustomRule.class).where(CustomRule_Table.name.eq(name)).querySingle() != null) {
            Toast.makeText(this, R.string.direct_rules_exists, Toast.LENGTH_SHORT).show();
            return;
        }
        CustomRule customRule = new CustomRule(name, rule);
        customRule.setActive(true);
        customRule.save();
        rulesChanged();

        action.check(R.id.direct_rule_action_allow);
        destination.setText("");
        port.setText("");
        protocol.setSelection(0);
        network.setSelection(0);
        Toast.makeText(this, R.string.direct_rules_added, Toast.LENGTH_SHORT).show();
        refreshRules();
    }

    private void rulesChanged() {
        MainActivity.requireFullApply();
        Api.setRulesUpToDate(false);
    }

    private void insertPortSeparator() {
        int start = Math.max(port.getSelectionStart(), 0);
        int end = Math.max(port.getSelectionEnd(), 0);
        port.getText().replace(Math.min(start, end), Math.max(start, end), ":", 0, 1);
    }

    private void refreshRules() {
        rulesList.removeAllViews();
        List<CustomRule> rules = AppRuleHelper.getRulesForUid(uid);

        emptyView.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);
        orderHint.setVisibility(rules.isEmpty() ? View.GONE : View.VISIBLE);
        // listed in the order they are checked: all networks first, block before allow
        List<Object[]> rows = new ArrayList<>();
        for (CustomRule rule : rules) {
            AppRuleHelper.ParsedRule parsed = AppRuleHelper.parseRuleName(rule.getName());
            if (parsed != null) {
                rows.add(new Object[]{rule, parsed});
            }
        }
        Collections.sort(rows, (a, b) -> listOrder((AppRuleHelper.ParsedRule) a[1]) - listOrder((AppRuleHelper.ParsedRule) b[1]));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Object[] row : rows) {
            rulesList.addView(createRuleView(inflater, (CustomRule) row[0], (AppRuleHelper.ParsedRule) row[1]));
        }
    }

    private static int listOrder(AppRuleHelper.ParsedRule parsed) {
        return (AppRuleHelper.NETWORK_ALL.equals(parsed.network) ? 0 : 10) + AppRuleHelper.applyOrder(parsed);
    }

    private View createRuleView(LayoutInflater inflater, CustomRule rule, AppRuleHelper.ParsedRule parsed) {
        View row = inflater.inflate(R.layout.direct_rule_item, rulesList, false);

        TextView chip = row.findViewById(R.id.direct_rule_item_action);
        chip.setText(parsed.isBlock() ? R.string.direct_rules_block : R.string.direct_rules_allow);
        chip.setAllCaps(true);
        chip.setBackgroundResource(parsed.isBlock() ? R.drawable.rule_chip_block : R.drawable.rule_chip_allow);

        ((TextView) row.findViewById(R.id.direct_rule_item_summary)).setText(summary(parsed));
        String networkText = networkLabel(parsed.network);
        if (AppRuleHelper.NETWORK_VPN.equals(parsed.network) && !G.enableVPN()) {
            networkText += " · " + getString(R.string.direct_rules_vpn_off);
        }
        ((TextView) row.findViewById(R.id.direct_rule_item_network)).setText(networkText);

        SwitchCompat active = row.findViewById(R.id.direct_rule_item_active);
        active.setChecked(rule.isActive());
        active.setOnCheckedChangeListener((button, checked) -> {
            rule.setActive(checked);
            rule.save();
            rulesChanged();
        });

        ImageButton remove = row.findViewById(R.id.direct_rule_item_remove);
        remove.setOnClickListener(v -> {
            rule.delete();
            rulesChanged();
            Toast.makeText(this, R.string.direct_rules_removed, Toast.LENGTH_SHORT).show();
            refreshRules();
        });
        return row;
    }

    /** "203.0.113.50 · TCP · port 443" */
    private String summary(AppRuleHelper.ParsedRule parsed) {
        List<String> parts = new ArrayList<>();
        parts.add(parsed.destination.isEmpty() ? getString(R.string.direct_rules_any_address) : parsed.destination);
        String proto = parsed.protocol.isEmpty() ? "any" : parsed.protocol;
        parts.add("any".equals(proto) ? getString(R.string.direct_rules_any_protocol) : proto.toUpperCase(Locale.US));
        if (!parsed.port.isEmpty()) {
            parts.add(getString(R.string.direct_rules_port_value, parsed.port));
        }
        return TextUtils.join(" · ", parts);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
