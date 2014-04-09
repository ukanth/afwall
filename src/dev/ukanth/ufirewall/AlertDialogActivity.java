package dev.ukanth.ufirewall;

import java.lang.ref.WeakReference;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Window;

public class AlertDialogActivity  extends SherlockListActivity {
	WeakReference<AlertDialog> alertDialog;
	
	@Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawableResource(R.drawable.class_zero_background);
        
        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");

        new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, mOkListener)
        .setCancelable(false)
        .show();
    }

    private final OnClickListener mOkListener = new OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
        	Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        	intent.putExtra("EXIT", true);
        	startActivity(intent);
        	finish();
        }
    };
    
    @Override
	protected void onDestroy(){
		if(alertDialog!=null){
			AlertDialog d=alertDialog.get();
			if(d!=null) {
				d.dismiss();
			}
		}
		super.onDestroy();
	}
}
