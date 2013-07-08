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

package com.stericson.RootTools;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.stericson.RootTools.containers.Mount;
import com.stericson.RootTools.containers.Permissions;
import com.stericson.RootTools.containers.Symlink;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.exceptions.RootToolsException;
import com.stericson.RootTools.execution.Command;
import com.stericson.RootTools.execution.Executer;
import com.stericson.RootTools.execution.IResult;
import com.stericson.RootTools.execution.Shell;
import com.stericson.RootTools.internal.Remounter;
import com.stericson.RootTools.internal.RootToolsInternalMethods;
import com.stericson.RootTools.internal.Runner;

public final class RootTools {

    /**
     * This class is the gateway to every functionality within the RootTools library.The developer
     * should only have access to this class and this class only.This means that this class should
     * be the only one to be public.The rest of the classes within this library must not have the
     * public modifier.
     * 
     * All methods and Variables that the developer may need to have access to should be here.
     * 
     * If a method, or a specific functionality, requires a fair amount of code, or work to be done,
     * then that functionality should probably be moved to its own class and the call to it done
     * here.For examples of this being done, look at the remount functionality.
     */

	private static RootToolsInternalMethods rim = null;
	
	public static void setRim(RootToolsInternalMethods rim) {
		RootTools.rim = rim;
	}
	
	private static final RootToolsInternalMethods getInternals() {
		if (rim == null) {
			RootToolsInternalMethods.getInstance();
			return rim;
		} else {
			return rim;
		}
	}
	
    // --------------------
    // # Public Variables #
    // --------------------

    public static boolean debugMode = false;
    public static List<String> lastFoundBinaryPaths = new ArrayList<String>();
    public static int lastExitCode;
    public static String utilPath;

    /**
     * You can use this to force sendshell to use a shell other than the deafult.
     */
    public static String customShell = "";

    /**
     * Change this to a lower/higher setting to speed up/slow down shell commands if things are
     * taking too long or you are having constant crashes or timeout exceptions.
     */
    public static int shellDelay = 0;

    /**
     * Many Functions here use root by default, but there may be times that you do not want them to
     * use root. This can be useful when running a lot of commands at once. By default, if all of
     * these functions are requesting root access then superuser will notify the user everytime that
     * root is requested...this can lead to a flood of toast messages from superuser notifying the
     * user that root access is being requested.
     * 
     * Setting this to false will cause sendShell to not use root by default. So any commands sent
     * to the shell will not have root access unless specifically directed to obtain root access by
     * you. Some commands will not work properly without root access, so use this with care.
     */
    public static boolean useRoot = true;

    // ---------------------------
    // # Public Variable Getters #
    // ---------------------------

    // ------------------
    // # Public Methods #
    // ------------------

    /**
     * This will check a given binary, determine if it exists and determine that it has either the
     * permissions 755, 775, or 777.
     * 
     * 
     * @param util
     *            Name of the utility to check.
     * 
     * @return boolean to indicate whether the binary is installed and has appropriate permissions.
     */
    public static boolean checkUtil(String util) {
    	
		return getInternals().checkUtil(util);
    }

    /**
     * This will close all open shells.
     * 
     * @throws IOException 
     *  
     */
    public static void closeAllShells() throws IOException
    {
    	Shell.closeAll();
    }

    /**
     * This will close the custom shell that you opened.
     * 
     * @throws IOException 
     *  
     */
    public static void closeCustomShell() throws IOException
    {
		Shell.closeCustomShell();
    }
    
    /**
     * This will close either the root shell or the standard shell depending on what you specify.
     * 
     * @param root
     * 			a <code>boolean</code> to specify whether to close the root shell or the standard shell.
     * 
     * @throws IOException 
     *  
     */
    public static void closeShell(boolean root) throws IOException
    {
    	if (root)
    		Shell.closeRootShell();
    	else
    		Shell.closeShell();
    }
    
