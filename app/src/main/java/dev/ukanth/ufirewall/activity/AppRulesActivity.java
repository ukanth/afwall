package dev.ukanth.ufirewall.activity;

import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.List;
import java.util.Locale;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.customrules.CustomRule;
import dev.ukanth.ufirewall.util.AppRuleHelper;
import dev.ukanth.ufirewall.util.G;

public class AppRulesActivity extends AppCompatActivity {

    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_LABEL = "label";

    private int uid;
    private String packageName;
    private String label;
    private EditText destination;
    private EditText port;
    private Spinner protocol;
    private LinearLayout rulesList;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTheme();
        setContentView(R.layout.app_rules);
        setTitle(R.string.direct_rules_title);

        Toolbar toolbar = findViewById(R.id.app_rules_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        uid = getIntent().getIntExtra(EXTRA_UID, Api.SPECIAL_UID_ANY);
        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (TextUtils.isEmpty(label)) {
            label = packageName != null ? packageName : String.valueOf(uid);
        }

        TextView appTitle = findViewById(R.id.app_rules_app);
        appTitle.setText(label + " [" + uid + "]");

        destination = findViewById(R.id.direct_rule_destination);
        port = findViewById(R.id.direct_rule_port);
        port.setKeyListener(DigitsKeyListener.getInstance("0123456789:"));
        protocol = findViewById(R.id.direct_rule_protocol);
        rulesList = findViewById(R.id.direct_rules_list);
        emptyView = findViewById(R.id.direct_rules_empty);
        Button add = findViewById(R.id.direct_rule_add);
        Button portSeparator = findViewById(R.id.direct_rule_port_separator);

        ArrayAdapter<String> protocolAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Any", "TCP", "UDP"});
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        protocol.setAdapter(protocolAdapter);

        add.setOnClickListener(v -> addRule());
        portSeparator.setOnClickListener(v -> insertPortSeparator());
        refreshRules();
    }

    private void initTheme() {
        switch (G.getSelectedTheme()) {
            case "D":
                setTheme(R.style.AppDarkTheme);
                break;
            case "L":
                setTheme(R.style.AppLightTheme);
                break;
            case "LHC":
                setTheme(R.style.AppLightHighContrastTheme);
                break;
            case "B":
                setTheme(R.style.AppBlackTheme);
                break;
        }
    }

    private void addRule() {
        String destinationValue = destination.getText().toString().trim();
        String portValue = port.getText().toString().trim();
        String protocolValue = protocol.getSelectedItem().toString().toLowerCase(Locale.US);

        if (destinationValue.isEmpty() && portValue.isEmpty()) {
            Toast.makeText(this, R.string.direct_rules_need_match, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!destinationValue.isEmpty() && !AppRuleHelper.isValidDestination(destinationValue)) {
            Toast.makeText(this, R.string.direct_rules_invalid_destination, Toast.LENGTH_SHORT).show();
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

        String rule = Api.validateCustomRuleForStorage(AppRuleHelper.buildAllowRule(uid, destinationValue, protocolValue, portValue));
        if (rule == null) {
            Toast.makeText(this, R.string.direct_rules_invalid_rule, Toast.LENGTH_SHORT).show();
            return;
        }

        CustomRule customRule = new CustomRule(AppRuleHelper.buildAllowRuleName(uid, destinationValue, protocolValue, portValue), rule);
        customRule.setActive(true);
        customRule.save();
        MainActivity.requireFullApply();
        Api.setRulesUpToDate(false);

        destination.setText("");
        port.setText("");
        protocol.setSelection(0);
        Toast.makeText(this, R.string.direct_rules_added, Toast.LENGTH_SHORT).show();
        refreshRules();
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
        for (CustomRule rule : rules) {
            rulesList.addView(createRuleView(rule));
        }
    }

    private View createRuleView(CustomRule rule) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        TextView text = new TextView(this);
        String state = rule.isActive() ? "" : "Disabled - ";
        text.setText(state + AppRuleHelper.displayNameForRule(uid, rule.getName()) + "\n" + rule.getRule());
        text.setTextSize(13);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(text, textParams);

        Button remove = new Button(this);
        remove.setText(R.string.direct_rules_remove);
        remove.setOnClickListener(v -> {
            rule.delete();
            MainActivity.requireFullApply();
            Api.setRulesUpToDate(false);
            Toast.makeText(this, R.string.direct_rules_removed, Toast.LENGTH_SHORT).show();
            refreshRules();
        });
        row.addView(remove);

        return row;
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
