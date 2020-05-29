package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.service.RootCommand;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;
import dev.ukanth.ufirewall.widget.RadialMenuWidget.RadialMenuEntry;

import static dev.ukanth.ufirewall.util.SecurityUtil.LOCK_VERIFICATION;
import static dev.ukanth.ufirewall.util.SecurityUtil.REQ_ENTER_PATTERN;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FAILED;
import static haibison.android.lockpattern.LockPatternActivity.RESULT_FORGOT_PATTERN;

public class ToggleWidgetActivity extends Activity {

    private RadialMenuWidget pieMenu;
    private RelativeLayout relativeLayout;

    private int actionType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.toggle_widget_view);

        relativeLayout = this.findViewById(R.id.widgetCircle);
        pieMenu = new RadialMenuWidget(getBaseContext());

        pieMenu.setAnimationSpeed(0L);

        int xLayoutSize = relativeLayout.getWidth();
        int yLayoutSize = relativeLayout.getHeight();
        pieMenu.setSourceLocation(xLayoutSize, yLayoutSize);
        pieMenu.setIconSize(15, 30);
        pieMenu.setTextSize(13);

        pieMenu.setCenterCircle(new Close());
        pieMenu.addMenuEntry(new Status());
        pieMenu.addMenuEntry(new EnableFirewall());
        pieMenu.addMenuEntry(new DisableFirewall());

        if (G.enableMultiProfile()) {
            pieMenu.addMenuEntry(new Profiles());
        }

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

        relativeLayout.addView(pieMenu, params);

    }


    public class Close implements RadialMenuEntry {

        public String getName() {
            return "Close";
        }

        public String getLabel() {
            return null;
        }

        public int getIcon() {
            return android.R.drawable.ic_menu_close_clear_cancel;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            relativeLayout = findViewById(R.id.widgetCircle);
            relativeLayout.removeAllViews();
            finish();
        }
    }

    public class EnableFirewall implements RadialMenuEntry {
        public String getName() {
            return "";
        }

        public String getLabel() {
            return getString(R.string.enable);
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            actionType = 1;
            startAction(1);
        }
    }

    public class Status implements RadialMenuEntry {
        public String getName() {
            if (G.enableMultiProfile()) {
                switch (G.storedProfile()) {
                    case Api.DEFAULT_PREFS_NAME:
                        return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile));
                    case "AFWallProfile1":
                        return G.gPrefs.getString("profile1", getApplicationContext().getString(R.string.profile1));
                    case "AFWallProfile2":
                        return G.gPrefs.getString("profile2", getApplicationContext().getString(R.string.profile2));
                    case "AFWallProfile3":
                        return G.gPrefs.getString("profile3", getApplicationContext().getString(R.string.profile3));
                    default:
                        return G.storedProfile();
                }
            } else {
                return "";
            }
        }

        public String getLabel() {
            if (G.enableMultiProfile()) {
                switch (G.storedProfile()) {
                    case Api.DEFAULT_PREFS_NAME:
                        return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile));
                    case "AFWallProfile1":
                        return G.gPrefs.getString("profile1", getApplicationContext().getString(R.string.profile1));
                    case "AFWallProfile2":
                        return G.gPrefs.getString("profile2", getApplicationContext().getString(R.string.profile2));
                    case "AFWallProfile3":
                        return G.gPrefs.getString("profile3", getApplicationContext().getString(R.string.profile3));
                    default:
                        return G.storedProfile();
                }
            } else {
                return "";
            }
        }

        public int getIcon() {
            return (Api.isEnabled(getApplicationContext()) ? R.drawable.widget_on : R.drawable.widget_off);
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {

        }
    }

    public class DisableFirewall implements RadialMenuEntry {
        public String getName() {
            return "";
        }

        public String getLabel() {
            return getString(R.string.disable);
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            actionType = 2;
            startAction(2);
        }
    }


    public class Profiles implements RadialMenuEntry {
        public String getName() {
            return getString(R.string.profiles);
        }

        public String getLabel() {
            return getString(R.string.profiles);
        }

        public int getIcon() {
            return 0;
        }

        private List<RadialMenuEntry> children = new ArrayList<RadialMenuEntry>();

        public List<RadialMenuEntry> getChildren() {
            return children;
        }

        public Profiles() {
            if (!G.isProfileMigrated()) {
                children.add(new DefaultProfile());
                children.add(new Profile1());
                children.add(new Profile2());
                children.add(new Profile3());
                for (String profileName : G.getAdditionalProfiles()) {
                    RadialMenuEntry entry = new GenericProfile(profileName);
                    children.add(entry);
                }
            } else {
                children.add(new DefaultProfile());
                for (ProfileData data : ProfileHelper.getProfiles()) {
                    RadialMenuEntry entry = new GenericProfile(data.getName());
                    children.add(entry);
                }
            }
        }

        public void menuActiviated() {
        }
    }

    public class GenericProfile implements RadialMenuEntry {
        public String getName() {
            return profileName;
        }

        public String getLabel() {
            return profileName;
        }

        public int getIcon() {
            return 0;
        }

        private String profileName;

        public GenericProfile(String profileName) {
            this.profileName = profileName;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            final Handler toaster = new Handler() {
                public void handleMessage(Message msg) {
                    if (msg.arg1 != 0)
                        Toast.makeText(getApplicationContext(), msg.arg1, Toast.LENGTH_SHORT).show();
                }
            };
            final Context context = getApplicationContext();
            new Thread() {
                @Override
                public void run() {
                    if (G.isProfileMigrated()) {
                        ProfileData data = ProfileHelper.getProfileByName(profileName);
                        G.setProfile(true, data.getIdentifier());
                    } else {
                        G.setProfile(true, profileName);
                    }
                    Api.applySavedIptablesRules(context, true, new RootCommand()
                            .setSuccessToast(R.string.rules_applied)
                            .setFailureToast(R.string.error_apply)
                            .setCallback(new RootCommand.Callback() {
                                @Override
                                public void cbFunc(RootCommand state) {
                                    Message msg = new Message();
                                    if (state.exitCode == 0) {
                                        msg.arg1 = R.string.rules_applied;
                                    } else {
                                        // error details are already in logcat
                                        msg.arg1 = R.string.error_apply;
                                    }
                                    toaster.sendMessage(msg);
                                }
                            }));
                    //Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                    Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                }
            }.start();
        }
    }

    public class DefaultProfile implements RadialMenuEntry {
        public String getName() {
            return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile));
        }

        public String getLabel() {
            return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile));
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            startAction(3);
        }
    }

    public class Profile1 implements RadialMenuEntry {
        public String getName() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile1", getString(R.string.profile1));
            } else {
                return "AFWallProfile1";
            }
        }

        public String getLabel() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile1", getString(R.string.profile1));
            } else {
                return "AFWallProfile1";
            }
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            startAction(4);
        }
    }

    public class Profile2 implements RadialMenuEntry {
        public String getName() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile2", getString(R.string.profile2));
            } else {
                return "AFWallProfile2";
            }
        }

        public String getLabel() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile2", getString(R.string.profile2));
            } else {
                return "AFWallProfile2";
            }
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            startAction(5);
        }
    }

    public class Profile3 implements RadialMenuEntry {
        public String getName() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile3", getString(R.string.profile3));
            } else {
                return "AFWallProfile3";
            }
        }

        public String getLabel() {
            if (!G.isProfileMigrated()) {
                return G.gPrefs.getString("profile3", getString(R.string.profile3));
            } else {
                return "AFWallProfile3";
            }
        }

        public int getIcon() {
            return 0;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            startAction(6);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LOCK_VERIFICATION: {
                switch (resultCode) {
                    case RESULT_OK:
                        invokeAction();
                        break;
                    default:
                        ToggleWidgetActivity.this.finish();
                        android.os.Process.killProcess(android.os.Process.myPid());
                        break;
                }
            }
            break;
            case REQ_ENTER_PATTERN: {
                switch (resultCode) {
                    case RESULT_OK:
                        invokeAction();
                        break;
                    case RESULT_CANCELED:
                        ToggleWidgetActivity.this.finish();
                        break;
                    case RESULT_FAILED:
                        ToggleWidgetActivity.this.finish();
                        break;
                    case RESULT_FORGOT_PATTERN:
                        ToggleWidgetActivity.this.finish();
                        break;
                    default:
                        ToggleWidgetActivity.this.finish();
                        break;
                }
            }
            break;
        }
    }


    private void startAction(final int i) {
        actionType = i;
        SecurityUtil util = new SecurityUtil(ToggleWidgetActivity.this);
        boolean isProtected = util.isPasswordProtected();
        if (!isProtected) {
            invokeAction();
        } else {
            util.passCheck();
        }
    }

    private void invokeAction() {
        final Handler toaster = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.arg1 != 0) {
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(),msg.arg1,Toast.LENGTH_SHORT).show());
                }
            }
        };
        final Context context = getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                if (actionType < 7) {
                    switch (actionType) {
                        case 1:
                            Api.applySavedIptablesRules(context, true, new RootCommand()
                                    .setSuccessToast(R.string.rules_applied)
                                    .setFailureToast(R.string.error_apply)
                                    .setReopenShell(true)
                                    .setCallback(new RootCommand.Callback() {
                                        @Override
                                        public void cbFunc(RootCommand state) {
                                            final Message msg = new Message();
                                            if (state.exitCode == 0) {
                                                msg.arg1 = R.string.rules_applied;
                                                Api.setEnabled(context, true, false);
                                            } else {
                                                // error details are already in logcat
                                                msg.arg1 = R.string.error_apply;
                                            }
                                            toaster.sendMessage(msg);
                                        }
                                    }));
                            break;
                        case 2:
                            //validation, check for password
                            Api.purgeIptables(context, true, new RootCommand()
                                    .setSuccessToast(R.string.toast_disabled)
                                    .setFailureToast(R.string.toast_error_disabling)
                                    .setReopenShell(true)
                                    .setCallback(new RootCommand.Callback() {
                                        public void cbFunc(RootCommand state) {
                                            final Message msg = new Message();
                                            if (state.exitCode == 0) {
                                                msg.arg1 = R.string.toast_disabled;
                                                Api.setEnabled(context, false, false);
                                            } else {
                                                // error details are already in logcat
                                                msg.arg1 = R.string.toast_error_disabling;
                                            }
                                            toaster.sendMessage(msg);
                                        }
                                    }));
                            break;
                        case 3:
                            G.setProfile(G.enableMultiProfile(), "AFWallPrefs");
                            break;
                        case 4:
                            G.setProfile(true, "AFWallProfile1");
                            break;
                        case 5:
                            G.setProfile(true, "AFWallProfile2");
                            break;
                        case 6:
                            G.setProfile(true, "AFWallProfile3");
                            break;
                    }
                    if (actionType > 2) {
                        final Message msg = new Message();
                        Api.applySavedIptablesRules(context, true, new RootCommand()
                                .setSuccessToast(R.string.rules_applied)
                                .setFailureToast(R.string.error_apply)
                                .setCallback(new RootCommand.Callback() {
                                    @Override
                                    public void cbFunc(RootCommand state) {
                                        if (state.exitCode == 0) {
                                            msg.arg1 = R.string.rules_applied;
                                        } else {
                                            // error details are already in logcat
                                            msg.arg1 = R.string.error_apply;
                                        }
                                        toaster.sendMessage(msg);
                                    }
                                }));
                        G.reloadPrefs();
                    }
                }
                //Api.showNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
                Api.updateNotification(Api.isEnabled(getApplicationContext()), getApplicationContext());
            }
        }.start();
    }

    /*private boolean applyProfileRules(final Context context, final Message msg, final Handler toaster) {
        boolean ret = Api.applySavedIptablesRules(context, false, new RootCommand()
                .setFailureToast(R.string.error_apply)
                .setCallback(new RootCommand.Callback() {
                    @Override
                    public void cbFunc(RootCommand state) {
                        if (state.exitCode == 0) {
                            msg.arg1 = R.string.rules_applied;
                        } else {
                            // error details are already in logcat
                            msg.arg1 = R.string.error_apply;
                        }
                    }
                }));
        return ret;
    }*/
}