    /**
     * Copys a file to a destination. Because cp is not available on all android devices, we have a
     * fallback on the cat command
     * 
     * @param source
     *            example: /data/data/org.adaway/files/hosts
     * @param destination
     *            example: /system/etc/hosts
     * @param remountAsRw
     *            remounts the destination as read/write before writing to it
     * @param preserveFileAttributes
     *            tries to copy file attributes from source to destination, if only cat is available
     *            only permissions are preserved
     * @return true if it was successfully copied
     */
    public static boolean copyFile(String source, String destination, boolean remountAsRw, 
    		boolean preserveFileAttributes) {
        return getInternals().copyFile(source, destination, remountAsRw, preserveFileAttributes);
    }

    /**
     * Deletes a file or directory
     *
     * @param target
     *          example: /data/data/org.adaway/files/hosts
     * @param remountAsRw
     *          remounts the destination as read/write before writing to it
     * @return true if it was successfully deleted
     */
    public boolean deleteFileOrDirectory(String target, boolean remountAsRw) {
        return getInternals().deleteFileOrDirectory(target, remountAsRw);
    }

	/**
	 * Use this to check whether or not a file exists on the filesystem.
	 * 
	 * @param file
	 *            String that represent the file, including the full path to the
	 *            file and its name.
	 * 
	 * @return a boolean that will indicate whether or not the file exists.
	 * 
	 */
	public static boolean exists(final String file)
	{
		return getInternals().exists(file);
	}
	
    /**
     * This will try and fix a given binary. (This is for Busybox applets or Toolbox applets) By
     * "fix", I mean it will try and symlink the binary from either toolbox or Busybox and fix the
     * permissions if the permissions are not correct.
     * 
     * @param util
     *            Name of the utility to fix.
     * @param utilPath
     *            path to the toolbox that provides ln, rm, and chmod. This can be a blank string, a
     *            path to a binary that will provide these, or you can use
     *            RootTools.getWorkingToolbox()
     */
    public static void fixUtil(String util, String utilPath) {
    	getInternals().fixUtil(util, utilPath);
    }
    
    /**
     * This will check an array of binaries, determine if they exist and determine that it has
     * either the permissions 755, 775, or 777. If an applet is not setup correctly it will try and
     * fix it. (This is for Busybox applets or Toolbox applets)
     * 
     * @param utils
     *            Name of the utility to check.
     * 
     * @throws Exception
     *             if the operation cannot be completed.
     * 
     * @return boolean to indicate whether the operation completed. Note that this is not indicative
     *         of whether the problem was fixed, just that the method did not encounter any
     *         exceptions.
     */
    public static boolean fixUtils(String[] utils) throws Exception {
    	return getInternals().fixUtils(utils);
    }
        
    /**
     * 
     * @param binaryName
     *            String that represent the binary to find.
     * 
     * @return <code>true</code> if the specified binary was found. Also, the path the binary was
     *         found at can be retrieved via the variable lastFoundBinaryPath, if the binary was
     *         found in more than one location this will contain all of these locations.
     * 
     */
    public static boolean findBinary(String binaryName) {
    	return getInternals().findBinary(binaryName);
    }
    
    /**
     * @param path
     *				String that represents the path to the Busybox binary you want to retrieve the version of.
     *            
     * @return BusyBox version is found, "" if not found.
     */
    public static String getBusyBoxVersion(String path) {
        return getInternals().getBusyBoxVersion(path);
    }

    /**
     * @return BusyBox version is found, "" if not found.
     */
    public static String getBusyBoxVersion() {
        return RootTools.getBusyBoxVersion("");
    }

    /**
     * This will return an List of Strings. Each string represents an applet available from BusyBox.
     * <p/>
     * 
     * @return <code>List<String></code> a List of strings representing the applets available from
     *         Busybox.
     *         
     * @return <code>null</code> If we cannot return the list of applets.
     * 
     */
    public static List<String> getBusyBoxApplets() throws Exception {
        return RootTools.getBusyBoxApplets("");
    }
    
