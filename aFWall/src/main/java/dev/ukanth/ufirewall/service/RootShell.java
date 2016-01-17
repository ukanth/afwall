/**
 * Keep a persistent root shell running in the background
 *
 * Copyright (C) 2013  Kevin Cernekee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Cernekee
 * @version 1.0
 */

package dev.ukanth.ufirewall.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.SynchronousQueue;

import dev.ukanth.ufirewall.log.Log;
import eu.chainfire.libsuperuser.Debug;
import eu.chainfire.libsuperuser.Shell;

import static dev.ukanth.ufirewall.service.RootShell.ShellState.INIT;

public class RootShell extends Service {

	public static final String TAG = "AFWall";

	/* write command completion times to logcat */
	private static final boolean enableProfiling = false;

	private static Shell.Interactive rootSession;
	private static Context mContext;

	public enum ShellState {
		INIT,
		READY,
		BUSY,
		FAIL
	}

	private static ShellState rootState = INIT;

	private final static int MAX_RETRIES = 10;

	private static LinkedList<RootCommand> waitQueue = new LinkedList<RootCommand>();

	private SynchronousQueue workingCommand = new SynchronousQueue();

	public final static int EXIT_NO_ROOT_ACCESS = -1;

	public final static int NO_TOAST = -1;

	public static class RootCommand {
		private List<String> script;

		private Callback cb = null;
		private int successToast = NO_TOAST;
		private int failureToast = NO_TOAST;
		private boolean reopenShell = false;
		private int retryExitCode = -1;

		private int commandIndex;
		private boolean ignoreExitCode;
		private Date startTime;
		private int retryCount;

		public StringBuilder res;
		public String lastCommand;
		public StringBuilder lastCommandResult;
		public int exitCode;
		public boolean done = false;
		/*private boolean startCheck = false;
		

		public boolean isStartCheck() {
			return startCheck;
		}

		public RootCommand setStartCheck(boolean startCheck) {
			this.startCheck = startCheck;
			return this;
		}*/

		public static abstract class Callback {

			/**
			 * Optional user-specified callback
			 */
			public abstract void cbFunc(RootCommand state);
		}

		/**
		 * Set callback to run after command completion
		 * 
		 * @param cb Callback object, with cbFunc() populated
		 * @return RootCommand builder object
		 */
		public RootCommand setCallback(Callback cb) {
			this.cb = cb;
			return this;
		}

		/**
		 * Tell RootShell to display a toast message on success
		 * 
		 * @param resId Resource ID of the toast string
		 * @return RootCommand builder object
		 */
		public RootCommand setSuccessToast(int resId) {
			this.successToast = resId;
			return this;
		}

		/**
		 * Tell RootShell to display a toast message on failure
		 * 
		 * @param resId Resource ID of the toast string
		 * @return RootCommand builder object
		 */
		public RootCommand setFailureToast(int resId) {
			this.failureToast = resId;
			return this;
		}

		/**
		 * Tell RootShell whether or not it should try to open a new root shell if the last attempt
		 * died.  To avoid "thrashing" it might be best to only try this in response to a user
		 * request 
		 * 
		 * @param reopenShell true to attempt reopening a failed shell
		 * @return RootCommand builder object
		 */
		public RootCommand setReopenShell(boolean reopenShell) {
			this.reopenShell = reopenShell;
			return this;
		}

		/**
		 * Capture the command output in this.res
		 * 
		 * @param enableLog true to enable logging
		 * @return RootCommand builder object
		 */
		public RootCommand setLogging(boolean enableLog) {
			if (enableLog) {
				this.res = new StringBuilder();
			} else {
				this.res = null;
			}
			return this;
		}

		/**
		 * Retry a failed command on a specific exit code
		 * 
		 * @param retryExitCode code that indicates a transient failure
		 * @return RootCommand builder object
		 */
		public RootCommand setRetryExitCode(int retryExitCode) {
			this.retryExitCode = retryExitCode;
			return this;
		}

		/**
		 * Run a series of commands as root; call cb.cbFunc() when complete
		 *
		 * @param ctx Context object used to create toasts
		 * @param script List of commands to run as root
		 */
		public final void run(Context ctx, List<String> script) {
			RootShell.runScriptAsRoot(ctx, script, this);
		}

		/**
		 * Run a single command as root; call cb.cbFunc() when complete
		 *
		 * @param ctx Context object used to create toasts
		 * @param cmd Command to run as root
		 */
		public final void run(Context ctx, String cmd) {
			List<String> script = new ArrayList<String>();
			script.add(cmd);
			RootShell.runScriptAsRoot(ctx, script, this);
		}
	}

	private static void complete(final RootCommand state, int exitCode) {
		if (enableProfiling) {
			Log.d(TAG, "RootShell: " + state.script.size() + " commands completed in " +
					(new Date().getTime() - state.startTime.getTime()) + " ms");
		}

		state.exitCode = exitCode;
		state.done = true;
		if (state.cb != null) {
			state.cb.cbFunc(state);
		}

		if (exitCode == 0 && state.successToast != NO_TOAST) {
			showToastUIThread(mContext.getString(state.successToast));
		} else if (exitCode != 0 && state.failureToast != NO_TOAST) {
			showToastUIThread(mContext.getString(state.failureToast));
		}
	}

