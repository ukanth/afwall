/*
 * Copyright (C) 2015 Willi Ye
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kerneladiutor.library;

import android.os.Environment;
import android.util.Log;

import com.kerneladiutor.library.root.RootFile;
import com.kerneladiutor.library.root.RootUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by willi on 11.08.15.
 */

/**
 * Just a collection of functions which might be useful
 */
public class Tools {

    /**
     * Debugging TAG
     */
    public final static String TAG = "KernelAdiutorPlugin";

    /**
     * Get path of external storage
     *
     * @return path of external storage
     */
    public static String getExternalStorage() {
        String path = RootUtils.runCommand("echo ${SECONDARY_STORAGE%%:*}");
        return path.contains("/") ? path : null;
    }

    /**
     * Get path of internal storage
     *
     * @return path of internal storage
     */
    public static String getInternalStorage() {
        String dataPath = existFile("/data/media/0", true) ? "/data/media/0" : "/data/media";
        if (!new RootFile(dataPath).isEmpty()) return dataPath;
        if (existFile("/sdcard", true)) return "/sdcard";
        return Environment.getExternalStorageDirectory().getPath();
    }

    /**
     * Check if a file exists
     *
     * @param file   path to file
     * @param asRoot check as root
     * @return true = file exists, false = file does not exist
     */
    public static boolean existFile(String file, boolean asRoot) {
        if (asRoot) return new RootFile(file).exists();
        return new File(file).exists();
    }

    /**
     * Write a string to any file
     *
     * @param path   path to file
     * @param text   your text
     * @param append append your text to file
     * @param asRoot write as root
     */
    public static void writeFile(String path, String text, boolean append, boolean asRoot) {
        if (asRoot) {
            new RootFile(path).write(text, append);
            return;
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(path, append);
            writer.write(text);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write " + path);
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Read any file from storage
     *
     * @param file   path to the file
     * @param asRoot read as root
     * @return content of file
     */
    public static String readFile(String file, boolean asRoot) {
        if (asRoot) return new RootFile(file).readFile();

        StringBuilder s = null;
        FileReader fileReader = null;
        BufferedReader buf = null;
        try {
            fileReader = new FileReader(file);
            buf = new BufferedReader(fileReader);

            String line;
            s = new StringBuilder();
            while ((line = buf.readLine()) != null) s.append(line).append("\n");
        } catch (FileNotFoundException ignored) {
            Log.e(TAG, "File does not exist " + file);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + file);
        } finally {
            try {
                if (fileReader != null) fileReader.close();
                if (buf != null) buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return s == null ? null : s.toString().trim();
    }

}