    /**
     * This will return an List of Strings. Each string represents an applet available from BusyBox.
     * <p/>
     * 
     * @param path
     *				Path to the busybox binary that you want the list of applets from.
     *
     * @return <code>List<String></code> a List of strings representing the applets available from
     *         Busybox.
     *         
     * @return <code>null</code> If we cannot return the list of applets.
     * 
     */
    public static List<String> getBusyBoxApplets(String path) throws Exception {
        return getInternals().getBusyBoxApplets(path);
    }

    /**
     * This will open or return, if one is already open, a custom shell, you are responsible for managing the shell, reading the output
     * and for closing the shell when you are done using it.
     * 
     * @param	shellPath
     * 				a <code>String</code> to Indicate the path to the shell that you want to open.
     * 
     * @param	timeout
     * 				an <code>int</code> to Indicate the length of time before giving up on opening a shell.
     * 
     * @throws	IOException 
     * @throws TimeoutException 
     * @throws RootDeniedException 
     *  
     */
    public static Shell getCustomShell(String shellPath, int timeout) throws IOException, TimeoutException, RootDeniedException
    {
    	return Shell.startCustomShell(shellPath, timeout);
    }
    
    /**
     * This will open or return, if one is already open, a custom shell, you are responsible for managing the shell, reading the output
     * and for closing the shell when you are done using it.
     * 
     * @param	shellPath
     * 				a <code>String</code> to Indicate the path to the shell that you want to open.
     * 
     * @throws	IOException 
     * @throws TimeoutException 
     * @throws RootDeniedException 
     *  
     */
    public static Shell getCustomShell(String shellPath) throws IOException, TimeoutException, RootDeniedException
    {
    	return RootTools.getCustomShell(shellPath, 10000);
    }
    
    /**
     * 
     * @param file
     *            String that represent the file, including the full path to the file and its name.
     * 
     * @return An instance of the class permissions from which you can get the permissions of the
     *         file or if the file could not be found or permissions couldn't be determined then
     *         permissions will be null.
     * 
     */
    public static Permissions getFilePermissionsSymlinks(String file) {
		return getInternals().getFilePermissionsSymlinks(file);
    }
    
    /**
     * This method will return the inode number of a file. This method is dependent on having a version of
     * ls that supports the -i parameter. 
     * 
     *  @param file path to the file that you wish to return the inode number
     *  
     *  @return String The inode number for this file or "" if the inode number could not be found.
     */
    public static String getInode(String file)
    {
    	return getInternals().getInode(file);
    }

    /**
     * This will return an ArrayList of the class Mount. The class mount contains the following
     * property's: device mountPoint type flags
     * <p/>
     * These will provide you with any information you need to work with the mount points.
     * 
     * @return <code>ArrayList<Mount></code> an ArrayList of the class Mount.
     * @throws Exception
     *             if we cannot return the mount points.
     */
    public static ArrayList<Mount> getMounts() throws Exception {
        return getInternals().getMounts();
    }
    
    /**
     * This will tell you how the specified mount is mounted. rw, ro, etc...
     * <p/>
     * @param path The mount you want to check
     * 
     * @return <code>String</code> What the mount is mounted as.
     * @throws Exception
     *             if we cannot determine how the mount is mounted.
     */
    public static String getMountedAs(String path) throws Exception {
        return getInternals().getMountedAs(path);
    }
    
    /**
     * This will return the environment variable $PATH
     * 
     * @return <code>Set<String></code> A Set of Strings representing the environment variable $PATH
     * @throws Exception
     *             if we cannot return the $PATH variable
     */
    public static Set<String> getPath() throws Exception {
        return getInternals().getPath();
    }

    /**
     * This will open or return, if one is already open, a shell, you are responsible for managing the shell, reading the output
     * and for closing the shell when you are done using it.
     *
     * @param	root
     * 				a <code>boolean</code> to Indicate whether or not you want to open a root shell or a standard shell
     *
     * @param	timeout
     * 				an <code>int</code> to Indicate the length of time to wait before giving up on opening a shell.
     *
     * @param   retry
     *              a <code>int</code> to indicate how many times the ROOT shell should try to open with root priviliges...
     *
     * @throws	IOException
     * @throws TimeoutException
     * @throws RootDeniedException
     *
     */
    public static Shell getShell(boolean root, int timeout, int retry) throws IOException, TimeoutException, RootDeniedException
    {
    	if (root)
    		return Shell.startRootShell(timeout);
    	else
    		return Shell.startShell(timeout);
    }

