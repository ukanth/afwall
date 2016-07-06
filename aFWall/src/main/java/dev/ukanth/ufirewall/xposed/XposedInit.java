package dev.ukanth.ufirewall.xposed;

import android.content.Context;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import dev.ukanth.ufirewall.Api;

import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.callMethod;


/**
 * Created by ukanth on 6/7/16.
 */
public class XposedInit implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static final String MY_PACKAGE_NAME = XposedInit.class.getPackage().getName();
    public static String MODULE_PATH = null;
    public static final String TAG = "AFWallXPosed";
    private static Context context;


    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        final String packageName = loadPackageParam.packageName;
        try {

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

            if(context == null ) {
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
                    if(context != null) {
                        Log.d(TAG, "Calling Package ----> " + Api.getPackageDetails(context,packageName));
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


        } catch (XposedHelpers.ClassNotFoundError e) {

        }
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }

}
