package dev.ukanth.ufirewall.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.List;

import com.afollestad.materialdialogs.MaterialDialog;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.profiles.ProfileData;
import dev.ukanth.ufirewall.profiles.ProfileHelper;
import dev.ukanth.ufirewall.util.FirewallActions;
import dev.ukanth.ufirewall.util.G;
import dev.ukanth.ufirewall.util.SecurityUtil;
import dev.ukanth.ufirewall.widget.RadialMenuWidget.RadialMenuEntry;

public class ToggleWidgetActivity extends Activity {

    private RadialMenuWidget pieMenu;
    private RelativeLayout relativeLayout;

    private static final int ACTION_ENABLE = 1;
    private static final int ACTION_DISABLE = 2;
    private static final int ACTION_PROFILE = 4;

    private int actionType = 0;
    // identifier of the profile to switch to (ACTION_PROFILE)
    private String pendingProfileId;
    private SecurityUtil security;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.toggle_widget_view);
        security = new SecurityUtil(this);

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
            startAction(ACTION_ENABLE);
        }
    }

    public class Status implements RadialMenuEntry {
        public String getName() {
            return activeProfileName();
        }

        public String getLabel() {
            return activeProfileName();
        }

        private String activeProfileName() {
            if (!G.enableMultiProfile()) {
                return "";
            }
            String identifier = G.storedProfile();
            if (Api.DEFAULT_PREFS_NAME.equals(identifier)) {
                return G.gPrefs.getString("default", getApplicationContext().getString(R.string.defaultProfile));
            }
            ProfileData data = ProfileHelper.getProfileByIdentifier(identifier);
            return data != null ? data.getName() : identifier;
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
            startAction(ACTION_DISABLE);
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

        private final List<RadialMenuEntry> children = new ArrayList<RadialMenuEntry>();

        public List<RadialMenuEntry> getChildren() {
            return children;
        }

        public Profiles() {
            children.add(new DefaultProfile());
            for (ProfileData data : ProfileHelper.getProfiles()) {
                children.add(new GenericProfile(data.getName()));
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

        private final String profileName;

        public GenericProfile(String profileName) {
            this.profileName = profileName;
        }

        public List<RadialMenuEntry> getChildren() {
            return null;
        }

        public void menuActiviated() {
            ProfileData data = ProfileHelper.getProfileByName(profileName);
            if (data != null) {
                // same checks (app lock) as the other actions
                pendingProfileId = data.getIdentifier();
                startAction(ACTION_PROFILE);
            }
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
            pendingProfileId = Api.DEFAULT_PREFS_NAME;
            startAction(ACTION_PROFILE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // device lock / pattern results of the app lock check
        security.handleActivityResult(requestCode, resultCode);
    }


    private void startAction(final int i) {
        actionType = i;
        if (i == ACTION_DISABLE && G.enableConfirm()) {
            confirmDisableFromWidget();
            return;
        }
        continueActionAfterConfirmation();
    }

    private void confirmDisableFromWidget() {
        new MaterialDialog.Builder(this)
                .title(R.string.confirmMsg)
                .cancelable(false)
                .positiveText(R.string.Yes)
                .negativeText(R.string.No)
                .onPositive((dialog, which) -> {
                    Log.i(Api.TAG, "Widget firewall disable confirmed");
                    dialog.dismiss();
                    continueActionAfterConfirmation();
                })
                .onNegative((dialog, which) -> {
                    Log.i(Api.TAG, "Widget firewall disable canceled");
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void continueActionAfterConfirmation() {
        security.passCheck(allowed -> {
            if (allowed) {
                invokeAction();
            } else {
                finish();
            }
        });
    }

    private void invokeAction() {
        final Context context = getApplicationContext();
        FirewallActions.Done updateNotification = ok -> Api.updateNotification(Api.isEnabled(context), context);
        switch (actionType) {
            case ACTION_ENABLE:
                FirewallActions.setEnabled(context, true, true, updateNotification);
                break;
            case ACTION_DISABLE:
                FirewallActions.setEnabled(context, false, true, updateNotification);
                break;
            case ACTION_PROFILE:
                if (pendingProfileId != null) {
                    // applies the rules only while the firewall is enabled
                    FirewallActions.switchProfile(context, pendingProfileId, true, updateNotification);
                }
                break;
        }
    }

}