    /**
     * This will open or return, if one is already open, a shell, you are responsible for managing the shell, reading the output
     * and for closing the shell when you are done using it.
     * 
     * @param	root
     * 				a <code>boolean</code> to Indicate whether or not you want to open a root shell or a standard shell
     * 
     * @param	timeout
     * 				an <code>int</code> to Indicate the length of time to wait before giving up on opening a shell.
     * 
     * @throws	IOException 
     * @throws TimeoutException 
     * @throws RootDeniedException 
     *  
     */
    public static Shell getShell(boolean root, int timeout) throws IOException, TimeoutException, RootDeniedException
    {
    	return getShell(root, timeout, 3);
    }
    
    /**
     * This will open or return, if one is already open, a shell, you are responsible for managing the shell, reading the output
     * and for closing the shell when you are done using it.
     * 
     * @param	root
     * 				a <code>boolean</code> to Indicate whether or not you want to open a root shell or a standard shell
     * 
     * @throws	IOException 
     * @throws TimeoutException 
     * @throws RootDeniedException 
     *  
     */
    public static Shell getShell(boolean root) throws IOException, TimeoutException, RootDeniedException
    {
    	return RootTools.getShell(root, 10000);
    }
    
    /**
     * Get the space for a desired partition.
     * 
     * @param path
     *            The partition to find the space for.
     * @return the amount if space found within the desired partition. If the space was not found
     *         then the value is -1
     * @throws TimeoutException
     */
    public static long getSpace(String path) {
        return getInternals().getSpace(path);
    }

    /**
     * This will return a String that represent the symlink for a specified file.
     * <p/>
     * 
     * @param file
     *            path to the file to get the Symlink for. (must have absolute path)
     * 
     * @return <code>String</code> a String that represent the symlink for a specified file or an
     *         empty string if no symlink exists.
     */
    public static String getSymlink(String file) {
    	return getInternals().getSymlink(file);
    }
    
    /**
     * This will return an ArrayList of the class Symlink. The class Symlink contains the following
     * property's: path SymplinkPath
     * <p/>
     * These will provide you with any Symlinks in the given path.
     * 
     * @param path
     *            path to search for Symlinks.
     * 
     * @return <code>ArrayList<Symlink></code> an ArrayList of the class Symlink.
     * @throws Exception
     *             if we cannot return the Symlinks.
     */
    public static ArrayList<Symlink> getSymlinks(String path) throws Exception {
    	return getInternals().getSymlinks(path);
    }
    
    /**
     * This will return to you a string to be used in your shell commands which will represent the
     * valid working toolbox with correct permissions. For instance, if Busybox is available it will
     * return "busybox", if busybox is not available but toolbox is then it will return "toolbox"
     * 
     * @return String that indicates the available toolbox to use for accessing applets.
     */
    public static String getWorkingToolbox() {
        return getInternals().getWorkingToolbox();
    }
    
    /**
     * Checks if there is enough Space on SDCard
     * 
     * @param updateSize
     *            size to Check (long)
     * @return <code>true</code> if the Update will fit on SDCard, <code>false</code> if not enough
     *         space on SDCard. Will also return <code>false</code>, if the SDCard is not mounted as
     *         read/write
     */
    public static boolean hasEnoughSpaceOnSdCard(long updateSize) {
        return getInternals().hasEnoughSpaceOnSdCard(updateSize);
    }

    /**
     * Checks whether the toolbox or busybox binary contains a specific util
     * 
     * @param util
     * @param box
     *            Should contain "toolbox" or "busybox"
     * @return true if it contains this util
     */
    public static boolean hasUtil(final String util, final String box) {
    	//TODO Convert this to use the new shell.
    	return getInternals().hasUtil(util, box);
    }

