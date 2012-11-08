package dev.ukanth.ufirewall;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;

public class Rules extends SherlockActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.rules);
        
        

        //Load partially transparent black background
        getSupportActionBar().setBackgroundDrawable(getResources().getDrawable(R.drawable.ab_bg_black));
        
        Intent intent = getIntent();
        String message = intent.getStringExtra(MainActivity.IPTABLE_RULES);
        String title = intent.getStringExtra(MainActivity.VIEW_TITLE);
        setTitle(title);
        final EditText rulesText = (EditText)findViewById(R.id.rules);
        rulesText.setTextColor(Color.WHITE);
        rulesText.setFocusable(false);
        rulesText.setKeyListener(null);
        rulesText.setClickable(false);
        rulesText.setText(message);
        
        // Set the text view as the activity layout
        
    }

}
