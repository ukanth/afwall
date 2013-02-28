/*
 * Copyright (C) 2012 Jorrit "Chainfire" Jongma
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

package eu.chainfire.libsuperuser;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.os.Looper;
import dev.ukanth.ufirewall.BuildConfig;

/**
 * Class providing functionality to execute commands in a (root) shell 
 */
public class Shell {
	/**
	 * Runs commands using the supplied shell, and returns the output, or null in
	 * case of errors. 
	 * 
	 * Note that due to compatibility with older Android versions,
	 * wantSTDERR is not implemented using redirectErrorStream, but rather appended
	 * to the output. STDOUT and STDERR are thus not guaranteed to be in the correct
	 * order in the output.
	 * 
	 * Note as well that this code will intentionally crash when run in debug mode 
	 * from the main thread of the application. You should always execute shell 
	 * commands from a background thread.
	 * 
	 * When in debug mode, the code will also excessively log the commands passed to
	 * and the output returned from the shell.
	 * 
	 * Though this function uses background threads to gobble STDOUT and STDERR so
	 * a deadlock does not occur if the shell produces massive output, the output is
	 * still stored in a List<String>, and as such doing something like "ls -lR /"
	 * will probably have you run out of memory.
	 * 
	 * @param shell The shell to use for executing the commands
	 * @param commands The commands to execute
	 * @param wantSTDERR Return STDERR in the output ? 
	 * @return Output of the commands, or null in case of an error
	 */
	public static List<String> run(String shell, String[] commands, boolean wantSTDERR) {
		String shellUpper = shell.toUpperCase();
		
		if (BuildConfig.DEBUG) {
			// check if we're running in the main thread, and if so, crash if we're in debug mode,
			// to let the developer know attention is needed here.
			
			if (Looper.myLooper() == Looper.getMainLooper()) {
				Debug.log("Application attempted to run a shell command from the main thread");
				throw new ShellOnMainThreadException();
			}
			
			Debug.log(String.format("[%s%%] START", shellUpper));			
		}
		
		List<String> res = Collections.synchronizedList(new ArrayList<String>());
		Process process = null;
		try {
			// setup our process, retrieve STDIN stream, and STDOUT/STDERR gobblers
			process = Runtime.getRuntime().exec(shell);
			DataOutputStream STDIN = new DataOutputStream(process.getOutputStream());
			StreamGobbler STDOUT = new StreamGobbler(shellUpper + "-", process.getInputStream(), res);
			StreamGobbler STDERR = new StreamGobbler(shellUpper + "*", process.getErrorStream(), wantSTDERR ? res : null);
			
			// start gobbling and write our commands to the shell
			STDOUT.start();
			STDERR.start();
			for (String write : commands) {
				//if (BuildConfig.DEBUG) Debug.log(String.format("[%s+] %s", shellUpper, write));
				STDIN.writeBytes(write + "\n");
				STDIN.flush();
			}
			STDIN.writeBytes("exit\n");
			STDIN.flush();
			
			// wait for our process to finish, while we gobble away in the background
			process.waitFor();
			
			// make sure our threads are done gobbling, our streams are closed, and the process is 
			// destroyed - while the latter two shouldn't be needed in theory, and may even produce 
			// warnings, in "normal" Java they are required for guaranteed cleanup of resources, so
			// lets be safe and do this on Android as well
			try { 
				STDIN.close();
			} catch (IOException e) { 				
			}
			STDOUT.join();
			STDERR.join();
			
			// in case of su, 255 usually indicates access denied
			if (shell.equals("su") && (process.exitValue() == 255)) {
				res = null;
			}			
		} catch (IOException e) {
			// shell probably not found
			res = null;
		} catch (InterruptedException e) {
			// this should really be re-thrown
			res = null;
		} catch (Exception e) {
			res = null;
		}
		finally {
	           destroyProcess(process);           
		}
			
		//if (BuildConfig.DEBUG) Debug.log(String.format("[%s%%] END", shell.toUpperCase()));
		return res;
	}
	