    /**
     * This method can be used to unpack a binary from the raw resources folder and store it in
     * /data/data/app.package/files/ This is typically useful if you provide your own C- or
     * C++-based binary. This binary can then be executed using sendShell() and its full path.
     * 
     * @param context
     *            the current activity's <code>Context</code>
     * @param sourceId
     *            resource id; typically <code>R.raw.id</code>
     * @param destName
     *            destination file name; appended to /data/data/app.package/files/
     * @param mode
     *            chmod value for this file
     * @return a <code>boolean</code> which indicates whether or not we were able to create the new
     *         file.
     */
    public static boolean installBinary(Context context, int sourceId, String destName, String mode) {
        return getInternals().installBinary(context, sourceId, destName, mode);
    }

    /**
     * This method can be used to unpack a binary from the raw resources folder and store it in
     * /data/data/app.package/files/ This is typically useful if you provide your own C- or
     * C++-based binary. This binary can then be executed using sendShell() and its full path.
     * 
     * @param context
     *            the current activity's <code>Context</code>
     * @param sourceId
     *            resource id; typically <code>R.raw.id</code>
     * @param binaryName
     *            destination file name; appended to /data/data/app.package/files/
     * @return a <code>boolean</code> which indicates whether or not we were able to create the new
     *         file.
     */
    public static boolean installBinary(Context context, int sourceId, String binaryName) {
        return installBinary(context, sourceId, binaryName, "700");
    }
    
    /**
     * This will let you know if an applet is available from BusyBox
     * <p/>
     * 
     * @param applet The applet to check for.
     * @param path
     *				Path to the busybox binary that you want to check. (do not include binary name)
     * 
     * @return <code>true</code> if applet is available, false otherwise.
     */
    public static boolean isAppletAvailable(String applet, String path) {
        return getInternals().isAppletAvailable(applet, path);
    }

    /**
     * This will let you know if an applet is available from BusyBox
     * <p/>
     * 
     * @param applet The applet to check for.
     * 
     * @return <code>true</code> if applet is available, false otherwise.
     */
    public static boolean isAppletAvailable(String applet) {
        return RootTools.isAppletAvailable(applet, "");
    }

    /**
     * @return <code>true</code> if your app has been given root access.
     * @throws TimeoutException
     *             if this operation times out. (cannot determine if access is given)
     */
    public static boolean isAccessGiven() {
    	return getInternals().isAccessGiven();
    }

    /**
     * @return <code>true</code> if BusyBox was found.
     */
    public static boolean isBusyboxAvailable() {
        return findBinary("busybox");
    }
    
    public static boolean isNativeToolsReady(int nativeToolsId, Context context) {
    	return getInternals().isNativeToolsReady(nativeToolsId, context);
    }
    
    /**
     * This method can be used to to check if a process is running
     * 
     * @param processName
     *            name of process to check
     * @return <code>true</code> if process was found
     * @throws TimeoutException
     *             (Could not determine if the process is running)
     */
    public static boolean isProcessRunning(final String processName) {
    	//TODO convert to new shell
        return getInternals().isProcessRunning(processName);
    }
    
    /**
     * @return <code>true</code> if su was found.
     */
    public static boolean isRootAvailable() {
        return findBinary("su");
    }
    
    /**
     * This method can be used to kill a running process
     * 
     * @param processName
     *            name of process to kill
     * @return <code>true</code> if process was found and killed successfully
     */
    public static boolean killProcess(final String processName) {
    	//TODO convert to new shell
		return getInternals().killProcess(processName);
    }
    
    /**
     * This will launch the Android market looking for BusyBox
     * 
     * @param activity
     *            pass in your Activity
     */
    public static void offerBusyBox(Activity activity) {
        getInternals().offerBusyBox(activity);
    }

    /**
     * This will launch the Android market looking for BusyBox, but will return the intent fired and
     * starts the activity with startActivityForResult
     * 
     * @param activity
     *            pass in your Activity
     * @param requestCode
     *            pass in the request code
     * @return intent fired
     */
    public static Intent offerBusyBox(Activity activity, int requestCode) {
        return getInternals().offerBusyBox(activity, requestCode);
    }

