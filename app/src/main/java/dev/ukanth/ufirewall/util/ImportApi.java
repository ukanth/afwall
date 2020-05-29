package dev.ukanth.ufirewall.util;

import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Environment;

import com.stericson.roottools.RootTools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.log.Log;

public class ImportApi {


    private static File getDataDir(Context ctx, String packageName) {
        try {
            PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(packageName, 0);
            if (packageInfo == null) return null;
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            if (applicationInfo == null) return null;
            if (applicationInfo.dataDir == null) return null;
            return new File(applicationInfo.dataDir);
        } catch (NameNotFoundException ex) {
            return null;
        }
    }

    private static class LoadTask extends AsyncTask<Void, Void, Boolean> {
        private final Context ctx;
        boolean[] result = {false};

        private LoadTask(Context context) {
            this.ctx = context;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/afwall/");
            dir.mkdirs();
            File shared_prefs = new File(getDataDir(ctx, "com.googlecode.droidwall.free") + File.separator + "shared_prefs" + File.separator + "DroidWallPrefs.xml");
            File file = new File(dir, "DroidWallPrefs.xml");
            RootTools.copyFile(shared_prefs.getPath(), dir.getPath(), true, false);
            final Editor prefEdit = ctx.getSharedPreferences(Api.PREFS_NAME, Context.MODE_PRIVATE).edit();
            // write the logic to read the copied xml
            String wifi = null, g = null;
            try {
                String xmlStr = readTextFile(new FileInputStream(file));
                Document doc = XMLfromString(xmlStr);
                NodeList nodes = doc.getElementsByTagName("string");

                for (int i = 0; i < nodes.getLength(); i++) {
                    Element e = (Element) nodes.item(i);
                    if (e.getAttribute("name").equals("AllowedUidsWifi")) {
                        wifi = getElementValue(e);
                        Log.d("AllowedUidsWifi", wifi);
                    } else if (e.getAttribute("name").equals("AllowedUids3G")) {
                        g = getElementValue(e);
                        Log.d("AllowedUids3G", g);
                    }
                }

            } catch (FileNotFoundException e) {
            }

            if (wifi != null) {
                prefEdit.putString(Api.PREF_WIFI_PKG, getPackageListFromUID(ctx, wifi));
                prefEdit.putString(Api.PREF_WIFI_PKG_UIDS, wifi);
            }
            if (g != null) {
                prefEdit.putString(Api.PREF_3G_PKG, getPackageListFromUID(ctx, g));
                prefEdit.putString(Api.PREF_3G_PKG_UIDS, g);
            }
            prefEdit.commit();
            result[0] = true;
            return result[0];
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // result holds what you return from doInBackground
        }
    }

    public static boolean loadSharedPreferencesFromDroidWall(Context ctx) {
        try {
            LoadTask task = new LoadTask(ctx);
            task.execute();
            return task.result[0];
        } catch (Exception e) {
        }
        return false;
    }

    private static String getElementValue(Node elem) {
        Node kid;
        if (elem != null) {
            if (elem.hasChildNodes()) {
                for (kid = elem.getFirstChild(); kid != null; kid = kid.getNextSibling()) {
                    if (kid.getNodeType() == Node.TEXT_NODE) {
                        return kid.getNodeValue();
                    }
                }
            }
        }
        return "";
    }

    private static String readTextFile(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final byte[] buf = new byte[4096];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {

        }
        return outputStream.toString();
    }

    private static Document XMLfromString(String v) {
        Document doc = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(v));
            doc = db.parse(is);
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        }
        return doc;

    }

    private static String getPackageListFromUID(Context ctx, final String uids) {
        final PackageManager pm = ctx.getPackageManager();
        final StringBuilder pkg = new StringBuilder();
        final StringTokenizer tok = new StringTokenizer(uids, "|");
        while (tok.hasMoreTokens()) {
            final int uid = Integer.parseInt(tok.nextToken());
            String[] pack = pm.getPackagesForUid(uid);
            if (pack != null && pack.length == 1) {
                pkg.append(pack[0] + "|");
            }
            if (uid == 1000) {
                pkg.append("android|");
            }
        }
        return pkg.toString();
    }
}