	private static void runNextSubmission() {
		do {
			RootCommand state;
			try {
				state = waitQueue.remove();
			} catch (NoSuchElementException e) {
				// nothing left to do
				if (rootState == ShellState.BUSY) {
					rootState = ShellState.READY;
				}
				break;
			}

			if (enableProfiling) {
				state.startTime = new Date();
			}

			if (rootState == ShellState.FAIL) {
				// if we don't have root, abort all queued commands
				complete(state, EXIT_NO_ROOT_ACCESS);
				continue;
			} else if (rootState == ShellState.READY) {
				rootState = ShellState.BUSY;
				submitNextCommand(state);
			}
		} while (false);
	}

	private static void submitNextCommand(final RootCommand state) {
		String s = state.script.get(state.commandIndex);

		if(s != null) {
			if (s.startsWith("#NOCHK# ")) {
				s = s.replaceFirst("#NOCHK# ", "");
				state.ignoreExitCode = true;
			} else {
				state.ignoreExitCode = false;
			}
			state.lastCommand = s;
			state.lastCommandResult = new StringBuilder();

			Shell.OnCommandResultListener listener = new Shell.OnCommandResultListener() {

				@Override
				public void onCommandResult(int commandCode, int exitCode,
											List<String> output) {

					if(output != null) {
						ListIterator<String> iter = output.listIterator();
						while (iter.hasNext()) {
							String line = iter.next();
							if (line != null && !line.equals("")) {
								if (state.res != null) {
									state.res.append(line + "\n");
								}
								state.lastCommandResult.append(line + "\n");
							}
						}
					}


					if (exitCode >= 0 && exitCode == state.retryExitCode && state.retryCount < MAX_RETRIES) {
						state.retryCount++;
						Log.d(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
								", retrying (attempt " + state.retryCount + "/" + MAX_RETRIES + ")");
						submitNextCommand(state);
						return;
					}

					state.commandIndex++;
					state.retryCount = 0;

					boolean errorExit = exitCode != 0 && !state.ignoreExitCode;
					if (state.commandIndex >= state.script.size() || errorExit) {
						complete(state, exitCode);
						if (exitCode < 0) {
							rootState = ShellState.FAIL;
							Log.e(TAG, "libsuperuser error " + exitCode + " on command '" + state.lastCommand + "'");
						} else {
							if (errorExit) {
								Log.i(TAG, "command '" + state.lastCommand + "' exited with status " + exitCode +
										"\nOutput:\n" + state.lastCommandResult);
							}
							rootState = ShellState.READY;
						}
						runNextSubmission();
					} else {
						submitNextCommand(state);
					}
				}
			};
			if(listener != null && s!= null) {
				try {
					rootSession.addCommand(s, 0, listener);
				} catch (NullPointerException e) {
					Log.d(TAG, "Unable to add commands to session");
				}
			}
		}
	}

	private static void setupLogging() {
		Debug.setDebug(true);
		Debug.setLogTypeEnabled(Debug.LOG_ALL, false);
		Debug.setLogTypeEnabled(Debug.LOG_GENERAL, true);
		Debug.setSanityChecksEnabled(true);
		Debug.setOnLogListener(new Debug.OnLogListener() {
			@Override
			public void onLog(int type, String typeIndicator, String message) {
				Log.i(TAG, "[libsuperuser] " + message);
			}
		});
	}

	private static void startShellInBackground() {
		Log.d(TAG, "Starting root shell...");
		setupLogging();
		rootSession = new Shell.Builder().
				useSU().
				setWantSTDERR(true).
				setWatchdogTimeout(5).
				open(new Shell.OnCommandResultListener() {
					public void onCommandResult(int commandCode, int exitCode, List<String> output) {
						if (exitCode < 0) {
							Log.e(TAG, "Can't open root shell: exitCode " + exitCode);
							rootState = ShellState.FAIL;
						} else {
							Log.d(TAG, "Root shell is open");
							rootState = ShellState.READY;
						}
						runNextSubmission();
					}
				});
	}

	private static void reOpenShell(Context ctx) {
		rootState = ShellState.BUSY;
		startShellInBackground();
		Intent intent = new Intent(ctx, RootShell.class);
		ctx.startService(intent);
	}
	private static void runScriptAsRoot(Context ctx, List<String> script, RootCommand state) {
		state.script = script;
		state.commandIndex = 0;
		state.retryCount = 0;

		if (mContext == null) {
			mContext = ctx.getApplicationContext();
		}

		waitQueue.add(state);

		if (rootState == ShellState.INIT || (rootState == ShellState.FAIL && state.reopenShell)) {
			reOpenShell(ctx);
		} else if (rootState != ShellState.BUSY) {
			runNextSubmission();
		}
	}

	private final IBinder mBinder = new Binder();

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	private static void showToastUIThread(final String msg) {
		try {
			Thread thread = new Thread() {
				public void run() {
					Looper.prepare();

					final Handler handler = new Handler();
					handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							Toast.makeText(mContext,msg,Toast.LENGTH_LONG).show();
							handler.removeCallbacks(this);
							Looper.myLooper().quit();
						}
					}, 2000);
					Looper.loop();
				}
			};
			thread.start();
		}catch(Exception e) {}
	}
}