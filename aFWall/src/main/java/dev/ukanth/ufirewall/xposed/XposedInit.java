package dev.ukanth.ufirewall.xposed;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import com.crossbowffs.remotepreferences.RemotePreferences;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.BuildConfig;
import dev.ukanth.ufirewall.log.Log;
import dev.ukanth.ufirewall.preferences.SharePreference;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;


/**
 * Created by ukanth on 6/7/16.
 */
public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static final String MY_APP = BuildConfig.APPLICATION_ID;
    public static final String TAG = "AFWallXPosed";
    public static String MODULE_PATH = null;
    private static Context context;
    private XSharedPreferences prefs;
    private SharedPreferences pPrefs;
    private SharedPreferences sharedPreferences;
    private Activity activity;

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        try {
            //Log.i(TAG,"Looking for AFWall: " +  loadPackageParam.packageName);
            if (loadPackageParam.packageName.equals(MY_APP)) {
                Log.i(TAG, "Matched Package and now hooking: " + loadPackageParam.packageName);
                reloadPreference();
                interceptAFWall(loadPackageParam);
            }
            interceptDownloadManager(loadPackageParam);
        } catch (XposedHelpers.ClassNotFoundError e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

    //Check if AFWall is hooked to make sure XPosed works fine.
    private void interceptAFWall(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Class<?> afwallHook = findClass("dev.ukanth.ufirewall.util.G", loadPackageParam.classLoader);
        XC_MethodHook xposedResult = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.i(TAG, "Util.isXposedEnabled hooked");
                param.setResult(true);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            }
        };
        XposedBridge.hookAllMethods(afwallHook, "isXposedEnabled", xposedResult);
    }

    private void reloadPreference() {
        try {
            if (context == null) {
                Object activityThread = callStaticMethod(
                        findClass("android.app.ActivityThread", null), "currentActivityThread");
                context = (Context) AndroidAppHelper.currentApplication();
            }
            if (prefs == null) {
                prefs = new XSharedPreferences(MY_APP);
                prefs.makeWorldReadable();
                prefs.reload();
                sharedPreferences = new RemotePreferences(context, BuildConfig.APPLICATION_ID, Api.PREFS_NAME);
            } else {
                prefs.makeWorldReadable();
                prefs.reload();
            }
            //pPrefs = context.getSharedPreferences(Api.PREFS_NAME,Context.MODE_PRIVATE);
            pPrefs = new SharePreference(context, MY_APP, Api.PREFS_NAME);
            Log.d(TAG, "Reloaded preferences from AFWall");
        } catch (Exception e) {
            Log.d(TAG, "Exception in reloading preferences" + e.getLocalizedMessage());
        }
    }


    private void interceptDownloadManager(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        final ApplicationInfo applicationInfo = loadPackageParam.appInfo;
        Class<?> downloadManager = findClass("android.app.DownloadManager", loadPackageParam.classLoader);
        Class<?> downloadManagerRequest = findClass("android.app.DownloadManager.Request", loadPackageParam.classLoader);

        XC_MethodHook dmSingleResult = new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadPreference();
                final boolean isAppAllowed = Api.isAppAllowed(context, applicationInfo, sharedPreferences, pPrefs);
                Log.d(TAG, "DM Calling Application: " + applicationInfo.packageName + ", Allowed: " + isAppAllowed);
                if (!isAppAllowed) {
                    if (param.getResult() != null) {
                        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        dm.remove((Long) param.getResult());
                    }
                    param.setResult(0L);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity().getApplicationContext(), "AFWall+ denied access to Download Manager for package(uid) : " + applicationInfo.packageName + "(" + applicationInfo.uid + ")", Toast.LENGTH_LONG).show());
                    }
                }
            }
        };

        XC_MethodHook hookDM = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadPreference();

                final boolean isAppAllowed = Api.isAppAllowed(context, applicationInfo, sharedPreferences, pPrefs);
                Log.d(TAG, "DM Calling Application: " + applicationInfo.packageName + ", Allowed: " + isAppAllowed);
                if (!isAppAllowed) {
                    final Uri uri = (Uri) param.args[0];
                    Log.d(TAG, "Attempted URL via DM Leak : " + uri.toString());
                    XposedHelpers.setObjectField(param.thisObject, "mUri", Uri.parse("http://localhost/dummy.txt"));
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity().getApplicationContext(), "Download Manager is attempting to download : " + uri.toString(), Toast.LENGTH_LONG).show());
                    }
                }
            }
        };

        XposedBridge.hookAllMethods(downloadManager, "enqueue", dmSingleResult);
        XposedBridge.hookAllConstructors(downloadManagerRequest, hookDM);

        Class<?> instrumentation = findClass("android.app.Instrumentation", loadPackageParam.classLoader);
        XposedBridge.hookAllMethods(instrumentation, "newActivity", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity mCurrentActivity = (Activity) param.getResult();
                if (mCurrentActivity != null) {
                    setActivity(mCurrentActivity);
                }
            }
        });

    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        Log.d(TAG, "MyPackage: " + MY_APP);
    }
}
