package dev.ukanth.ufirewall;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.topjohnwu.superuser.Shell;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import dev.ukanth.ufirewall.log.Log;


public class MultiUser {
    public static final String TAG = "AFWall";

    private static final Pattern list_users = Pattern.compile("\\s*UserInfo\\{(.*?):(.*):(.*?)\\}.*", Pattern.MULTILINE);

    // just call `pm list users` directly - the HiddenApi IUserManager dance still
    // requires MANAGE_USERS which has android:protectionLevel="signature|privileged"
    // so there is no way for us to get it even with root su shell.
    public static List<UserInfo> getUsers() {
        Shell.Result result = Shell.cmd("pm list users").exec();
        List<UserInfo> res = new ArrayList();
        if (!result.isSuccess()) {
            Log.w(TAG, "pm list users failed; further errors likely");
            return res;
        }
        List<String> out = result.getOut();
        Matcher matcher;
        for (String item : out) {
            matcher = list_users.matcher(item);
            if (matcher.find() && matcher.groupCount() > 0) {
                int user_id = Integer.parseInt(matcher.group(1));
                String username = matcher.group(2);
                int flag = Integer.parseInt(matcher.group(3), 16);
                UserInfo ui = new UserInfo();
                ui.id = user_id;
                ui.name = username;
                //ui.flags = flag; // hiddenapi-stub doesn't yet expose this, we don't need it anyway
                res.add(ui);
            }
        }
        return res;
    }

    public static void setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }

        Shell.Result result = Shell.cmd("pm grant dev.ukanth.ufirewall android.permission.INTERACT_ACROSS_USERS").exec();
        if (!result.isSuccess()) {
            Log.w(TAG, "pm grant INTERACT_ACROSS_USERS failed; further errors likely");
        }
    }

    // Copied from LSPosed daemon/**/PackageService.java
    private static IBinder pbinder = null;
    private static IPackageManager pm = null;
    public static final int PER_USER_RANGE = 100000;

    // Copied from LSPosed daemon/**/PackageService.java
    // We drop MATCH_ANY_USER as we don't need it, and it requires MANAGE_USERS which we can't get, as explained above
    public static final int MATCH_ALL_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | PackageManager.MATCH_UNINSTALLED_PACKAGES; // | MATCH_ANY_USER;

    public static final int MATCH_ALL_METADATA = PackageManager.GET_META_DATA | MATCH_ALL_FLAGS;

    // Copied from LSPosed daemon/**/PackageService.java
    private static final IBinder.DeathRecipient precipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.w(TAG, "pm is dead");
            pbinder.unlinkToDeath(this, 0);
            pbinder = null;
            pm = null;
        }
    };

    // Copied from LSPosed daemon/**/PackageService.java
    private static IPackageManager getPackageManager() {
        if (pbinder == null || pm == null) {
            pbinder = ServiceManager.getService("package");
            if (pbinder == null) return null;
            try {
                pbinder.linkToDeath(precipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, android.util.Log.getStackTraceString(e));
            }
            pm = IPackageManager.Stub.asInterface(pbinder);
        }
        return pm;
    }

    // Copied from LSPosed daemon/**/PackageService.java
    public static boolean isPackageAvailable(String packageName, int userId, boolean ignoreHidden) throws RemoteException {
        return pm.isPackageAvailable(packageName, userId) || (ignoreHidden && pm.getApplicationHiddenSettingAsUser(packageName, userId));
    }

    public static int packageUserId(PackageInfo info) {
        return applicationUserId(info.applicationInfo);
    }

    public static int applicationUserId(ApplicationInfo info) {
        return (info == null)? -1: info.uid / PER_USER_RANGE;
    }

    // Copied from LSPosed daemon/**/PackageService.java
    // This requires INTERACT_ACROSS_USERS permission. LSPosed already has it, but
    // we need to grant it at runtime - make sure you call setup() in app.onCreate(_)
    // We use this instead of `pm list packages` so that we have ApplicationInfo
    // objects, which allows us to get the icons and labels in a convenient manner.
    public static List<PackageInfo> getInstalledPackagesFromAllUsers(int flags/*, boolean filterNoProcess*/) throws RemoteException {
        List<PackageInfo> res = new ArrayList<>();
        IPackageManager pm = getPackageManager();
        if (pm == null) return res;
        for (UserInfo user : getUsers()) {
            // in case pkginfo of other users in primary user
            ParceledListSlice<PackageInfo> infos;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                infos = pm.getInstalledPackages((long) flags, user.id);
            } else {
                infos = pm.getInstalledPackages(flags, user.id);
            }
            res.addAll(infos
                    .getList().parallelStream()
                    .filter(info -> info.applicationInfo != null && packageUserId(info) == user.id)
                    .filter(info -> {
                        try {
                            return isPackageAvailable(info.packageName, user.id, true);
                        } catch (RemoteException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList()));
        }
        /*if (filterNoProcess) {
            return new ParcelableListSlice<>(res.parallelStream().filter(packageInfo -> {
                try {
                    PackageInfo pkgInfo = getPackageInfoWithComponents(packageInfo.packageName, MATCH_ALL_FLAGS, packageInfo.applicationInfo.uid / PER_USER_RANGE);
                    return !fetchProcesses(pkgInfo).isEmpty();
                } catch (RemoteException e) {
                    Log.w(TAG, "filter failed", e);
                    return true;
                }
            }).collect(Collectors.toList()));
        }*/
        return res;
    }
}
