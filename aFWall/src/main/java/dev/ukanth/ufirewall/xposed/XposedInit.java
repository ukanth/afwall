package dev.ukanth.ufirewall.xposed;

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.net.UnknownHostException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.MainActivity;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;


/**
 * Created by ukanth on 6/7/16.
 */
public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static final String MY_PACKAGE_NAME = XposedInit.class.getPackage().getName();
    private static final String UNABLE_TO_RESOLVE_HOST = "Unable to resolve host";

    public static String MODULE_PATH = null;
    public static final String TAG = "AFWallXPosed";
    private static Context context;
    private XSharedPreferences prefs;
    private XSharedPreferences pPrefs;


    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        //final String packageName = loadPackageParam.packageName;
        try {

            if (context == null) {
                Object activityThread = callStaticMethod(
                        findClass("android.app.ActivityThread", null), "currentActivityThread");
                context = (Context) callMethod(activityThread, "getSystemContext");
            }
            if(prefs== null) {
                prefs = new XSharedPreferences(MainActivity.class.getPackage().getName());
                prefs.makeWorldReadable();

            }

            //enable when through settings
            interceptDownloadManager(loadPackageParam);

            //interceptNet(loadPackageParam);
        } catch (XposedHelpers.ClassNotFoundError e) {

        }
    }

    private void interceptDownloadManager(XC_LoadPackage.LoadPackageParam loadPackageParam) {

        final String packageName = loadPackageParam.packageName;

        Class<?> downloadManager = findClass("android.app.DownloadManager", loadPackageParam.classLoader);
        XC_MethodHook dmSingleResult = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(context == null) {
                    Object activityThread = callStaticMethod(
                            findClass("android.app.ActivityThread", null), "currentActivityThread");
                    context = (Context) callMethod(activityThread, "getSystemContext");
                }
                if (context != null) {
                    prefs = new XSharedPreferences(MainActivity.class.getPackage().getName());
                    prefs.makeWorldReadable();
                    prefs.reload();

                    String profileName = Api.DEFAULT_PREFS_NAME;
                    if(prefs.getBoolean("enableMultiProfile", false)) {
                        profileName = prefs.getString("storedProfile", "AFWallPrefs");
                    }
                    Api.PREFS_NAME = profileName;

                    Log.d(TAG, "beforeHookedMethod Loading Profile: " + profileName);
                    pPrefs =  new XSharedPreferences(MainActivity.class.getPackage().getName(),profileName);
                    pPrefs.reload();
                    pPrefs.makeWorldReadable();

                    boolean isXposedEnabled = prefs.getBoolean("fixDownloadManagerLeak", false);
                    Log.d(TAG, "beforeHookedMethod AFWall isEnabled: " + isXposedEnabled);
                    if(isXposedEnabled) {
                        PackageInfo packageInfo = Api.getPackageDetails(context, packageName);
                        Log.d(TAG, "beforeHookedMethod Calling Package: " + packageInfo.packageName);
                        boolean isAppAllowed = Api.isAppAllowed(context,packageInfo,pPrefs);
                        Log.d(TAG, "beforeHookedMethod isAppAllowed : " + isAppAllowed);
                        if(!isAppAllowed){
                            Log.d(TAG, "beforeHookedMethod Calling Package: " + packageInfo.packageName);
                            param.setResult(new Object());
                            param.setThrowable(new UnknownHostException(UNABLE_TO_RESOLVE_HOST));
                            Toast.makeText(context,"Application doesn't have access to network",Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "beforeHookedMethod Params: " + param);
                            if (param != null && param.args != null && param.args.length > 0) {
                                DownloadManager.Request request = (DownloadManager.Request) param.args[0];
                                request.setDestinationUri(Uri.parse("127.0.0.1"));
                            }
                         }
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(context == null) {
                    Object activityThread = callStaticMethod(
                            findClass("android.app.ActivityThread", null), "currentActivityThread");
                    context = (Context) callMethod(activityThread, "getSystemContext");
                }
                if (context != null) {
                    prefs = new XSharedPreferences(MainActivity.class.getPackage().getName());
                    prefs.makeWorldReadable();
                    prefs.reload();

                    String profileName = Api.DEFAULT_PREFS_NAME;
                    if(prefs.getBoolean("enableMultiProfile", false)) {
                        profileName = prefs.getString("storedProfile", "AFWallPrefs");
                    }
                    Api.PREFS_NAME = profileName;
                    pPrefs =  new XSharedPreferences(MainActivity.class.getPackage().getName(),profileName);
                    pPrefs.reload();
                    pPrefs.makeWorldReadable();

                    boolean isXposedEnabled = prefs.getBoolean("fixDownloadManagerLeak", false);
                    Log.d(TAG, "afterHookedMethod AFWall isEnabled: " + isXposedEnabled);
                    if(isXposedEnabled) {
                        PackageInfo packageInfo = Api.getPackageDetails(context, packageName);
                        Log.d(TAG, "Calling Package: " + packageInfo.packageName);
                        boolean isAppAllowed = Api.isAppAllowed(context,packageInfo,pPrefs);
                        Log.d(TAG, "isAppAllowed : " + isAppAllowed);
                        if(!isAppAllowed){
                            Toast.makeText(context,"Application doesn't have access to network",Toast.LENGTH_SHORT).show();
                            if (param != null && param.args != null && param.args.length > 0) {
                                param.setResult(new Object());
                                param.setThrowable(new UnknownHostException(UNABLE_TO_RESOLVE_HOST));
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "AFWall Context: " + context);
                }
            }
        };
        XposedBridge.hookAllMethods(downloadManager, "enqueue", dmSingleResult);
        XposedBridge.hookAllMethods(downloadManager, "query", dmSingleResult);
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
            context = (Context) callMethod(activityThread, "getSystemContext");
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
    }

}
