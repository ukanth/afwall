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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

import com.stericson.RootTools.RootTools.Result;

//no modifier, this is package-private which means that no one but the library can access it.
class InternalMethods
{

	// --------------------
	// # Internal methods #
	// --------------------

	protected boolean returnPath() throws TimeoutException
	{

		CommandCapture command = null;

		try
		{
			if(!RootTools.exists("/data/local/tmp"))
			{
				command = new CommandCapture(0, "mkdir /data/local/tmp");
				Shell.startRootShell().add(command).waitForFinish();
			}

			InternalVariables.path = new HashSet<String>();
			// Try to read from the file.
			LineNumberReader lnr = null;

			String mountedas = RootTools.getMountedAs("/");
			RootTools.remount("/", "rw");

			command = new CommandCapture(0, "chmod 0777 /init.rc");
			Shell.startRootShell().add(command);
			command = new CommandCapture(0,
					"dd if=/init.rc of=/data/local/tmp/init.rc");
			Shell.startRootShell().add(command);
			command = new CommandCapture(0,
					"chmod 0777 /data/local/tmp/init.rc");
			Shell.startRootShell().add(command).waitForFinish();

			RootTools.remount("/", mountedas);

			lnr = new LineNumberReader(
					new FileReader("/data/local/tmp/init.rc"));
			String line;
			while ((line = lnr.readLine()) != null)
			{
				RootTools.log(line);
				if(line.contains("export PATH"))
				{
					int tmp = line.indexOf("/");
					InternalVariables.path = new HashSet<String>(
							Arrays.asList(line.substring(tmp).split(":")));
					return true;
				}
			}
			return false;
		}
		catch (Exception e)
		{
			if(RootTools.debugMode)
			{
				RootTools.log("Error: " + e.getMessage());
				e.printStackTrace();
			}
			return false;
		}
	}

	protected ArrayList<Symlink> getSymLinks() throws FileNotFoundException,
			IOException
	{
		LineNumberReader lnr = null;
		try
		{
			lnr = new LineNumberReader(new FileReader(
					"/data/local/symlinks.txt"));
			String line;
			ArrayList<Symlink> symlink = new ArrayList<Symlink>();
			while ((line = lnr.readLine()) != null)
			{

				RootTools.log(line);

				String[] fields = line.split(" ");
				symlink.add(new Symlink(new File(fields[fields.length - 3]), // file
						new File(fields[fields.length - 1]) // SymlinkPath
				));
			}
			return symlink;
		}
		finally
		{
			// no need to do anything here.
		}
	}

