/* 
 * This file is part of the RootTools Project: http://code.google.com/p/roottools/
 *  
 * Copyright (c) 2012 Stephen Erickson, Chris Ravenscroft, Dominik Schuermann, Adam Shanks
 *  
 * This code is dual-licensed under the terms of the Apache License Version 2.0 and
 * the terms of the General Public License (GPL) Version 2.
 * You may use this code according to either of these licenses as is most appropriate
 * for your project on a case-by-case basis.
 * 
 * The terms of each license can be found in the root directory of this project's repository as well as at:
 * 
 * * http://www.apache.org/licenses/LICENSE-2.0
 * * http://www.gnu.org/licenses/gpl-2.0.txt
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under these Licenses is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See each License for the specific language governing permissions and
 * limitations under that License.
 */

package com.stericson.roottools.internal;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.stericson.rootshell.RootShell;
import com.stericson.rootshell.execution.Command;
import com.stericson.rootshell.execution.Shell;
import com.stericson.roottools.Constants;
import com.stericson.roottools.RootTools;
import com.stericson.roottools.containers.Mount;
import com.stericson.roottools.containers.Permissions;
import com.stericson.roottools.containers.Symlink;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;

public final class RootToolsInternalMethods {

    // --------------------
    // # Internal methods #
    // --------------------

    protected RootToolsInternalMethods() {
    }

    public static void getInstance() {
        //this will allow RootTools to be the only one to get an instance of this class.
        RootTools.setRim(new RootToolsInternalMethods());
    }

    public ArrayList<Symlink> getSymLinks() throws IOException {

        LineNumberReader lnr = null;
        FileReader fr = null;

        try {

            fr = new FileReader("/data/local/symlinks.txt");
            lnr = new LineNumberReader(fr);

            String line;
            ArrayList<Symlink> symlink = new ArrayList<Symlink>();

            while ((line = lnr.readLine()) != null) {

                RootTools.log(line);

                String[] fields = line.split(" ");
                symlink.add(new Symlink(new File(fields[fields.length - 3]), // file
                        new File(fields[fields.length - 1]) // SymlinkPath
                ));
            }
            return symlink;
        } finally {
            try {
                fr.close();
            } catch (Exception e) {
            }

            try {
                lnr.close();
            } catch (Exception e) {
            }
        }
    }

    public Permissions getPermissions(String line) {

        String[] lineArray = line.split(" ");
        String rawPermissions = lineArray[0];

        if (rawPermissions.length() == 10
                && (rawPermissions.charAt(0) == '-'
                || rawPermissions.charAt(0) == 'd' || rawPermissions
                .charAt(0) == 'l')
                && (rawPermissions.charAt(1) == '-' || rawPermissions.charAt(1) == 'r')
                && (rawPermissions.charAt(2) == '-' || rawPermissions.charAt(2) == 'w')) {
            RootTools.log(rawPermissions);

            Permissions permissions = new Permissions();

            permissions.setType(rawPermissions.substring(0, 1));

            RootTools.log(permissions.getType());

            permissions.setUserPermissions(rawPermissions.substring(1, 4));

            RootTools.log(permissions.getUserPermissions());

            permissions.setGroupPermissions(rawPermissions.substring(4, 7));

            RootTools.log(permissions.getGroupPermissions());

            permissions.setOtherPermissions(rawPermissions.substring(7, 10));

            RootTools.log(permissions.getOtherPermissions());

            StringBuilder finalPermissions = new StringBuilder();
            finalPermissions.append(parseSpecialPermissions(rawPermissions));
            finalPermissions.append(parsePermissions(permissions.getUserPermissions()));
            finalPermissions.append(parsePermissions(permissions.getGroupPermissions()));
            finalPermissions.append(parsePermissions(permissions.getOtherPermissions()));

            permissions.setPermissions(Integer.parseInt(finalPermissions.toString()));

            return permissions;
        }

        return null;
    }

    public int parsePermissions(String permission) {
        permission = permission.toLowerCase(Locale.US);
        int tmp;
        if (permission.charAt(0) == 'r') {
            tmp = 4;
        } else {
            tmp = 0;
        }

        RootTools.log("permission " + tmp);
        RootTools.log("character " + permission.charAt(0));

        if (permission.charAt(1) == 'w') {
            tmp += 2;
        } else {
            tmp += 0;
        }

        RootTools.log("permission " + tmp);
        RootTools.log("character " + permission.charAt(1));

        if (permission.charAt(2) == 'x' || permission.charAt(2) == 's'
                || permission.charAt(2) == 't') {
            tmp += 1;
        } else {
            tmp += 0;
        }

        RootTools.log("permission " + tmp);
        RootTools.log("character " + permission.charAt(2));

        return tmp;
    }

