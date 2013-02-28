/**
 * Custom scripts activity.
 * This screen is displayed to change the custom scripts.
 * 
 * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
 * Copyright (C) 2011-2012  Umakanthan Chandran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
 * @version 1.1
 */
package dev.ukanth.ufirewall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Custom scripts activity.
 * This screen is displayed to change the custom scripts.
 */
public class CustomScriptActivity extends Activity implements OnClickListener {
	private EditText script;
	private EditText script2;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final View view = getLayoutInflater().inflate(R.layout.customscript, null);
		((Button)view.findViewById(R.id.customscript_ok)).setOnClickListener(this);
		((Button)view.findViewById(R.id.customscript_cancel)).setOnClickListener(this);
		((TextView)view.findViewById(R.id.customscript_link)).setMovementMethod(LinkMovementMethod.getInstance());
		final SharedPreferences prefs = getSharedPreferences(Api.PREFS_NAME, 0);
		this.script = (EditText) view.findViewById(R.id.customscript);
		this.script.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT, ""));
		this.script2 = (EditText) view.findViewById(R.id.customscript2);
		this.script2.setText(prefs.getString(Api.PREF_CUSTOMSCRIPT2, ""));
		setTitle(R.string.set_custom_script);
		setContentView(view);
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
			final DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case DialogInterface.BUTTON_POSITIVE:
						resultOk();
						break;
					case DialogInterface.BUTTON_NEGATIVE:
						// Propagate the event back to perform the desired action
						CustomScriptActivity.super.onKeyDown(keyCode, event);
						break;
					}
				}
			};
			final AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.unsaved_changes).setMessage(R.string.unsaved_changes_message)
					.setPositiveButton(R.string.apply, dialogClickListener)
					.setNegativeButton(R.string.discard, dialogClickListener).show();
			// Say that we've consumed the event
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
}
