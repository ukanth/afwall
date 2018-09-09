package dev.ukanth.ufirewall.activity;

import android.content.Intent;
import android.os.Bundle;

import dev.ukanth.ufirewall.MainActivity;

public class StartActivity extends BaseActivity {

    /*
     * This activity only existed, so the user can toggle between
     * classic and material icon.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        startActivity(intent);
        finish();
    }

}