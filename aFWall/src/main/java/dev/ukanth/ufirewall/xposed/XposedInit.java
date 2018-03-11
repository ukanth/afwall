package dev.ukanth.ufirewall.xposed;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.widget.Toast;

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

    public static String MODULE_PATH = null;
    public static final String TAG = "AFWallXPosed";
    private static Context context;
    private XSharedPreferences prefs;
    private SharedPreferences pPrefs;
    private String profileName = Api.PREFS_NAME;

    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    private Activity activity;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        try {
            //Log.i(TAG,"Looking for AFWall: " +  loadPackageParam.packageName);
            if (loadPackageParam.packageName.equals(MY_APP)) {
                Log.i(TAG, "Matched Package and now hooking: " + loadPackageParam.packageName);
                reloadPreference();
                interceptAFWall(loadPackageParam);
                //hide lockscreen notification
                //hookLockScreen(loadPackageParam);
            }
            interceptDownloadManager(loadPackageParam);
        } catch (XposedHelpers.ClassNotFoundError e) {
            Log.d(TAG, e.getLocalizedMessage());
        }
    }

   /* private void hookLockScreen(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam.packageName.equals("com.android.systemui")) {

            XC_MethodHook xNotificationHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    StatusBarNotification notification = (StatusBarNotification) param.args[0];
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && notification.getPackageName().equals(MY_APP)) {
                        reloadPreference();
                        if(prefs.getBoolean("lockScreenNotification", false)) {
                            if (param.getResult() != Boolean.valueOf(false)) {
                                param.setResult(Boolean.valueOf(false));
                            }
                        }
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                }
            };
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.BaseStatusBar", loadPackageParam.classLoader, "shouldShowOnKeyguard", new Object[]{StatusBarNotification.class, xNotificationHook});
        }
    }*/

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
                //context = (Context) callMethod(activityThread, "getSystemContext");
                context = (Context) AndroidAppHelper.currentApplication();
            }
            if (prefs == null) {
                prefs = new XSharedPreferences(MY_APP);
                prefs.makeWorldReadable();
                prefs.reload();
                if (prefs.getBoolean("enableMultiProfile", false)) {
                    profileName = prefs.getString("storedProfile", "AFWallPrefs");
                }
                Log.d(TAG, "Loading Profile: " + profileName);
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


    private void interceptDownloadManager(XC_LoadPackage.LoadPackageParam loadPackageParam) throws NoSuchMethodException {
        final ApplicationInfo applicationInfo = loadPackageParam.appInfo;
        Class<?> downloadManager = findClass("android.app.DownloadManager", loadPackageParam.classLoader);
        Class<?> downloadManagerRequest = findClass("android.app.DownloadManager.Request", loadPackageParam.classLoader);

        XC_MethodHook dmSingleResult = new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadPreference();
                final boolean isXposedEnabled = prefs.getBoolean("fixDownloadManagerLeak", false);
                Log.i(TAG, "isXposedEnabled: " + isXposedEnabled);
                if (isXposedEnabled) {
                    final boolean isAppAllowed = Api.isAppAllowed(context, applicationInfo, pPrefs);
                    Log.i(TAG, "DM Calling Application: " + applicationInfo.packageName + ", Allowed: " + isAppAllowed);
                    if (!isAppAllowed) {
                        //showNotification(context,"Package: " + pPrefs.getString("cache.label." + applicationInfo.packageName,applicationInfo.packageName) + " trying to use download manager has been blocked successfully");
                        param.setResult(0);
                        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        dm.remove(0);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity().getApplicationContext(), "AFWall+ denied access to Download Manager for package(uid) : " + applicationInfo.packageName + "(" + applicationInfo.uid + ")", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }
            }
        };

        XC_MethodHook hookDM = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                reloadPreference();
                final boolean isXposedEnabled = prefs.getBoolean("fixDownloadManagerLeak", false);
                Log.i(TAG, "isXposedEnabled: " + isXposedEnabled);
                if (isXposedEnabled) {
                    final boolean isAppAllowed = Api.isAppAllowed(context, applicationInfo, pPrefs);
                    Log.i(TAG, "DM Calling Application: " + applicationInfo.packageName + ", Allowed: " + isAppAllowed);
                    if (!isAppAllowed) {
                        final Uri uri = (Uri) param.args[0];
                        Log.i(TAG, "Attempted URL via DM Leak : " + uri.toString());
                        XposedHelpers.setObjectField(param.thisObject, "mUri", Uri.parse("http://localhost/dummy.txt"));
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity().getApplicationContext(), "Download Manager is attempting to download : " + uri.toString(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
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

    private void interceptNet(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        final String packageName = loadPackageParam.packageName;
         /* Reference taken from XPrivacy */
        // public static InetAddress[] getAllByName(String host)
        // public static InetAddress[] getAllByNameOnNet(String host, int netId)
        // public static InetAddress getByAddress(byte[] ipAddress)
        // public static InetAddress getByAddress(String hostName, byte[] ipAddress)
        // public static InetAddress getByName(String host)
        // public static InetAddress getByNameOnNet(String host, int netId)
        // libcore/luni/src/main/java/java/net/InetAddress.java
        // http://developer.android.com/reference/java/net/InetAddress.html
        // http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.0_r1/android/net/Network.java

        if (context == null) {
            Object activityThread = callStaticMethod(
                    findClass("android.app.ActivityThread", null), "currentActivityThread");
            //context = (Context) callMethod(activityThread, "getSystemContext");
            context = (Context) AndroidAppHelper.currentApplication();
        }

        Class<?> inetAddress = findClass("java.net.InetAddress", loadPackageParam.classLoader);
        Class<?> inetSocketAddress = XposedHelpers.findClass(" java.net.InetSocketAddress", loadPackageParam.classLoader);
        final Class<?> socket = XposedHelpers.findClass("java.net.Socket", loadPackageParam.classLoader);

            /*if(context != null) {
                Log.d(TAG, "Calling Package ----> " + Api.getPackageDetails(context,packageName));
            }*/

        XposedBridge.hookAllConstructors(socket, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {


            }
        });


        XC_MethodHook inetAddrHookSingleResult = new XC_MethodHook() {


            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (context != null) {
                    Log.d(TAG, "Calling Package ----> " + Api.getPackageDetails(context, packageName));
                }
                if (param != null && param.args != null && param.args.length > 0) {
                    for (Object obj : param.args) {
                        if (obj != null) {
                            Log.d(TAG, "XPOSED Param Class ->" + (obj.getClass().getSimpleName()));
                            Log.d(TAG, "XPOSED Param Value->" + obj.toString());
                        }
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                   /* String host = (String) param.args[0];

                    if(Main.patterns.contains(host)) {
                        Log.d("inet_after_host", host);
                        param.setResult(new Object());
                        param.setThrowable(new UnknownHostException(UNABLE_TO_RESOLVE_HOST));
                    }*/
            }
        };

        XposedBridge.hookAllMethods(inetAddress, "getByName", inetAddrHookSingleResult);
        XposedBridge.hookAllMethods(inetAddress, "getByAddress", inetAddrHookSingleResult);
        XposedBridge.hookAllMethods(inetAddress, "getAllByName", inetAddrHookSingleResult);
        XposedBridge.hookAllMethods(inetAddress, "getAllByNameOnNet", inetAddrHookSingleResult);
        XposedBridge.hookAllMethods(inetAddress, "getByNameOnNet", inetAddrHookSingleResult);


        XposedBridge.hookAllMethods(inetSocketAddress, "createUnresolved", inetAddrHookSingleResult);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        Log.d(TAG, "MyPackage: " + MY_APP);
    }
}
