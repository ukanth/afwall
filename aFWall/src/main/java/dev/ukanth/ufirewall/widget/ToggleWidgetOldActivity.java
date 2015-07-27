package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.R;

public class ToggleWidgetOldActivity extends Activity implements
		OnClickListener {

	private static Button enableButton;
	private static Button disableButton;
	private static Button defaultButton;
	private static Button profButton1;
	private static Button profButton2;
	private static Button profButton3;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.toggle_widget_old_view);

		enableButton = (Button) this.findViewById(R.id.toggle_enable_firewall);
		disableButton = (Button) this
				.findViewById(R.id.toggle_disable_firewall);
		defaultButton = (Button) this.findViewById(R.id.toggle_default_profile);
		profButton1 = (Button) this.findViewById(R.id.toggle_profile1);
		profButton2 = (Button) this.findViewById(R.id.toggle_profile2);
		profButton3 = (Button) this.findViewById(R.id.toggle_profile3);

		if (Api.isEnabled(getApplicationContext())) {
			enableOthers();
		} else {
			disableOthers();
		}

		enableButton.setOnClickListener(this);
		disableButton.setOnClickListener(this);
		defaultButton.setOnClickListener(this);
		profButton1.setOnClickListener(this);
		profButton2.setOnClickListener(this);
		profButton3.setOnClickListener(this);

		if (!G.enableMultiProfile()) {
			profButton1.setEnabled(false);
			profButton2.setEnabled(false);
			profButton3.setEnabled(false);
		} else {
			if (Api.isEnabled(getApplicationContext())) {
				int position = G.storedPosition();
				if (position == 0) {
					disableDefault();
				} else {
					disableCustom(position);
				}
			}
		}

	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.toggle_enable_firewall:
			startAction(1);
			break;
		case R.id.toggle_disable_firewall:
			startAction(2);
			break;
		case R.id.toggle_default_profile:
			startAction(3);
			break;
		case R.id.toggle_profile1:
			startAction(4);
			break;
		case R.id.toggle_profile2:
			startAction(5);
			break;
		case R.id.toggle_profile3:
			startAction(6);
			break;
		}
	}

	private void startAction(final int i) {

		final Handler toaster = new Handler() {
			public void handleMessage(Message msg) {
				if (msg.arg1 != 0)
					Toast.makeText(getApplicationContext(), msg.arg1,
							Toast.LENGTH_SHORT).show();
			}
		};
		final Context context = getApplicationContext();
	/*	final String oldPwd = G.profile_pwd();
		final String newPwd = getSharedPreferences(Api.PREF_FIREWALL_STATUS, 0)
				.getString("LockPassword", "");*/
		new Thread() {
			@Override
			public void run() {
				Looper.prepare();
				final Message msg = new Message();
				switch (i) {
				case 1:
					if (applyRules(context, msg, toaster)) {
						Api.setEnabled(context, true, false);
					}
					break;
				case 2:
					// validation, check for password

					if(G.protectionLevel().equals("p0")){
						if (Api.purgeIptables(context, false)) {
							msg.arg1 = R.string.toast_disabled;
							toaster.sendMessage(msg);
							disableOthers();
							Api.setEnabled(context, false, false);
						} else {
							msg.arg1 = R.string.toast_error_disabling;
							toaster.sendMessage(msg);
						}
					} else {
						msg.arg1 = R.string.widget_disable_fail;
						toaster.sendMessage(msg);
					}

					break;
				case 3:
					G.setProfile(G.enableMultiProfile(), 0);
					if (applyProfileRules(context, msg, toaster)) {
						disableDefault();
					}
					break;
				case 4:
					G.setProfile(true, 1);
					if (applyProfileRules(context, msg, toaster)) {
						disableCustom(1);
					}
					break;
				case 5:
					G.setProfile(true, 2);
					if (applyProfileRules(context, msg, toaster)) {
						disableCustom(2);
					}
					break;
				case 6:
					G.setProfile(true, 3);
					if (applyProfileRules(context, msg, toaster)) {
						disableCustom(3);
					}
					break;
				}
			}
		}.start();
	}

	private void enableOthers() {
		runOnUiThread(new Runnable() {
			public void run() {
				enableButton.setEnabled(false);
				disableButton.setEnabled(true);
				defaultButton.setEnabled(true);
				if (G.enableMultiProfile()) {
					profButton1.setEnabled(true);
					profButton2.setEnabled(true);
					profButton3.setEnabled(true);
				}
			}
		});

	}

	private void disableOthers() {
		runOnUiThread(new Runnable() {
			public void run() {
				enableButton.setEnabled(true);
				disableButton.setEnabled(false);
				defaultButton.setEnabled(false);
				profButton1.setEnabled(false);
				profButton2.setEnabled(false);
				profButton3.setEnabled(false);
			}
		});
	}

	private void disableDefault() {
		runOnUiThread(new Runnable() {
			public void run() {
				defaultButton.setEnabled(false);
				if (G.enableMultiProfile()) {
					profButton1.setEnabled(true);
					profButton2.setEnabled(true);
					profButton3.setEnabled(true);
				}
			}
		});
	}

	private void disableCustom(final int code) {
		runOnUiThread(new Runnable() {
			public void run() {
				switch (code) {
				case 1:
					defaultButton.setEnabled(true);
					profButton1.setEnabled(false);
					profButton2.setEnabled(true);
					profButton3.setEnabled(true);
					break;
				case 2:
					defaultButton.setEnabled(true);
					profButton1.setEnabled(true);
					profButton2.setEnabled(false);
					profButton3.setEnabled(true);
					break;
				case 3:
					defaultButton.setEnabled(true);
					profButton1.setEnabled(true);
					profButton2.setEnabled(true);
					profButton3.setEnabled(false);
				}
			}
		});
	}

	private boolean applyRules(Context context, Message msg, Handler toaster) {
		boolean success = false;
		if (Api.applySavedIptablesRules(context, false)) {
			msg.arg1 = R.string.toast_enabled;
			toaster.sendMessage(msg);
			enableOthers();
			success = true;
		} else {
			msg.arg1 = R.string.toast_error_enabling;
			toaster.sendMessage(msg);
		}
		return success;
	}

	private boolean applyProfileRules(Context context, Message msg,
			Handler toaster) {
		boolean success = false;
		if (Api.applySavedIptablesRules(context, false)) {
			msg.arg1 = R.string.rules_applied;
			toaster.sendMessage(msg);
			enableOthers();
			success = true;
		} else {
			msg.arg1 = R.string.error_apply;
			toaster.sendMessage(msg);
		}
		return success;
	}
}