	protected Permissions getPermissions(String line)
	{

		String[] lineArray = line.split(" ");
		String rawPermissions = lineArray[0];

		if(rawPermissions.length() == 10
				&& (rawPermissions.charAt(0) == '-'
						|| rawPermissions.charAt(0) == 'd' || rawPermissions
						.charAt(0) == 'l')
				&& (rawPermissions.charAt(1) == '-' || rawPermissions.charAt(1) == 'r')
				&& (rawPermissions.charAt(2) == '-' || rawPermissions.charAt(2) == 'w'))
		{
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

	protected int parsePermissions(String permission)
	{
		int tmp;
		if(permission.charAt(0) == 'r')
			tmp = 4;
		else
			tmp = 0;

		RootTools.log("permission " + tmp);
		RootTools.log("character " + permission.charAt(0));

		if(permission.charAt(1) == 'w')
			tmp += 2;
		else
			tmp += 0;

		RootTools.log("permission " + tmp);
		RootTools.log("character " + permission.charAt(1));

		if(permission.charAt(2) == 'x')
			tmp += 1;
		else
			tmp += 0;

		RootTools.log("permission " + tmp);
		RootTools.log("character " + permission.charAt(2));

		return tmp;
	}
	
	protected int parseSpecialPermissions(String permission)
	{
		int tmp = 0;
		if(permission.charAt(2) == 's')
			tmp += 4;

		if(permission.charAt(5) == 's')
			tmp += 2;

		if(permission.charAt(8) == 't')
			tmp += 1;

		RootTools.log("special permissions " + tmp);

		return tmp;
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
                	CommandCapture command = new CommandCapture(0, "cp -fp " + source + " " + destination);
                	Shell.startRootShell().add(command).waitForFinish();
                } else {
                	CommandCapture command = new CommandCapture(0, "cp -f " + source + " " + destination);
                	Shell.startRootShell().add(command).waitForFinish();
                }
            } else {
                if (checkUtil("busybox") && hasUtil("cp", "busybox")) {
                    RootTools.log("busybox cp command is available!");

                    if (preserveFileAttributes) {
                    	CommandCapture command = new CommandCapture(0, "busybox cp -fp " + source + " " + destination);
                    	Shell.startRootShell().add(command).waitForFinish();
                    } else {
                    	CommandCapture command = new CommandCapture(0, "busybox cp -f " + source + " " + destination);
                    	Shell.startRootShell().add(command).waitForFinish();
                    }
                } else { // if cp is not available use cat
                    // if cat is available and has appropriate permissions
                    if (checkUtil("cat")) {
                        RootTools.log("cp is not available, use cat!");

                        int filePermission = -1;
                        if (preserveFileAttributes) {
                            // get permissions of source before overwriting
                            Permissions permissions = getFilePermissionsSymlinks(source);
                            filePermission = permissions.permissions;
                        }

                        CommandCapture command;
                        // copy with cat
                    	command = new CommandCapture(0, "cat " + source + " > " + destination);
                    	Shell.startRootShell().add(command).waitForFinish();
                        
                        if (preserveFileAttributes) {
                            // set premissions of source to destination
                        	command = new CommandCapture(0, "chmod " + filePermission + " " + destination);
                        	Shell.startRootShell().add(command).waitForFinish();
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

        return result;
    }

	/**
	 * This will check a given binary, determine if it exists and determine that
	 * it has either the permissions 755, 775, or 777.
	 * 
	 * 
	 * @param String
	 *            Name of the utility to check.
	 * 
	 * @return boolean to indicate whether the binary is installed and has
	 *         appropriate permissions.
	 */
	static boolean checkUtil(String util)
	{
		if(RootTools.findBinary(util))
		{

			List<String> binaryPaths = new ArrayList<String>();
			binaryPaths.addAll(RootTools.lastFoundBinaryPaths);

			for(String path : binaryPaths)
			{
				Permissions permissions = RootTools
						.getFilePermissionsSymlinks(path + "/" + util);

				if(permissions != null)
				{
					String permission;
					
					if (Integer.toString(permissions.getPermissions()).length() > 3)
						permission = Integer.toString(permissions.getPermissions()).substring(1);
					else
						permission = Integer.toString(permissions.getPermissions());
					
					if(permission.equals("755") || permission.equals("777")
							|| permission.equals("775"))
					{
						RootTools.utilPath = path + "/" + util;
						return true;
					}
				}
			}
		}

		return false;

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
		final List<String> result = new ArrayList<String>();
		
		Command command = new Command(0, "ls " + file)
		{
			@Override
			public void output(int arg0, String arg1)
			{
				RootTools.log(arg1);
				result.add(arg1);
			}			
		};
		
		try
		{
			//Try not to open a new shell if one is open.
			if (!Shell.isAnyShellOpen())
				Shell.startShell().add(command).waitForFinish();
			else
				Shell.getOpenShell().add(command).waitForFinish();
		}
		catch (Exception e)
		{
			return false;
		}
		
		for (String line : result)
		{
			if (line.trim().equals(file))
			{
				return true;
			}
		}
		
		try
		{
			RootTools.closeShell(false);
		} catch (Exception e) {}
		
		result.clear();
		try
		{
			Shell.startRootShell().add(command).waitForFinish();
		}
		catch (Exception e)
		{
			return false;
		}
		
		//Avoid concurrent modification...
		List<String> final_result = new ArrayList<String>();
		final_result.addAll(result);
		
		for (String line : final_result)
		{
			if (line.trim().equals(file))
			{
				return true;
			}
		}
		
		return false;

	}
	
    /**
     * This will try and fix a given binary. (This is for Busybox applets or Toolbox applets) By
     * "fix", I mean it will try and symlink the binary from either toolbox or Busybox and fix the
     * permissions if the permissions are not correct.
     * 
     * @param String
     *            Name of the utility to fix.
     * @param String
     *            path to the toolbox that provides ln, rm, and chmod. This can be a blank string, a
     *            path to a binary that will provide these, or you can use
     *            RootTools.getWorkingToolbox()
     */
    public static void fixUtil(String util, String utilPath) {
        try {
            RootTools.remount("/system", "rw");

            if (RootTools.findBinary(util)) {
            	List<String> paths = new ArrayList<String>();
            	paths.addAll(RootTools.lastFoundBinaryPaths);
                for (String path : paths)
                {
                	CommandCapture command = new CommandCapture(0, utilPath + " rm " + path + "/" + util);
                	Shell.startRootShell().add(command).waitForFinish();
                }

                CommandCapture command = new CommandCapture(0, utilPath + " ln -s " + utilPath + " /system/bin/" + util, utilPath + " chmod 0755 /system/bin/" + util);
            	Shell.startRootShell().add(command).waitForFinish();
            }

            RootTools.remount("/system", "ro");
        } catch (Exception e) {}
    }
    
    /**
     * This will check an array of binaries, determine if they exist and determine that it has
     * either the permissions 755, 775, or 777. If an applet is not setup correctly it will try and
     * fix it. (This is for Busybox applets or Toolbox applets)
     * 
     * @param String
     *            Name of the utility to check.
     * 
     * @throws Exception
     *             if the operation cannot be completed.
     * 
     * @return boolean to indicate whether the operation completed. Note that this is not indicative
     *         of whether the problem was fixed, just that the method did not encounter any
     *         exceptions.
     */
    static boolean fixUtils(String[] utils) throws Exception {

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
     * 
     * @param binaryName
     *            String that represent the binary to find.
     * 
     * @return <code>true</code> if the specified binary was found. Also, the path the binary was
     *         found at can be retrieved via the variable lastFoundBinaryPath, if the binary was
     *         found in more than one location this will contain all of these locations.
     * 
     */
    static boolean findBinary(String binaryName) {
        boolean found = false;
        RootTools.lastFoundBinaryPaths.clear();

    	List<String> list = new ArrayList<String>();

        RootTools.log("Checking for " + binaryName);
        
        try {
        	Set<String> paths = RootTools.getPath();
        	if (paths.size() > 0)
        	{
	            for (String path : paths) {
	                if (RootTools.exists(path + "/" + binaryName)) {
	                	RootTools.log(binaryName + " was found here: " + path);
	                    list.add(path);
	                    found = true;
	                } else {
	                	RootTools.log(binaryName + " was NOT found here: " + path);
	                }
	            }
        	}
        } catch (TimeoutException ex) {
            RootTools.log("TimeoutException!!!");
        } catch (Exception e) {
            RootTools.log(binaryName + " was not found, more information MAY be available with Debugging on.");
        }

        if (!found) {
            RootTools.log("Trying second method");
            RootTools.log("Checking for " + binaryName);
            String[] places = { "/sbin/", "/system/bin/", "/system/xbin/", "/data/local/xbin/",
                    "/data/local/bin/", "/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/" };
            for (String where : places) {
                if (RootTools.exists(where + binaryName)) {
                	RootTools.log(binaryName + " was found here: " + where);
                    list.add(where);
                    found = true;
                } else {
                	RootTools.log(binaryName + " was NOT found here: " + where);
                }
            }
        }

        if (RootTools.debugMode)
        {        	
        	for (String path : list)
        	{
            	RootTools.log("Paths: " + path);	
        	}        	
        }
        
        Collections.reverse(list);
        
        RootTools.lastFoundBinaryPaths.addAll(list);
        
        return found;
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
     */
    static List<String> getBusyBoxApplets(String path) throws Exception {
    	
    	if (path != null && !path.endsWith("/"))
    		path += "/";
    	
    	final List<String> results = new ArrayList<String>();
    	
    	Command command = new Command(InternalVariables.BBA, path + "busybox --list")
    	{

			@Override
			public void output(int id, String line)
			{
				if (id == InternalVariables.BBA)
				{
					if (!line.trim().equals("") && !line.trim().contains("not found"))
						results.add(line);
				}				
			}    		
    	};
    	
    	Shell.startRootShell().add(command);
    	command.waitForFinish();
    	
        return results;
    }
    
    /**
     * @return BusyBox version is found, "" if not found.
     */
    static String getBusyBoxVersion(String path) {
    	
    	if (!path.equals("") && !path.endsWith("/"))
    	{
    		path += "/";
    	}
    	
        RootTools.log("Getting BusyBox Version");
        InternalVariables.busyboxVersion = "";
        try {
        	Command command = new Command(InternalVariables.BBV, path + "busybox")
        	{
				@Override
				public void output(int id, String line)
				{
					if (id == InternalVariables.BBV)
					{
		                if (line.startsWith("BusyBox")) {
		                    String[] temp = line.split(" ");
		                    InternalVariables.busyboxVersion = temp[1];
		                }
					}					
				}
        	};
        	
        	Shell.startRootShell().add(command);
        	command.waitForFinish();
        	
        } catch (Exception e) {
            RootTools.log("BusyBox was not found, more information MAY be available with Debugging on.");
            return "";
        }
        
        return InternalVariables.busyboxVersion;
    }
    
	/**
	 * @return long Size, converted to kilobytes (from xxx or xxxm or xxxk etc.)
	 */
	protected long getConvertedSpace(String spaceStr)
	{
		try
		{
			double multiplier = 1.0;
			char c;
			StringBuffer sb = new StringBuffer();
			for(int i = 0; i < spaceStr.length(); i++)
			{
				c = spaceStr.charAt(i);
				if(!Character.isDigit(c) && c != '.')
				{
					if(c == 'm' || c == 'M')
					{
						multiplier = 1024.0;
					}
					else if(c == 'g' || c == 'G')
					{
						multiplier = 1024.0 * 1024.0;
					}
					break;
				}
				sb.append(spaceStr.charAt(i));
			}
			return (long) Math.ceil(Double.valueOf(sb.toString()) * multiplier);
		}
		catch (Exception e)
		{
			return -1;
		}
	}

    /**
     * This method will return the inode number of a file. This method is dependent on having a version of
     * ls that supports the -i parameter. 
     * 
     *  @param String path to the file that you wish to return the inode number
     *  
     *  @return String The inode number for this file or "" if the inode number could not be found.
     */
    static String getInode(String file)
    {
    	try
    	{
    		Command command = new Command(InternalVariables.GI, "/data/local/ls -i " + file)
    		{

				@Override
				public void output(int id, String line)
				{
					if (id == InternalVariables.GI)
					{
			    		if (!line.trim().equals("") && Character.isDigit((char) line.trim().substring(0, 1).toCharArray()[0]))
			    		{
			    			InternalVariables.inode = line.trim().split(" ")[0].toString();
			    		}
					}					
				}
    		};
    		Shell.startRootShell().add(command);
    		command.waitForFinish();
    		
	    	return InternalVariables.inode;
    	}
    	catch (Exception ignore)
    	{
    		return "";
    	}
    }

    /**
     * @return <code>true</code> if your app has been given root access.
     * @throws TimeoutException
     *             if this operation times out. (cannot determine if access is given)
     */
    static boolean isAccessGiven() {
        try {
            RootTools.log("Checking for Root access");
            InternalVariables.accessGiven = false;
            
        	Command command = new Command(InternalVariables.IAG, "id")
        	{
				@Override
				public void output(int id, String line)
				{
					if (id == InternalVariables.IAG)
					{
						Set<String> ID = new HashSet<String>(Arrays.asList(line.split(" ")));
		                for (String userid : ID) {
		                    RootTools.log(userid);

		                    if (userid.toLowerCase().contains("uid=0")) {
		                        InternalVariables.accessGiven = true;
		                        RootTools.log("Access Given");
		                        break;
		                    }
		                }
		                if (!InternalVariables.accessGiven) {
		                    RootTools.log("Access Denied?");
		                }		                    
					}					
				}
        	};
        	
        	Shell.startRootShell().add(command);
        	command.waitForFinish();
        	
        	
            if (InternalVariables.accessGiven) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            RootTools.shellDelay = 0;
        }
    }

    static boolean isNativeToolsReady(int nativeToolsId, Context context) {
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
	 * 
	 * @param file
	 *            String that represent the file, including the full path to the
	 *            file and its name.
	 * 
	 * @return An instance of the class permissions from which you can get the
	 *         permissions of the file or if the file could not be found or
	 *         permissions couldn't be determined then permissions will be null.
	 * 
	 */
	static Permissions getFilePermissionsSymlinks(String file)
	{
		RootTools.log("Checking permissions for " + file);
		if(RootTools.exists(file))
		{
			RootTools.log(file + " was found.");
			try
			{

				Command command = new Command(
						InternalVariables.FPS, "ls -l " + file,
						"busybox ls -l " + file,
						"/system/bin/failsafe/toolbox ls -l " + file,
						"toolbox ls -l " + file)
				{
					@Override
					public void output(int id, String line)
					{
						if(id == InternalVariables.FPS)
						{
							String symlink_final = "";

							String[] lineArray = line.split(" ");
							if(lineArray[0].length() != 10)
							{
								return;
							}

							RootTools.log("Line " + line);

							try
							{
								String[] symlink = line.split(" ");
								if(symlink[symlink.length - 2].equals("->"))
								{
									RootTools.log("Symlink found.");
									symlink_final = symlink[symlink.length - 1];
								}
							}
							catch (Exception e)
							{
							}

							try
							{
								InternalVariables.permissions = new InternalMethods().getPermissions(line);
								if(InternalVariables.permissions != null)
								{
									InternalVariables.permissions.setSymlink(symlink_final);
								}
							}
							catch (Exception e)
							{
								RootTools.log(e.getMessage());
							}
						}						
					}
					
				};
				Shell.startRootShell().add(command);
				command.waitForFinish();

				return InternalVariables.permissions;

			}
			catch (Exception e)
			{
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
     * @throws Exception
     *             if we cannot return the mount points.
     */
	protected static ArrayList<Mount> getMounts() throws Exception
	{
		LineNumberReader lnr = null;
		lnr = new LineNumberReader(new FileReader("/proc/mounts"));
		String line;
		ArrayList<Mount> mounts = new ArrayList<Mount>();
		while ((line = lnr.readLine()) != null)
		{
	
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
	}
    
    /**
     * This will tell you how the specified mount is mounted. rw, ro, etc...
     * <p/>
     * @param The mount you want to check
     * 
     * @return <code>String</code> What the mount is mounted as.
     * @throws Exception
     *             if we cannot determine how the mount is mounted.
     */
    static String getMountedAs(String path) throws Exception {
        InternalVariables.mounts = getMounts();
        if (InternalVariables.mounts != null) {
        	for (Mount mount : InternalVariables.mounts)
        	{
        		if (path.contains(mount.getMountPoint().getAbsolutePath()))
        		{
        			RootTools.log((String) mount.getFlags().toArray()[0]);
        			return (String) mount.getFlags().toArray()[0];
        		}
        	}
        	
        	throw new Exception();
        } 
        else 
        {
            throw new Exception();
        }
    }
    
    /**
     * This will return the environment variable $PATH
     * 
     * @return <code>Set<String></code> A Set of Strings representing the environment variable $PATH
     * @throws Exception
     *             if we cannot return the $PATH variable
     */
    static Set<String> getPath() throws Exception {
        if (InternalVariables.path != null) {
            return InternalVariables.path;
        } else {
            if (new InternalMethods().returnPath()) {
                return InternalVariables.path;
            } else {
                throw new Exception();
            }
        }
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
    static long getSpace(String path) {
        InternalVariables.getSpaceFor = path;
        boolean found = false;
        RootTools.log("Looking for Space");
        try {
            Command command = new Command(InternalVariables.GS, "df " + path)
            {

				@Override
				public void output(int id, String line)
				{
					if (id == InternalVariables.GS)
					{
						if (line.contains(command[0].substring(2, command[0].length()).trim())) {
		                    InternalVariables.space = line.split(" ");
		                }
					}					
				}
            };
            
            Shell.startRootShell().add(command);
            command.waitForFinish();
        } catch (Exception e) {}

        if (InternalVariables.space != null) {
            RootTools.log("First Method");

            for (String spaceSearch : InternalVariables.space) {

                RootTools.log(spaceSearch);

                if (found) {
                    return new InternalMethods().getConvertedSpace(spaceSearch);
                } else if (spaceSearch.equals("used,")) {
                    found = true;
                }
            }

            // Try this way
            int count = 0, targetCount = 3;

            RootTools.log("Second Method");

            if (InternalVariables.space[0].length() <= 5 ) {
                targetCount = 2;
            }

            for (String spaceSearch : InternalVariables.space) {

                RootTools.log(spaceSearch);
                if (spaceSearch.length() > 0) {
                    RootTools.log(spaceSearch + ("Valid"));
                    if (count == targetCount) {
                        return new InternalMethods().getConvertedSpace(spaceSearch);
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
     * @param The
     *            file to get the Symlink for. (must have absolute path)
     * 
     * @return <code>String</code> a String that represent the symlink for a specified file or an
     *         empty string if no symlink exists.
     */
    static String getSymlink(String file) {
        RootTools.log("Looking for Symlink for " + file);

        try {
        	final List<String> results = new ArrayList<String>();

        	Command command = new Command(InternalVariables.GSYM, "ls -l " + file)
        	{

				@Override
				public void output(int id, String line)
				{
					if (id == InternalVariables.GSYM)
					{
						if (!line.trim().equals(""))
						{
							results.add(line);
						}
					}					
				}
        	};
        	
        	Shell.startRootShell().add(command);
        	command.waitForFinish();
        	            
            String[] symlink = results.get(0).split(" ");
            if (symlink[symlink.length - 2].equals("->")) {
                RootTools.log("Symlink found.");
                
                String final_symlink = "";
                if (!symlink[symlink.length - 1].equals("") && !symlink[symlink.length - 1].contains("/"))
                {
                	//We assume that we need to get the path for this symlink as it is probably not absolute.
                	findBinary(symlink[symlink.length - 1]);
                	if (RootTools.lastFoundBinaryPaths.size() > 0)
                	{
                		//We return the first found location.
                		final_symlink = RootTools.lastFoundBinaryPaths.get(0) + "/" + symlink[symlink.length - 1];
                	}
                	else
                	{
                    	//we couldnt find a path, return the symlink by itself.
                    	final_symlink = symlink[symlink.length - 1];
                	}
                }
                else
                {
                	final_symlink = symlink[symlink.length - 1];
                }

                return final_symlink;
            }
        } catch (Exception e) {
        	if (RootTools.debugMode)
        		e.printStackTrace();
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
     * @param The
     *            path to search for Symlinks.
     * 
     * @return <code>ArrayList<Symlink></code> an ArrayList of the class Symlink.
     * @throws Exception
     *             if we cannot return the Symlinks.
     */
    static ArrayList<Symlink> getSymlinks(String path) throws Exception {

        // this command needs find
        if (!checkUtil("find")) {
            throw new Exception();
        }

        CommandCapture command = new CommandCapture(0, "find " + path + " -type l -exec ls -l {} \\; > /data/local/symlinks.txt;");
        Shell.startRootShell().add(command);
        command.waitForFinish();
        
        InternalVariables.symlinks = new InternalMethods().getSymLinks();
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
    static String getWorkingToolbox() {
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
     * @param updateSize
     *            size to Check (long)
     * @return <code>true</code> if the Update will fit on SDCard, <code>false</code> if not enough
     *         space on SDCard. Will also return <code>false</code>, if the SDCard is not mounted as
     *         read/write
     */
    public static boolean hasEnoughSpaceOnSdCard(long updateSize) {
        RootTools.log("Checking SDcard size and that it is mounted as RW");
        String status = Environment.getExternalStorageState();
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            return false;
        }
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return (updateSize < availableBlocks * blockSize);
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
    	
    	InternalVariables.found = false;
    	
        // only for busybox and toolbox
        if (!(box.endsWith("toolbox") || box.endsWith("busybox"))) {
            return false;
        }
        
        try {
        	
        	Command command = new Command(0, box.endsWith("toolbox") ? box + " " + util : box + " --list" ) {

				@Override
				public void output(int id, String line)
				{
					if (box.endsWith("toolbox")) {
                        if (line.contains("no such tool")) {
                        	InternalVariables.found = true;
                        }
                    } else if (box.endsWith("busybox")) {
                        // go through all lines of busybox --list
                        if (line.contains(util)) {
                            RootTools.log("Found util!");
                            InternalVariables.found = true;
                        }
                    }					
				}
        		
        	};
        	RootTools.getShell(true).add(command).waitForFinish(5000);
        	
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
    static boolean installBinary(Context context, int sourceId, String destName, String mode) {
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
     * This will let you know if an applet is available from BusyBox
     * <p/>
     * 
     * @param <code>String</code> The applet to check for.
     * 
     * @return <code>true</code> if applet is available, false otherwise.
     */
    public static boolean isAppletAvailable(String Applet, String binaryPath) {
        try {
            for (String applet : getBusyBoxApplets(binaryPath)) {
                if (applet.equals(Applet)) {
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
     * @param processName
     *            name of process to check
     * @return <code>true</code> if process was found
     * @throws TimeoutException
     *             (Could not determine if the process is running)
     */
    static boolean isProcessRunning(final String processName) {
        RootTools.log("Checks if process is running: " + processName);

        boolean processRunning = false;
        try {
            Result result = new Result() {
                @Override
                public void process(String line) throws Exception {
                    if (line.contains(processName)) {
                        setData(1);
                    }
                }

                @Override
                public void onFailure(Exception ex) {
                    setError(1);
                }

                @Override
                public void onComplete(int diag) {
                }

                @Override
                public void processError(String arg0) throws Exception {
                }

            };
            RootTools.sendShell(new String[] { "ps" }, 1, result, -1);

            if (result.getError() == 0) {
                // if data has been set process is running
                if (result.getData() != null) {
                    processRunning = true;
                }
            }
        } catch (Exception e) {
            RootTools.log(e.getMessage());
        }

        return processRunning;
    }

	/**
	 * This method can be used to kill a running process
	 * 
	 * @param processName
	 *            name of process to kill
	 * @return <code>true</code> if process was found and killed successfully
	 */
	static boolean killProcess(final String processName)
	{
		RootTools.log("Killing process " + processName);

		boolean processKilled = false;
		try
		{
			Result result = new Result()
			{
				@Override
				public void process(String line) throws Exception
				{
					if(line.contains(processName))
					{
						Matcher psMatcher = InternalVariables.psPattern.matcher(line);

						try
						{
							if(psMatcher.find())
							{
								String pid = psMatcher.group(1);
								// concatenate to existing pids, to use later in
								// kill
								if(getData() != null)
								{
									setData(getData() + " " + pid);
								}
								else
								{
									setData(pid);
								}
								RootTools.log("Found pid: " + pid);
							}
							else
							{
								RootTools.log("Matching in ps command failed!");
							}
						}
						catch (Exception e)
						{
							RootTools.log("Error with regex!");
							e.printStackTrace();
						}
					}
				}

				@Override
				public void onFailure(Exception ex)
				{
					setError(1);
				}

				@Override
				public void onComplete(int diag)
				{
				}

				@Override
				public void processError(String arg0) throws Exception
				{
				}

			};
			RootTools.sendShell(new String[] { "ps" }, 1, result, -1);

			if(result.getError() == 0)
			{
				// get all pids in one string, created in process method
				String pids = (String) result.getData();

				// kill processes
				if(pids != null)
				{
					try
					{
						// example: kill -9 1234 1222 5343
						RootTools.sendShell(new String[] { "kill -9 " + pids }, 1, -1);
						processKilled = true;
					}
					catch (Exception e)
					{
						RootTools.log(e.getMessage());
					}
				}
			}
		}
		catch (Exception e)
		{
			RootTools.log(e.getMessage());
		}

		return processKilled;
	}

    /**
     * This will launch the Android market looking for BusyBox
     * 
     * @param activity
     *            pass in your Activity
     */
    static void offerBusyBox(Activity activity) {
        RootTools.log("Launching Market for BusyBox");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=stericson.busybox"));
        activity.startActivity(i);
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
    static Intent offerBusyBox(Activity activity, int requestCode) {
        RootTools.log("Launching Market for BusyBox");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=stericson.busybox"));
        activity.startActivityForResult(i, requestCode);
        return i;
    }

    /**
     * This will launch the Android market looking for SuperUser
     * 
     * @param activity
     *            pass in your Activity
     */
    static void offerSuperUser(Activity activity) {
        RootTools.log("Launching Market for SuperUser");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.noshufou.android.su"));
        activity.startActivity(i);
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
    static Intent offerSuperUser(Activity activity, int requestCode) {
        RootTools.log("Launching Market for SuperUser");
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.noshufou.android.su"));
        activity.startActivityForResult(i, requestCode);
        return i;
    }
}
