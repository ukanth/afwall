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

package com.kerneladiutor.library.root;

import com.kerneladiutor.library.Tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by willi on 08.02.15.
 */

/**
 * This class is kinda similar to {@link java.io.File}.
 * Only difference is that this class runs as Root.
 */
public class RootFile {

    private final String file;

    public RootFile(String file) {
        this.file = file;
    }

    public String getName() {
        return RootUtils.runCommand("basename '" + file + "'");
    }

    public void mkdir() {
        RootUtils.runCommand("mkdir -p '" + file + "'");
    }

    public void move(String newPath) {
        RootUtils.runCommand("mv -f '" + file + "' '" + newPath + "'");
    }
    public static boolean copy(String src,String newPath,boolean remountAsRw,boolean preserveAttributes) {
        if(remountAsRw) {
            RootUtils.mount(true,newPath);
        }
        if(RootUtils.busyboxInstalled()) {
            if(preserveAttributes) {
                RootUtils.runCommand("busybox cp -fp '" + src + "' '" + newPath + "'");
            } else {
                RootUtils.runCommand("busybox cp -f '" + src + "' '" + newPath + "'");
            }
        } else {
            if(preserveAttributes) {
                RootUtils.runCommand("cp -fp '" + src + "' '" + newPath + "'");
            } else {
                RootUtils.runCommand("cp -f '" + src + "' '" + newPath + "'");
            }
        }
        if(remountAsRw) {
            RootUtils.mount(false,newPath);
        }
        return true;
    }

    public void write(String text, boolean append) {
        String[] textarray = text.split("\\r?\\n");
        RootUtils.runCommand(append ? "echo '" + textarray[0] + "' >> " + file : "echo '" + textarray[0] + "' > " + file);
        if (textarray.length > 1) for (int i = 1; i < textarray.length; i++)
            RootUtils.runCommand("echo '" + textarray[i] + "' >> " + file);
    }

    public boolean delete(boolean deleteDir) {
        if(deleteDir) {
            RootUtils.runCommand("rm -rf '" + file + "'");
            return true;
        } else {
            RootUtils.runCommand("rm -r '" + file + "'");
            return true;
        }
    }

    public static boolean delete(String file, boolean deleteDir) {
        if(deleteDir) {
            RootUtils.runCommand("rm -rf '" + file + "'");
            return true;
        } else {
            RootUtils.runCommand("rm -r '" + file + "'");
            return true;
        }
    }

    public List<String> list() {
        List<String> list = new ArrayList<>();
        String files = RootUtils.runCommand("ls '" + file + "'");
        if (files != null)
            // Make sure the file exists
            for (String file : files.split("\\r?\\n"))
                if (file != null && !file.isEmpty() && Tools.existFile(this.file + "/" + file, true))
                    list.add(file);
        return list;
    }

    public List<RootFile> listFiles() {
        List<RootFile> list = new ArrayList<>();
        String files = RootUtils.runCommand("ls '" + file + "'");
        if (files != null)
            // Make sure the file exists
            for (String file : files.split("\\r?\\n"))
                if (file != null && !file.isEmpty() && Tools.existFile(this.file + "/" + file, true))
                    list.add(new RootFile(this.file + "/" + file));
        return list;
    }

    public float length() {
        try {
            return Float.parseFloat(RootUtils.runCommand("du '" + file + "'").split(file)[0].trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public String getParent() {
        return RootUtils.runCommand("dirname '" + file + "'");
    }

    public boolean isEmpty() {
        return RootUtils.runCommand("find '" + file + "' -mindepth 1 | read || echo false").equals("false");
    }

    public boolean exists() {
        String output = RootUtils.runCommand("[ -e '" + file + "' ] && echo true");
        return output != null && output.contains("true");
    }

    public String readFile() {
        return RootUtils.runCommand("cat '" + file + "'");
    }

    public String toString() {
        return file;
    }

}