	private static void destroyProcess(Process process) {
		if(process == null) return;
        try {
            if (process != null) {
                // use exitValue() to determine if process is still running. 
                process.exitValue();
            }
        } catch (IllegalThreadStateException e) {
            // process is still running, kill it.
            process.destroy();
        } catch (Exception e) {
            // process is still running, kill it.
            process.destroy();
        }
    }
	
	/**
	 * This class provides utility functions to easily execute commands using SH
	 */
	public static class SH {
		/**
		 * Runs command and return output
		 * 
		 * @param command The command to run
		 * @return Output of the command, or null in case of an error
		 */
		public static List<String> run(String command) {
			return Shell.run("sh", new String[] { command }, false);
		}
		
		/**
		 * Runs commands and return output
		 * 
		 * @param commands The commands to run
		 * @return Output of the commands, or null in case of an error
		 */
		public static List<String> run(List<String> commands) {
			return Shell.run("sh", commands.toArray(new String[commands.size()]), false);
		}

		/**
		 * Runs command and return output
		 * 
		 * @param commands The commands to run
		 * @return Output of the commands, or null in case of an error
		 */
		public static List<String> run(String[] commands) {
			return Shell.run("sh", commands, false);
		}
	}

	/**
	 * This class provides utility functions to easily execute commands using SU
	 * (root shell), as well as detecting whether or not root is available, and
	 * if so which version.
	 */
	public static class SU {
		/**
		 * Runs command as root (if available) and return output
		 * 
		 * @param command The command to run
		 * @return Output of the command, or null if root isn't available or in case of an error
		 */
		public static List<String> run(String command) {
			return Shell.run("su", new String[] { command }, false);
		}
		
		/**
		 * Runs commands as root (if available) and return output
		 * 
		 * @param command The commands to run
		 * @return Output of the commands, or null if root isn't available or in case of an error
		 */
		public static List<String> run(List<String> commands) {
			return Shell.run("su", commands.toArray(new String[commands.size()]), false);
		}

		/**
		 * Runs commands as root (if available) and return output
		 * 
		 * @param command The commands to run
		 * @return Output of the commands, or null if root isn't available or in case of an error
		 */
		public static List<String> run(String[] commands) {
			return Shell.run("su", commands, false);
		}
		
		/**
		 * Detects whether or not superuser access is available, by checking the output
		 * of the "id" command if available, checking if a shell runs at all otherwise
		 * 
		 * @return True if superuser access available
		 */
		public static boolean available() {
			// this is only one of many ways this can be done
			
			List<String> ret = run(new String[] { 
				"id",
				"echo -EOC-"
			});
			if (ret == null) return false;
			
			for (String line : ret) {
				if (line.contains("uid=")) {
					// id command is working, let's see if we are actually root
					return line.contains("uid=0");
				} else if (line.contains("-EOC-")) {
					// if we end up here, the id command isn't present, but at
					// least the su commands starts some kind of shell, let's
					// hope it has root priviliges - no way to know without
					// additional native binaries
					return true;
				}
			}
			return false;
		}
		
		/**
		 * Detects the version of the su binary installed (if any), if supported by the binary.
		 * Most binaries support two different version numbers, the public version that is
		 * displayed to users, and an internal version number that is used for version number 
		 * comparisons. Returns null if su not available or retrieving the version isn't supported.
		 * 
		 * Note that su binary version and GUI (APK) version can be completely different.
		 * 
		 * @param internal Request human-readable version or application internal version
		 * @return String containing the su version or null
		 */
		public static String version(boolean internal) {
			// we add an additional exit call, because the command
			// line options are not available in all su versions,
			// thus potentially launching a shell instead
			
			List<String> ret = Shell.run("sh", new String[] {
				internal ? "su -V" : "su -v",
				"exit"
			}, false);
			if (ret == null) return null;
				
			for (String line : ret) {
				if (!internal) {
					if (line.contains(".")) return line;					
				} else { 
					try {
						if (Integer.parseInt(line) > 0) return line;
					} catch(NumberFormatException e) {					
					}
				}
			}
			return null;
		}
	}
}