    /**
     * This will launch the Android market looking for SuperUser
     * 
     * @param activity
     *            pass in your Activity
     */
    public static void offerSuperUser(Activity activity) {
        getInternals().offerSuperUser(activity);
    }

    /**
     * This will launch the Android market looking for SuperUser, but will return the intent fired
     * and starts the activity with startActivityForResult
     * 
     * @param activity
     *            pass in your Activity
     * @param requestCode
     *            pass in the request code
     * @return intent fired
     */
    public static Intent offerSuperUser(Activity activity, int requestCode) {
        return getInternals().offerSuperUser(activity, requestCode);
    }
    
    /**
     * This will take a path, which can contain the file name as well, and attempt to remount the
     * underlying partition.
     * <p/>
     * For example, passing in the following string:
     * "/system/bin/some/directory/that/really/would/never/exist" will result in /system ultimately
     * being remounted. However, keep in mind that the longer the path you supply, the more work
     * this has to do, and the slower it will run.
     * 
     * @param file
     *            file path
     * @param mountType
     *            mount type: pass in RO (Read only) or RW (Read Write)
     * @return a <code>boolean</code> which indicates whether or not the partition has been
     *         remounted as specified.
     */
    public static boolean remount(String file, String mountType) {
    	// Recieved a request, get an instance of Remounter
        Remounter remounter = new Remounter();
        // send the request.
        return (remounter.remount(file, mountType));
    }

    /**
     * This restarts only Android OS without rebooting the whole device. This does NOT work on all
     * devices. This is done by killing the main init process named zygote. Zygote is restarted
     * automatically by Android after killing it.
     * 
     * @throws TimeoutException
     */
    public static void restartAndroid() {
        RootTools.log("Restart Android");
        killProcess("zygote");
    }

    /**
     * Executes binary in a separated process. Before using this method, the binary has to be
     * installed in /data/data/app.package/files/ using the installBinary method.
     * 
     * @param context
     *            the current activity's <code>Context</code>
     * @param binaryName
     *            name of installed binary
     * @param parameter
     *            parameter to append to binary like "-vxf"
     */
    public static void runBinary(Context context, String binaryName, String parameter) {
		Runner runner = new Runner(context, binaryName, parameter);
        runner.start();
    }

    /**
     * Executes a given command with root access or without depending on the value of the boolean passed.
     * This will also start a root shell or a standard shell without you having to open it specifically.
     * 
     * You will still need to close the shell after you are done using the shell.
     * 
     * @param shell
     *        	The shell to execute the command on, this can be a root shell or a standard shell.
     *            
     * @param command
     *            The command to execute in the shell
     *            
     * @throws IOException
     */
	public static void runShellCommand(Shell shell, Command command) throws IOException {
		shell.add(command);
	}
	
    /**
     * Sends several shell command as su (attempts to)
     * 
     * @param commands
     *            array of commands to send to the shell
     * @param sleepTime
     *            time to sleep between each command, delay.
     * @param result
     *            injected result object that implements the Result class
     * @param timeout
     *            How long to wait before throwing TimeoutException, sometimes when running root
     *            commands on certain devices or roms ANR's may occur because a process never
     *            returns or readline never returns. This allows you to protect your application
     *            from throwing an ANR.
     * 
     *            if you pass -1, then the default timeout is 5 minutes.
     * 
     * @return a <code>LinkedList</code> containing each line that was returned by the shell after
     *         executing or while trying to execute the given commands. You must iterate over this
     *         list, it does not allow random access, so no specifying an index of an item you want,
     *         not like you're going to know that anyways.
     * @throws InterruptedException
     * @throws IOException
     * @throws TimeoutException
     */
    public static List<String> sendShell(String[] commands, int sleepTime, Result result,
            int timeout) throws IOException, RootToolsException, TimeoutException {
        return sendShell(commands, sleepTime, result, useRoot, timeout);
    }