    public int parseSpecialPermissions(String permission) {
        int tmp = 0;
        if (permission.charAt(2) == 's') {
            tmp += 4;
        }

        if (permission.charAt(5) == 's') {
            tmp += 2;
        }

        if (permission.charAt(8) == 't') {
            tmp += 1;
        }

        RootTools.log("special permissions " + tmp);

        return tmp;
    }

    /**
     * Copys a file to a destination. Because cp is not available on all android devices, we have a
     * fallback on the cat command
     *
     * @param source                 example: /data/data/org.adaway/files/hosts
     * @param destination            example: /system/etc/hosts
     * @param remountAsRw            remounts the destination as read/write before writing to it
     * @param preserveFileAttributes tries to copy file attributes from source to destination, if only cat is available
     *                               only permissions are preserved
     * @return true if it was successfully copied
     */
    public boolean copyFile(String source, String destination, boolean remountAsRw,
                            boolean preserveFileAttributes) {

        Command command = null;
        boolean result = true;

        try {
            // mount destination as rw before writing to it
            if (remountAsRw) {
                RootTools.remount(destination, "RW");
            }

            // if cp is available and has appropriate permissions
            if (checkUtil("cp")) {
                RootTools.log("cp command is available!");

                if (preserveFileAttributes) {
                    command = new Command(0, false, "cp -fp " + source + " " + destination);
                    Shell.startRootShell().add(command);
                    commandWait(Shell.startRootShell(), command);

                    //ensure that the file was copied, an exitcode of zero means success
                    result = command.getExitCode() == 0;

                } else {
                    command = new Command(0, false, "cp -f " + source + " " + destination);
                    Shell.startRootShell().add(command);
                    commandWait(Shell.startRootShell(), command);

                    //ensure that the file was copied, an exitcode of zero means success
                    result = command.getExitCode() == 0;

                }
            } else {
                if (checkUtil("busybox") && hasUtil("cp", "busybox")) {
                    RootTools.log("busybox cp command is available!");

                    if (preserveFileAttributes) {
                        command = new Command(0, false, "busybox cp -fp " + source + " " + destination);
                        Shell.startRootShell().add(command);
                        commandWait(Shell.startRootShell(), command);

                    } else {
                        command = new Command(0, false, "busybox cp -f " + source + " " + destination);
                        Shell.startRootShell().add(command);
                        commandWait(Shell.startRootShell(), command);

                    }
                } else { // if cp is not available use cat
                    // if cat is available and has appropriate permissions
                    if (checkUtil("cat")) {
                        RootTools.log("cp is not available, use cat!");

                        int filePermission = -1;
                        if (preserveFileAttributes) {
                            // get permissions of source before overwriting
                            Permissions permissions = getFilePermissionsSymlinks(source);
                            filePermission = permissions.getPermissions();
                        }

                        // copy with cat
                        command = new Command(0, false, "cat " + source + " > " + destination);
                        Shell.startRootShell().add(command);
                        commandWait(Shell.startRootShell(), command);

                        if (preserveFileAttributes) {
                            // set premissions of source to destination
                            command = new Command(0, false, "chmod " + filePermission + " " + destination);
                            Shell.startRootShell().add(command);
                            commandWait(Shell.startRootShell(), command);
                        }
                    } else {
                        result = false;
                    }
                }
            }

            // mount destination back to ro
            if (remountAsRw) {
                RootTools.remount(destination, "RO");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        }

        if (command != null) {
            //ensure that the file was copied, an exitcode of zero means success
            result = command.getExitCode() == 0;
        }

        return result;
    }

    /**
     * This will check a given binary, determine if it exists and determine that
     * it has either the permissions 755, 775, or 777.
     *
     * @param util Name of the utility to check.
     * @return boolean to indicate whether the binary is installed and has
     * appropriate permissions.
     */
    public boolean checkUtil(String util) {
        List<String> foundPaths = RootShell.findBinary(util);
        if (foundPaths.size() > 0) {

            for (String path : foundPaths) {
                Permissions permissions = RootTools
                        .getFilePermissionsSymlinks(path + "/" + util);

                if (permissions != null) {
                    String permission;

                    if (Integer.toString(permissions.getPermissions()).length() > 3) {
                        permission = Integer.toString(permissions.getPermissions()).substring(1);
                    } else {
                        permission = Integer.toString(permissions.getPermissions());
                    }

                    if (permission.equals("755") || permission.equals("777")
                            || permission.equals("775")) {
                        RootTools.utilPath = path + "/" + util;
                        return true;
                    }
                }
            }
        }

        return false;

    }

    /**
     * Deletes a file or directory
     *
     * @param target      example: /data/data/org.adaway/files/hosts
     * @param remountAsRw remounts the destination as read/write before writing to it
     * @return true if it was successfully deleted
     */
    public boolean deleteFileOrDirectory(String target, boolean remountAsRw) {
        boolean result = true;

        try {
            // mount destination as rw before writing to it
            if (remountAsRw) {
                RootTools.remount(target, "RW");
            }

            if (hasUtil("rm", "toolbox")) {
                RootTools.log("rm command is available!");

                Command command = new Command(0, false, "rm -r " + target);
                Shell.startRootShell().add(command);
                commandWait(Shell.startRootShell(), command);

                if (command.getExitCode() != 0) {
                    RootTools.log("target not exist or unable to delete file");
                    result = false;
                }
            } else {
                if (checkUtil("busybox") && hasUtil("rm", "busybox")) {
                    RootTools.log("busybox rm command is available!");

                    Command command = new Command(0, false, "busybox rm -rf " + target);
                    Shell.startRootShell().add(command);
                    commandWait(Shell.startRootShell(), command);

                    if (command.getExitCode() != 0) {
                        RootTools.log("target not exist or unable to delete file");
                        result = false;
                    }
                }
            }

            // mount destination back to ro
            if (remountAsRw) {
                RootTools.remount(target, "RO");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        }

        return result;
    }

    /**
     * This will try and fix a given binary. (This is for Busybox applets or Toolbox applets) By
     * "fix", I mean it will try and symlink the binary from either toolbox or Busybox and fix the
     * permissions if the permissions are not correct.
     *
     * @param util     Name of the utility to fix.
     * @param utilPath path to the toolbox that provides ln, rm, and chmod. This can be a blank string, a
     *                 path to a binary that will provide these, or you can use
     *                 RootTools.getWorkingToolbox()
     */
    public void fixUtil(String util, String utilPath) {
        try {
            RootTools.remount("/system", "rw");

            List<String> foundPaths = RootShell.findBinary(util);

            if (foundPaths.size() > 0) {
                for (String path : foundPaths) {
                    Command command = new Command(0, false, utilPath + " rm " + path + "/" + util);
                    RootShell.getShell(true).add(command);
                    commandWait(RootShell.getShell(true), command);

                }

                Command command = new Command(0, false, utilPath + " ln -s " + utilPath + " /system/bin/" + util, utilPath + " chmod 0755 /system/bin/" + util);
                RootShell.getShell(true).add(command);
                commandWait(RootShell.getShell(true), command);

            }

            RootTools.remount("/system", "ro");
        } catch (Exception e) {
        }
    }

    /**
     * This will check an array of binaries, determine if they exist and determine that it has
     * either the permissions 755, 775, or 777. If an applet is not setup correctly it will try and
     * fix it. (This is for Busybox applets or Toolbox applets)
     *
     * @param utils Name of the utility to check.
     * @return boolean to indicate whether the operation completed. Note that this is not indicative
     * of whether the problem was fixed, just that the method did not encounter any
     * exceptions.
     * @throws Exception if the operation cannot be completed.
     */
    public boolean fixUtils(String[] utils) throws Exception {

        for (String util : utils) {
            if (!checkUtil(util)) {
                if (checkUtil("busybox")) {
                    if (hasUtil(util, "busybox")) {
                        fixUtil(util, RootTools.utilPath);
                    }
                } else {
                    if (checkUtil("toolbox")) {
                        if (hasUtil(util, "toolbox")) {
                            fixUtil(util, RootTools.utilPath);
                        }
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * This will return an List of Strings. Each string represents an applet available from BusyBox.
     * <p/>
     *
     * @param path Path to the busybox binary that you want the list of applets from.
     * @return <code>null</code> If we cannot return the list of applets.
     */
    public List<String> getBusyBoxApplets(String path) throws Exception {

        if (path != null && !path.endsWith("/") && !path.equals("")) {
            path += "/";
        } else if (path == null) {
            //Don't know what the user wants to do...what am I pshycic?
            throw new Exception("Path is null, please specifiy a path");
        }

        final List<String> results = new ArrayList<String>();

        Command command = new Command(Constants.BBA, false, path + "busybox --list") {

            @Override
            public void commandOutput(int id, String line) {
                if (id == Constants.BBA) {
                    if (!line.trim().equals("") && !line.trim().contains("not found") && !line.trim().contains("file busy")) {
                        results.add(line);
                    }
                }

                super.commandOutput(id, line);
            }
        };

        //try without root first...
        RootShell.getShell(false).add(command);
        commandWait(RootShell.getShell(false), command);

        if (results.size() <= 0) {
            //try with root...
            RootShell.getShell(true).add(command);
            commandWait(RootShell.getShell(true), command);
        }

        return results;
    }

    /**
     * @return BusyBox version if found, "" if not found.
     */
    public String getBusyBoxVersion(String path) {

        if (!path.equals("") && !path.endsWith("/")) {
            path += "/";
        }

        InternalVariables.busyboxVersion = "";

        try {
            Command command = new Command(Constants.BBV, false, path + "busybox") {
                @Override
                public void commandOutput(int id, String line) {
                    line = line.trim();

                    boolean foundVersion = false;

                    if (id == Constants.BBV) {
                        RootTools.log("Version Output: " + line);

                        String[] temp = line.split(" ");

                        if (temp.length > 1 && temp[1].contains("v1.") && !foundVersion) {
                            foundVersion = true;
                            InternalVariables.busyboxVersion = temp[1];
                            RootTools.log("Found Version: " + InternalVariables.busyboxVersion);
                        }
                    }

                    super.commandOutput(id, line);
                }
            };

            //try without root first
            RootTools.log("Getting BusyBox Version without root");
            Shell shell = RootTools.getShell(false);
            shell.add(command);
            commandWait(shell, command);

            if (InternalVariables.busyboxVersion.length() <= 0) {
                RootTools.log("Getting BusyBox Version with root");
                Shell rootShell = RootTools.getShell(true);
                //Now look for it...
                rootShell.add(command);
                commandWait(rootShell, command);
            }

        } catch (Exception e) {
            RootTools.log("BusyBox was not found, more information MAY be available with Debugging on.");
            return "";
        }

        return InternalVariables.busyboxVersion;
    }

    /**
     * @return long Size, converted to kilobytes (from xxx or xxxm or xxxk etc.)
     */
    public long getConvertedSpace(String spaceStr) {
        try {
            double multiplier = 1.0;
            char c;
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < spaceStr.length(); i++) {
                c = spaceStr.charAt(i);
                if (!Character.isDigit(c) && c != '.') {
                    if (c == 'm' || c == 'M') {
                        multiplier = 1024.0;
                    } else if (c == 'g' || c == 'G') {
                        multiplier = 1024.0 * 1024.0;
                    }
                    break;
                }
                sb.append(spaceStr.charAt(i));
            }
            return (long) Math.ceil(Double.valueOf(sb.toString()) * multiplier);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * This method will return the inode number of a file. This method is dependent on having a version of
     * ls that supports the -i parameter.
     *
     * @param file path to the file that you wish to return the inode number
     * @return String The inode number for this file or "" if the inode number could not be found.
     */
    public String getInode(String file) {
        try {
            Command command = new Command(Constants.GI, false, "/data/local/ls -i " + file) {

                @Override
                public void commandOutput(int id, String line) {
                    if (id == Constants.GI) {
                        if (!line.trim().equals("") && Character.isDigit(line.trim().substring(0, 1).toCharArray()[0])) {
                            InternalVariables.inode = line.trim().split(" ")[0];
                        }
                    }

                    super.commandOutput(id, line);
                }
            };
            Shell.startRootShell().add(command);
            commandWait(Shell.startRootShell(), command);

            return InternalVariables.inode;
        } catch (Exception ignore) {
            return "";
        }
    }

    public boolean isNativeToolsReady(int nativeToolsId, Context context) {
        RootTools.log("Preparing Native Tools");
        InternalVariables.nativeToolsReady = false;

        Installer installer;
        try {
            installer = new Installer(context);
        } catch (IOException ex) {
            if (RootTools.debugMode) {
                ex.printStackTrace();
            }
            return false;
        }

        if (installer.isBinaryInstalled("nativetools")) {
            InternalVariables.nativeToolsReady = true;
        } else {
            InternalVariables.nativeToolsReady = installer.installBinary(nativeToolsId,
                    "nativetools", "700");
        }
        return InternalVariables.nativeToolsReady;
    }

    /**
     * @param file String that represent the file, including the full path to the
     *             file and its name.
     * @return An instance of the class permissions from which you can get the
     * permissions of the file or if the file could not be found or
     * permissions couldn't be determined then permissions will be null.
     */
    public Permissions getFilePermissionsSymlinks(String file) {
        RootTools.log("Checking permissions for " + file);
        if (RootTools.exists(file)) {
            RootTools.log(file + " was found.");
            try {

                Command command = new Command(
                        Constants.FPS, false, "ls -l " + file,
                        "busybox ls -l " + file,
                        "/system/bin/failsafe/toolbox ls -l " + file,
                        "toolbox ls -l " + file) {
                    @Override
                    public void commandOutput(int id, String line) {
                        if (id == Constants.FPS) {
                            String symlink_final = "";

                            String[] lineArray = line.split(" ");
                            if (lineArray[0].length() != 10) {
                                super.commandOutput(id, line);
                                return;
                            }

                            RootTools.log("Line " + line);

                            try {
                                String[] symlink = line.split(" ");
                                if (symlink[symlink.length - 2].equals("->")) {
                                    RootTools.log("Symlink found.");
                                    symlink_final = symlink[symlink.length - 1];
                                }
                            } catch (Exception e) {
                            }

                            try {
                                InternalVariables.permissions = getPermissions(line);
                                if (InternalVariables.permissions != null) {
                                    InternalVariables.permissions.setSymlink(symlink_final);
                                }
                            } catch (Exception e) {
                                RootTools.log(e.getMessage());
                            }
                        }

                        super.commandOutput(id, line);
                    }
                };
                RootShell.getShell(true).add(command);
                commandWait(RootShell.getShell(true), command);

                return InternalVariables.permissions;

            } catch (Exception e) {
                RootTools.log(e.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * This will return an ArrayList of the class Mount. The class mount contains the following
     * property's: device mountPoint type flags
     * <p/>
     * These will provide you with any information you need to work with the mount points.
     *
     * @return <code>ArrayList<Mount></code> an ArrayList of the class Mount.
     * @throws Exception if we cannot return the mount points.
     */
    public ArrayList<Mount> getMounts() throws Exception {

        Shell shell = RootTools.getShell(true);

        Command cmd = new Command(0,
                false,
                "cat /proc/mounts > /data/local/RootToolsMounts",
                "chmod 0777 /data/local/RootToolsMounts");
        shell.add(cmd);
        this.commandWait(shell, cmd);

        LineNumberReader lnr = null;
        FileReader fr = null;

        try {
            fr = new FileReader("/data/local/RootToolsMounts");
            lnr = new LineNumberReader(fr);
            String line;
            ArrayList<Mount> mounts = new ArrayList<Mount>();
            while ((line = lnr.readLine()) != null) {

                RootTools.log(line);

                String[] fields = line.split(" ");
                mounts.add(new Mount(new File(fields[0]), // device
                        new File(fields[1]), // mountPoint
                        fields[2], // fstype
                        fields[3] // flags
                ));
            }
            InternalVariables.mounts = mounts;

            if (InternalVariables.mounts != null) {
                return InternalVariables.mounts;
            } else {
                throw new Exception();
            }
        } finally {
            try {
                fr.close();
            } catch (Exception e) {
            }

            try {
                lnr.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * This will tell you how the specified mount is mounted. rw, ro, etc...
     * <p/>
     *
     * @param path mount you want to check
     * @return <code>String</code> What the mount is mounted as.
     * @throws Exception if we cannot determine how the mount is mounted.
     */
    public String getMountedAs(String path) throws Exception {
        InternalVariables.mounts = getMounts();
        String mp;
        if (InternalVariables.mounts != null) {
            for (Mount mount : InternalVariables.mounts) {

                mp = mount.getMountPoint().getAbsolutePath();

                if (mp.equals("/")) {
                    if (path.equals("/")) {
                        return (String) mount.getFlags().toArray()[0];
                    } else {
                        continue;
                    }
                }

                if (path.equals(mp) || path.startsWith(mp + "/")) {
                    RootTools.log((String) mount.getFlags().toArray()[0]);
                    return (String) mount.getFlags().toArray()[0];
                }
            }

            throw new Exception();
        } else {
            throw new Exception();
        }
    }

    /**
     * Get the space for a desired partition.
     *
     * @param path The partition to find the space for.
     * @return the amount if space found within the desired partition. If the space was not found
     * then the value is -1
     * @throws TimeoutException
     */
    public long getSpace(String path) {
        InternalVariables.getSpaceFor = path;
        boolean found = false;
        RootTools.log("Looking for Space");
        try {
            final Command command = new Command(Constants.GS, false, "df " + path) {

                @Override
                public void commandOutput(int id, String line) {
                    if (id == Constants.GS) {
                        if (line.contains(InternalVariables.getSpaceFor.trim())) {
                            InternalVariables.space = line.split(" ");
                        }
                    }

                    super.commandOutput(id, line);
                }
            };
            Shell.startRootShell().add(command);
            commandWait(Shell.startRootShell(), command);

        } catch (Exception e) {
        }

        if (InternalVariables.space != null) {
            RootTools.log("First Method");

            for (String spaceSearch : InternalVariables.space) {

                RootTools.log(spaceSearch);

                if (found) {
                    return getConvertedSpace(spaceSearch);
                } else if (spaceSearch.equals("used,")) {
                    found = true;
                }
            }

            // Try this way
            int count = 0, targetCount = 3;

            RootTools.log("Second Method");

            if (InternalVariables.space[0].length() <= 5) {
                targetCount = 2;
            }

            for (String spaceSearch : InternalVariables.space) {

                RootTools.log(spaceSearch);
                if (spaceSearch.length() > 0) {
                    RootTools.log(spaceSearch + ("Valid"));
                    if (count == targetCount) {
                        return getConvertedSpace(spaceSearch);
                    }
                    count++;
                }
            }
        }
        RootTools.log("Returning -1, space could not be determined.");
        return -1;
    }

    /**
     * This will return a String that represent the symlink for a specified file.
     * <p/>
     *
     * @param file file to get the Symlink for. (must have absolute path)
     * @return <code>String</code> a String that represent the symlink for a specified file or an
     * empty string if no symlink exists.
     */
    public String getSymlink(String file) {
        RootTools.log("Looking for Symlink for " + file);

        try {
            final List<String> results = new ArrayList<String>();

            Command command = new Command(Constants.GSYM, false, "ls -l " + file) {

                @Override
                public void commandOutput(int id, String line) {
                    if (id == Constants.GSYM) {
                        if (!line.trim().equals("")) {
                            results.add(line);
                        }
                    }

                    super.commandOutput(id, line);
                }
            };
            Shell.startRootShell().add(command);
            commandWait(Shell.startRootShell(), command);

            String[] symlink = results.get(0).split(" ");
            if (symlink.length > 2 && symlink[symlink.length - 2].equals("->")) {
                RootTools.log("Symlink found.");

                String final_symlink;

                if (!symlink[symlink.length - 1].equals("") && !symlink[symlink.length - 1].contains("/")) {
                    //We assume that we need to get the path for this symlink as it is probably not absolute.
                    List<String> paths = RootShell.findBinary(symlink[symlink.length - 1]);
                    if (paths.size() > 0) {
                        //We return the first found location.
                        final_symlink = paths.get(0) + symlink[symlink.length - 1];
                    } else {
                        //we couldnt find a path, return the symlink by itself.
                        final_symlink = symlink[symlink.length - 1];
                    }
                } else {
                    final_symlink = symlink[symlink.length - 1];
                }

                return final_symlink;
            }
        } catch (Exception e) {
            if (RootTools.debugMode) {
                e.printStackTrace();
            }
        }

        RootTools.log("Symlink not found");
        return "";
    }

    /**
     * This will return an ArrayList of the class Symlink. The class Symlink contains the following
     * property's: path SymplinkPath
     * <p/>
     * These will provide you with any Symlinks in the given path.
     *
     * @param path path to search for Symlinks.
     * @return <code>ArrayList<Symlink></code> an ArrayList of the class Symlink.
     * @throws Exception if we cannot return the Symlinks.
     */
    public ArrayList<Symlink> getSymlinks(String path) throws Exception {

        // this command needs find
        if (!checkUtil("find")) {
            throw new Exception();
        }

        Command command = new Command(0, false, "dd if=/dev/zero of=/data/local/symlinks.txt bs=1024 count=1", "chmod 0777 /data/local/symlinks.txt");
        Shell.startRootShell().add(command);
        commandWait(Shell.startRootShell(), command);

        command = new Command(0, false, "find " + path + " -type l -exec ls -l {} \\; > /data/local/symlinks.txt");
        Shell.startRootShell().add(command);
        commandWait(Shell.startRootShell(), command);

        InternalVariables.symlinks = getSymLinks();
        if (InternalVariables.symlinks != null) {
            return InternalVariables.symlinks;
        } else {
            throw new Exception();
        }
    }

    /**
     * This will return to you a string to be used in your shell commands which will represent the
     * valid working toolbox with correct permissions. For instance, if Busybox is available it will
     * return "busybox", if busybox is not available but toolbox is then it will return "toolbox"
     *
     * @return String that indicates the available toolbox to use for accessing applets.
     */
    public String getWorkingToolbox() {
        if (RootTools.checkUtil("busybox")) {
            return "busybox";
        } else if (RootTools.checkUtil("toolbox")) {
            return "toolbox";
        } else {
            return "";
        }
    }

    /**
     * Checks if there is enough Space on SDCard
     *
     * @param updateSize size to Check (long)
     * @return <code>true</code> if the Update will fit on SDCard, <code>false</code> if not enough
     * space on SDCard. Will also return <code>false</code>, if the SDCard is not mounted as
     * read/write
     */
    @SuppressWarnings("deprecation")
    public boolean hasEnoughSpaceOnSdCard(long updateSize) {
        RootTools.log("Checking SDcard size and that it is mounted as RW");
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            return false;
        }
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = 0;
        long availableBlocks = 0;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            blockSize = stat.getBlockSize();
            availableBlocks = stat.getAvailableBlocks();
        } else {
            blockSize = stat.getBlockSizeLong();
            availableBlocks = stat.getAvailableBlocksLong();
        }
        return (updateSize < availableBlocks * blockSize);
    }

    /**
     * Checks whether the toolbox or busybox binary contains a specific util
     *
     * @param util
     * @param box  Should contain "toolbox" or "busybox"
     * @return true if it contains this util
     */
    public boolean hasUtil(final String util, final String box) {

        InternalVariables.found = false;

        // only for busybox and toolbox
        if (!(box.endsWith("toolbox") || box.endsWith("busybox"))) {
            return false;
        }

        try {

            Command command = new Command(0, false, box.endsWith("toolbox") ? box + " " + util : box + " --list") {

                @Override
                public void commandOutput(int id, String line) {
                    if (box.endsWith("toolbox")) {
                        if (!line.contains("no such tool")) {
                            InternalVariables.found = true;
                        }
                    } else if (box.endsWith("busybox")) {
                        // go through all lines of busybox --list
                        if (line.contains(util)) {
                            RootTools.log("Found util!");
                            InternalVariables.found = true;
                        }
                    }

                    super.commandOutput(id, line);
                }
            };
            RootTools.getShell(true).add(command);
            commandWait(RootTools.getShell(true), command);

            if (InternalVariables.found) {
                RootTools.log("Box contains " + util + " util!");
                return true;
            } else {
                RootTools.log("Box does not contain " + util + " util!");
                return false;
            }
        } catch (Exception e) {
            RootTools.log(e.getMessage());
            return false;
        }
    }

    /**
     * This method can be used to unpack a binary from the raw resources folder and store it in
     * /data/data/app.package/files/ This is typically useful if you provide your own C- or
     * C++-based binary. This binary can then be executed using sendShell() and its full path.
     *
     * @param context  the current activity's <code>Context</code>
     * @param sourceId resource id; typically <code>R.raw.id</code>
     * @param destName destination file name; appended to /data/data/app.package/files/
     * @param mode     chmod value for this file
     * @return a <code>boolean</code> which indicates whether or not we were able to create the new
     * file.
     */
    public boolean installBinary(Context context, int sourceId, String destName, String mode) {
        Installer installer;

        try {
            installer = new Installer(context);
        } catch (IOException ex) {
            if (RootTools.debugMode) {
                ex.printStackTrace();
            }
            return false;
        }

        return (installer.installBinary(sourceId, destName, mode));
    }

    /**
     * This method checks whether a binary is installed.
     *
     * @param context    the current activity's <code>Context</code>
     * @param binaryName binary file name; appended to /data/data/app.package/files/
     * @return a <code>boolean</code> which indicates whether or not
     * the binary already exists.
     */
    public boolean isBinaryAvailable(Context context, String binaryName) {
        Installer installer;

        try {
            installer = new Installer(context);
        } catch (IOException ex) {
            if (RootTools.debugMode) {
                ex.printStackTrace();
            }
            return false;
        }

        return (installer.isBinaryInstalled(binaryName));
    }

    /**
     * This will let you know if an applet is available from BusyBox
     * <p/>
     *
     * @param applet The applet to check for.
     * @return <code>true</code> if applet is available, false otherwise.
     */
    public boolean isAppletAvailable(String applet, String binaryPath) {
        try {
            for (String aplet : getBusyBoxApplets(binaryPath)) {
                if (aplet.equals(applet)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            RootTools.log(e.toString());
            return false;
        }
    }

    /**
     * This method can be used to to check if a process is running
     *
     * @param processName name of process to check
     * @return <code>true</code> if process was found
     * @throws TimeoutException (Could not determine if the process is running)
     */
    public boolean isProcessRunning(final String processName) {

        RootTools.log("Checks if process is running: " + processName);

        InternalVariables.processRunning = false;

        try {
            Command command = new Command(0, false, "ps") {
                @Override
                public void commandOutput(int id, String line) {
                    if (line.contains(processName)) {
                        InternalVariables.processRunning = true;
                    }

                    super.commandOutput(id, line);
                }
            };
            RootTools.getShell(true).add(command);
            commandWait(RootTools.getShell(true), command);

        } catch (Exception e) {
            RootTools.log(e.getMessage());
        }

        return InternalVariables.processRunning;
    }

    /**
     * This method can be used to kill a running process
     *
     * @param processName name of process to kill
     * @return <code>true</code> if process was found and killed successfully
     */
    public boolean killProcess(final String processName) {
        RootTools.log("Killing process " + processName);

        InternalVariables.pid_list = "";

        //Assume that the process is running
        InternalVariables.processRunning = true;

        try {

            Command command = new Command(0, false, "ps") {
                @Override
                public void commandOutput(int id, String line) {
                    if (line.contains(processName)) {
                        Matcher psMatcher = InternalVariables.psPattern.matcher(line);

                        try {
                            if (psMatcher.find()) {
                                String pid = psMatcher.group(1);

                                InternalVariables.pid_list += " " + pid;
                                InternalVariables.pid_list = InternalVariables.pid_list.trim();

                                RootTools.log("Found pid: " + pid);
                            } else {
                                RootTools.log("Matching in ps command failed!");
                            }
                        } catch (Exception e) {
                            RootTools.log("Error with regex!");
                            e.printStackTrace();
                        }
                    }

                    super.commandOutput(id, line);
                }
            };
            RootTools.getShell(true).add(command);
            commandWait(RootTools.getShell(true), command);

            // get all pids in one string, created in process method
            String pids = InternalVariables.pid_list;

            // kill processes
            if (!pids.equals("")) {
                try {
                    // example: kill -9 1234 1222 5343
                    command = new Command(0, false, "kill -9 " + pids);
                    RootTools.getShell(true).add(command);
                    commandWait(RootTools.getShell(true), command);

                    return true;
                } catch (Exception e) {
                    RootTools.log(e.getMessage());
                }
            } else {
                //no pids match, must be dead
                return true;
            }
        } catch (Exception e) {
            RootTools.log(e.getMessage());
        }

        return false;
    }

    /**
     * This will launch the Android market looking for BusyBox
     *
     * @param activity pass in your Activity
     */
    public void offerBusyBox(Activity activity) {
        RootTools.log("Launching Market for BusyBox");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=stericson.busybox"));
        activity.startActivity(i);
    }

    /**
     * This will launch the Android market looking for BusyBox, but will return the intent fired and
     * starts the activity with startActivityForResult
     *
     * @param activity    pass in your Activity
     * @param requestCode pass in the request code
     * @return intent fired
     */
    public Intent offerBusyBox(Activity activity, int requestCode) {
        RootTools.log("Launching Market for BusyBox");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=stericson.busybox"));
        activity.startActivityForResult(i, requestCode);
        return i;
    }

    /**
     * This will launch the Play Store looking for SuperUser
     *
     * @param activity pass in your Activity
     */
    public void offerSuperUser(Activity activity) {
        RootTools.log("Launching Play Store for SuperSU");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=eu.chainfire.supersu"));
        activity.startActivity(i);
    }

    /**
     * This will launch the Play Store looking for SuperSU, but will return the intent fired
     * and starts the activity with startActivityForResult
     *
     * @param activity    pass in your Activity
     * @param requestCode pass in the request code
     * @return intent fired
     */
    public Intent offerSuperUser(Activity activity, int requestCode) {
        RootTools.log("Launching Play Store for SuperSU");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=eu.chainfire.supersu"));
        activity.startActivityForResult(i, requestCode);
        return i;
    }

    private void commandWait(Shell shell, Command cmd) throws Exception {

        while (!cmd.isFinished()) {

            RootTools.log(Constants.TAG, shell.getCommandQueuePositionString(cmd));
            RootTools.log(Constants.TAG, "Processed " + cmd.totalOutputProcessed + " of " + cmd.totalOutput + " output from command.");

            synchronized (cmd) {
                try {
                    if (!cmd.isFinished()) {
                        cmd.wait(2000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!cmd.isExecuting() && !cmd.isFinished()) {
                if (!shell.isExecuting && !shell.isReading) {
                    Log.e(Constants.TAG, "Waiting for a command to be executed in a shell that is not executing and not reading! \n\n Command: " + cmd.getCommand());
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                } else if (shell.isExecuting && !shell.isReading) {
                    Log.e(Constants.TAG, "Waiting for a command to be executed in a shell that is executing but not reading! \n\n Command: " + cmd.getCommand());
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                } else {
                    Log.e(Constants.TAG, "Waiting for a command to be executed in a shell that is not reading! \n\n Command: " + cmd.getCommand());
                    Exception e = new Exception();
                    e.setStackTrace(Thread.currentThread().getStackTrace());
                    e.printStackTrace();
                }
            }

        }
    }
}
