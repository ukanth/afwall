package dev.ukanth.ufirewall.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.topjohnwu.superuser.Shell;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one place AFWall+ writes user-visible files to: backups, log and rule dumps all go to
 * Download/AFWall/, which file managers show and which survives an uninstall. Imports are read
 * back through the system file picker (a content Uri), since after a reinstall the app no longer
 * owns the files it wrote there.
 */
public final class BackupStorage {

    private static final String TAG = "AFWall";

    public static final String DIR_NAME = "AFWall";
    public static final String MIME_JSON = "application/json";
    public static final String MIME_TEXT = "text/plain";

    private static final long MAX_IMPORT_SIZE = 50L * 1024 * 1024;

    // backups written by every earlier version: afwall-backup[-all]-<date>.json and the oldest names
    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile(
            "afwall-backup(-[a-z]+)?-\\d{4}-\\S+\\.json|[a-z]+[_.][a-z]+\\.json");

    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME + "/";

    private BackupStorage() {
    }

    /**
     * Writes a file to Download/AFWall/.
     *
     * @return the location as the user sees it (e.g. "Download/AFWall/x.json"), or null on failure
     */
    @Nullable
    public static String save(Context ctx, String fileName, String mimeType, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try {
            String savedName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? saveToMediaStore(ctx, fileName, mimeType, bytes)
                    : saveToFile(fileName, bytes);
            Log.i(TAG, "Saved " + RELATIVE_PATH + savedName);
            return RELATIVE_PATH + savedName;
        } catch (Exception e) {
            Log.e(TAG, "Unable to save " + fileName + " to " + RELATIVE_PATH, e);
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static String saveToMediaStore(Context ctx, String fileName, String mimeType, byte[] bytes) throws IOException {
        ContentResolver resolver = ctx.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) {
            throw new IOException("MediaStore refused to create " + fileName);
        }
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("Unable to open " + uri);
            }
            out.write(bytes);
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        // MediaStore renames on a clash ("name (1).json"); report the name it actually used
        String saved = queryDisplayName(ctx, uri);
        return saved != null ? saved : fileName;
    }

    private static String saveToFile(String fileName, byte[] bytes) throws IOException {
        File dir = legacyDownloadDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Unable to create " + dir);
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
            out.write(bytes);
        }
        return fileName;
    }

    @SuppressWarnings("deprecation")
    private static File legacyDownloadDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME);
    }

    /**
     * Reads a file picked with the system file picker.
     *
     * @throws IOException when it cannot be read or is larger than the import limit
     */
    public static String read(Context ctx, Uri uri) throws IOException {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Unable to open " + uri);
            }
            return readLimited(in);
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > MAX_IMPORT_SIZE) {
                throw new IOException("Import file is too large (>50MB)");
            }
            out.write(buffer, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * @return the file name of a picked Uri, for messages; falls back to the Uri itself
     */
    public static String displayName(Context ctx, Uri uri) {
        String name = queryDisplayName(ctx, uri);
        return name != null ? name : uri.toString();
    }

    @Nullable
    private static String queryDisplayName(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to query name of " + uri, e);
        }
        return null;
    }

    /**
     * Where the system file picker should open: Download/AFWall/ on the primary storage. Pickers
     * that don't know this location simply open their default one.
     */
    public static Uri pickerInitialUri() {
        return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents",
                "primary:" + RELATIVE_PATH.substring(0, RELATIVE_PATH.length() - 1));
    }

    /**
     * Before 10, writing to Download needs the storage permission; from 10 on it needs none.
     */
    public static boolean needsStoragePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    // ---- one-time copy of backups made by earlier versions ----

    /**
     * Copies backups from the folders earlier versions used (/sdcard/afwall/ before Android 10,
     * the app's private folder under Android/data/ after) to Download/AFWall/. The originals are
     * kept; files already there are skipped. Must run off the main thread; may use root.
     *
     * @return number of backups copied, or -1 if some could not be copied (try again later)
     */
    public static int migrateLegacyBackups(Context ctx) {
        Set<String> existing = existingNames(ctx);
        int copied = 0;
        boolean failed = false;
        for (File dir : legacyDirs(ctx)) {
            List<String> names = listBackups(dir);
            for (String name : names) {
                if (existing.contains(name)) {
                    continue;
                }
                String content = readLegacy(new File(dir, name));
                if (content == null || save(ctx, name, MIME_JSON, content) == null) {
                    failed = true;
                    continue;
                }
                existing.add(name);
                copied++;
            }
        }
        Log.i(TAG, "Copied " + copied + " old backups to " + RELATIVE_PATH + (failed ? " (some failed)" : ""));
        return failed ? -1 : copied;
    }

    @SuppressWarnings("deprecation")
    private static List<File> legacyDirs(Context ctx) {
        List<File> dirs = new ArrayList<>();
        File sdcard = new File(Environment.getExternalStorageDirectory(), "afwall");
        dirs.add(sdcard);
        File files = ctx.getExternalFilesDir(null);
        File documents = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        // the old import migration copied /sdcard/afwall/ into these as an "afwall" subfolder
        for (File dir : new File[]{documents, files}) {
            if (dir != null) {
                dirs.add(dir);
                dirs.add(new File(dir, "afwall"));
            }
        }
        return dirs;
    }

    private static List<String> listBackups(File dir) {
        List<String> names = new ArrayList<>();
        String[] list = dir.list();
        // from Android 11 on, /sdcard/afwall/ is unreadable or lists empty without root
        if ((list == null || list.length == 0) && canUseRoot()) {
            Shell.Result result = Shell.cmd("ls " + quote(dir.getAbsolutePath()) + " 2>/dev/null").exec();
            list = result.isSuccess() ? result.getOut().toArray(new String[0]) : null;
        }
        if (list != null) {
            for (String name : list) {
                if (BACKUP_FILE_PATTERN.matcher(name).matches()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    @Nullable
    private static String readLegacy(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return readLimited(in);
        } catch (IOException e) {
            if (!canUseRoot()) {
                Log.w(TAG, "Unable to read old backup " + file, e);
                return null;
            }
        }
        Shell.Result result = Shell.cmd("cat " + quote(file.getAbsolutePath())).exec();
        return result.isSuccess() ? TextUtils.join("\n", result.getOut()) : null;
    }

    private static Set<String> existingNames(Context ctx) {
        Set<String> names = new HashSet<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // only lists files this install created; anything else gets a "(1)" copy at worst
            try (Cursor c = ctx.getContentResolver().query(
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    new String[]{MediaStore.Downloads.DISPLAY_NAME},
                    MediaStore.Downloads.RELATIVE_PATH + "=?", new String[]{RELATIVE_PATH}, null)) {
                while (c != null && c.moveToNext()) {
                    names.add(c.getString(0));
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to list " + RELATIVE_PATH, e);
            }
        } else {
            String[] list = legacyDownloadDir().list();
            if (list != null) {
                names.addAll(Arrays.asList(list));
            }
        }
        return names;
    }

    /**
     * Root, without ever prompting for it: only once the user has granted it to AFWall+. Waits for
     * the shell if it isn't up yet, so call off the main thread.
     */
    private static boolean canUseRoot() {
        Boolean granted = Shell.isAppGrantedRoot();
        if (granted == null && G.hasRoot()) {
            granted = Shell.getShell().isRoot();
        }
        return Boolean.TRUE.equals(granted);
    }

    private static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