    /**
     * Sends several shell command as su (attempts to) if useRoot is true; as the current user
     * (app_xxx) otherwise.
     * 
     * @deprecated
     * 
     * @param commands
     *            array of commands to send to the shell
     * @param sleepTime
     *            time to sleep between each command, delay.
     * @param result
     *            injected result object that implements the Result class
     * @param useRoot
     *            whether to use root or not when issuing these commands.
     * @param timeout
     *            How long to wait before throwing TimeoutException, sometimes when running root
     *            commands on certain devices or roms ANR's may occur because a process never
     *            returns or readline never returns. This allows you to protect your application
     *            from throwing an ANR.
     * 
     *            if you pass -1, then the default timeout is 5 minutes.
     * 
     * @return a <code>LinkedList</code> containing each line that was returned by the shell after
     *         executing or while trying to execute the given commands. You must iterate over this
     *         list, it does not allow random access, so no specifying an index of an item you want,
     *         not like you're going to know that anyways.
     * @throws InterruptedException
     * @throws IOException
     * @throws TimeoutException
     */
    public static List<String> sendShell(String[] commands, int sleepTime, Result result,
            boolean useRoot, int timeout) throws IOException, RootToolsException, TimeoutException {
        return new Executer().sendShell(commands, sleepTime, result, useRoot, timeout);
    }

    /**
     * Sends several shell command as su, unless useRoot is set to false
     * 
     * @deprecated
     * 
     * @param commands
     *            array of commands to send to the shell
     * @param sleepTime
     *            time to sleep between each command, delay.
     * @param timeout
     *            How long to wait before throwing TimeoutException, sometimes when running root
     *            commands on certain devices or roms ANR's may occur because a process never
     *            returns or readline never returns. This allows you to protect your application
     *            from throwing an ANR.
     * 
     *            if you pass -1, then the default timeout is 5 minutes.
     * 
     * @return a LinkedList containing each line that was returned by the shell after executing or
     *         while trying to execute the given commands. You must iterate over this list, it does
     *         not allow random access, so no specifying an index of an item you want, not like
     *         you're going to know that anyways.
     * 
     * @throws InterruptedException
     * @throws IOException
     * @throws TimeoutException
     */
    public static List<String> sendShell(String[] commands, int sleepTime, int timeout)
            throws IOException, RootToolsException, TimeoutException {
        return sendShell(commands, sleepTime, null, timeout);
    }

    /**
     * Sends one shell command as su, unless useRoot is set to false
     * 
     * @deprecated
     * 
     * @param command
     *            command to send to the shell
     * @param result
     *            injected result object that implements the Result class
     * @param timeout
     *            How long to wait before throwing TimeoutException, sometimes when running root
     *            commands on certain devices or roms ANR's may occur because a process never
     *            returns or readline never returns. This allows you to protect your application
     *            from throwing an ANR.
     * 
     *            if you pass -1, then the default timeout is 5 minutes.
     * 
     * @return a <code>LinkedList</code> containing each line that was returned by the shell after
     *         executing or while trying to execute the given commands. You must iterate over this
     *         list, it does not allow random access, so no specifying an index of an item you want,
     *         not like you're going to know that anyways.
     * 
     * @throws InterruptedException
     * @throws IOException
     * @throws RootToolsException
     * @throws TimeoutException
     */
    public static List<String> sendShell(String command, Result result, int timeout)
            throws IOException, RootToolsException, TimeoutException {
        return sendShell(new String[] { command }, 0, result, timeout);
    }

    /**
     * Sends one shell command as su, unless useRoot is set to false
     * 
     * @deprecated
     * 
     * @param command
     *            command to send to the shell
     * @param timeout
     *            How long to wait before throwing TimeoutException, sometimes when running root
     *            commands on certain devices or roms ANR's may occur because a process never
     *            returns or readline never returns. This allows you to protect your application
     *            from throwing an ANR.
     * 
     *            if you pass -1, then the default timeout is 5 minutes.
     * 
     * @return a LinkedList containing each line that was returned by the shell after executing or
     *         while trying to execute the given commands. You must iterate over this list, it does
     *         not allow random access, so no specifying an index of an item you want, not like
     *         you're going to know that anyways.
     * @throws InterruptedException
     * @throws IOException
     * @throws TimeoutException
     */
    public static List<String> sendShell(String command, int timeout) throws IOException,
            RootToolsException, TimeoutException {
        return sendShell(command, null, timeout);
    }

