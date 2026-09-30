package dev.ukanth.ufirewall.util;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Packages that PackageManager doesn't show us (package visibility, apps only in a work profile /
 * Private Space) are listed with root "pm list packages -f -U". Their APK is readable without root,
 * so their label and icon can be read from it.
 */
public final class ApkInfo {

    private ApkInfo() {
    }

    /**
     * One line of "pm list packages -U", with "-f" also the APK path.
     */
    public static final class Line {
        public final String packageName;
        /** null without "-f" */
        public final String apkPath;
        /** the first UID listed (the one of the user asked for with "--user") */
        public final int uid;

        Line(String packageName, String apkPath, int uid) {
            this.packageName = packageName;
            this.apkPath = apkPath;
            this.uid = uid;
        }
    }

    /**
     * Parse "package:com.example uid:10123" or "package:/data/app/.../base.apk=com.example
     * uid:10123,1010123".
     *
     * @return null if the line isn't a package line
     */
    public static Line parse(String line) {
        if (line == null || !line.startsWith("package:")) {
            return null;
        }
        String rest = line.substring("package:".length());
        int uidAt = rest.lastIndexOf(" uid:");
        if (uidAt <= 0) {
            return null;
        }
        String body = rest.substring(0, uidAt).trim();
        String uids = rest.substring(uidAt + " uid:".length()).trim();
        int uid;
        try {
            int comma = uids.indexOf(',');
            uid = Integer.parseInt(comma > 0 ? uids.substring(0, comma) : uids);
        } catch (NumberFormatException e) {
            return null;
        }
        String path = null;
        String pkg = body;
        // a package name has no '=', a path may
        int eq = body.lastIndexOf('=');
        if (body.startsWith("/") && eq > 0) {
            path = body.substring(0, eq);
            pkg = body.substring(eq + 1);
        }
        if (pkg.isEmpty() || pkg.contains(" ") || pkg.contains("/")) {
            return null;
        }
        return new Line(pkg, path, uid);
    }

    /**
     * @return the application of {@code apkPath} for label and icon, with {@code uid}; null if
     * the APK can't be read
     */
    public static ApplicationInfo load(PackageManager pm, String apkPath, int uid) {
        if (pm == null || apkPath == null) {
            return null;
        }
        try {
            PackageInfo info = pm.getPackageArchiveInfo(apkPath, 0);
            if (info == null || info.applicationInfo == null) {
                return null;
            }
            ApplicationInfo app = info.applicationInfo;
            // needed to load resources (label, icon) from an APK that isn't "installed" for us
            app.sourceDir = apkPath;
            app.publicSourceDir = apkPath;
            app.uid = uid;
            if (!apkPath.startsWith("/data/")) {
                app.flags |= ApplicationInfo.FLAG_SYSTEM; // preinstalled
            }
            return app;
        } catch (Exception e) {
            return null;
        }
    }
}