    public static abstract class Result implements IResult {
        private Process process = null;
        private Serializable data = null;
        private int error = 0;

        public abstract void process(String line) throws Exception;

        public abstract void processError(String line) throws Exception;

        public abstract void onFailure(Exception ex);

        public abstract void onComplete(int diag);

        public Result setProcess(Process process) {
            this.process = process;
            return this;
        }

        public Process getProcess() {
            return process;
        }

        public Result setData(Serializable data) {
            this.data = data;
            return this;
        }

        public Serializable getData() {
            return data;
        }

        public Result setError(int error) {
            this.error = error;
            return this;
        }

        public int getError() {
            return error;
        }
    }
    

    /**
     * This method allows you to output debug messages only when debugging is on. This will allow
     * you to add a debug option to your app, which by default can be left off for performance.
     * However, when you need debugging information, a simple switch can enable it and provide you
     * with detailed logging.
     * <p/>
     * This method handles whether or not to log the information you pass it depending whether or
     * not RootTools.debugMode is on. So you can use this and not have to worry about handling it
     * yourself.
     *
     * @param msg
     *            The message to output.
     */
    public static void log(String msg) {
        log(null, msg, 3, null);
    }

    /**
     * This method allows you to output debug messages only when debugging is on. This will allow
     * you to add a debug option to your app, which by default can be left off for performance.
     * However, when you need debugging information, a simple switch can enable it and provide you
     * with detailed logging.
     * <p/>
     * This method handles whether or not to log the information you pass it depending whether or
     * not RootTools.debugMode is on. So you can use this and not have to worry about handling it
     * yourself.
     *
     * @param TAG
     *            Optional parameter to define the tag that the Log will use.
     * @param msg
     *            The message to output.
     *
     */
    public static void log(String TAG, String msg) {
        log(TAG, msg, 3, null);
    }

    /**
     * This method allows you to output debug messages only when debugging is on. This will allow
     * you to add a debug option to your app, which by default can be left off for performance.
     * However, when you need debugging information, a simple switch can enable it and provide you
     * with detailed logging.
     * <p/>
     * This method handles whether or not to log the information you pass it depending whether or
     * not RootTools.debugMode is on. So you can use this and not have to worry about handling it
     * yourself.
     *
     * @param msg
     *            The message to output.
     *
     * @param type
     * 			  The type of log, 1 for verbose, 2 for error, 3 for debug
     *
     * @param e
     * 			  The exception that was thrown (Needed for errors)
     */
    public static void log(String msg, int type, Exception e) {
        log(null, msg, type, e);
    }

    /**
     * This method allows you to output debug messages only when debugging is on. This will allow
     * you to add a debug option to your app, which by default can be left off for performance.
     * However, when you need debugging information, a simple switch can enable it and provide you
     * with detailed logging.
     * <p/>
     * This method handles whether or not to log the information you pass it depending whether or
     * not RootTools.debugMode is on. So you can use this and not have to worry about handling it
     * yourself.
     *
     * @param TAG
     *            Optional parameter to define the tag that the Log will use.
     * @param msg
     *            The message to output.
     *
     * @param type
     * 			  The type of log, 1 for verbose, 2 for error, 3 for debug
     *
     * @param e
     * 			  The exception that was thrown (Needed for errors)
     */
    public static void log(String TAG, String msg, int type, Exception e) {
        if (msg != null && !msg.equals("")) {
            if (debugMode) {
                if (TAG == null) {
                	TAG = Constants.TAG;
                }
                
            	switch (type)
            	{
            	case 1:
                    Log.v(TAG, msg);
                    break;
            	case 2:
                    Log.e(TAG, msg, e);
                    break;
            	case 3:
                    Log.d(TAG, msg);
                    break;
            	}
            }
        }
    }
}
