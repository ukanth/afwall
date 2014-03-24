/**
 * 
 * Log Service
 * 
 * Copyright (C) 2014  Umakanthan Chandran
 * 
 * Originally copied from NetworkLog (C) 2012 Pragmatic Software (MPL2.0)
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
 * @author Umakanthan Chandran
 * @version 1.0
 */

package dev.ukanth.ufirewall.log;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import dev.ukanth.ufirewall.Api;
import dev.ukanth.ufirewall.R;

public class LogService extends Service {
	
	private final IBinder mBinder = new Binder();
	
	private boolean mRunning = false;

	private ShellCommand loggerCommand;
	
	private Handler handler;
	
	public static Toast toast;
	public static TextView toastTextView;
	public static CharSequence toastText;
	public static boolean toastEnabled;
	public static int toastDuration;
	public static int toastPosition;
	public static int toastDefaultYOffset;
	public static int toastYOffset;
	public static int toastOpacity;
	public static boolean toastShowAddress;
	
	
	private static Runnable showOnlyToastRunnable;
	private static CancelableRunnable showToastRunnable;
	private static View toastLayout;

	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	public static String klogPath;
	
	private static abstract class CancelableRunnable implements Runnable {
	    public boolean cancel;
	}

	public void onCreate() {
		super.onCreate();
        mRunning = false;
	}
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (!mRunning) {
            mRunning = true;
            klogPath = Api.getKLogPath(getApplicationContext());
    		handler = new Handler();
    		loggerCommand = new ShellCommand(
    				new String[] { "su", "-c",  klogPath + " --skip-first"},
    				"LogService");
    		loggerCommand.start(false);
    		if (loggerCommand.error != null) {
    		} else {
    			NetworkLogger logger = new NetworkLogger();
    			new Thread(logger, "NetworkLogger").start();
    		}
        }
        return super.onStartCommand(intent, flags, startId);
    }
	
	
	public static void showToast(final Context context, final Handler handler, final CharSequence text, boolean cancel) {
	    if(showToastRunnable == null) {
	      showToastRunnable = new CancelableRunnable() {
	        public void run() {
	          if(cancel && toast != null) {
	            toast.cancel();
	          }

	          if(cancel || toast == null) {
	            toastLayout = ((LayoutInflater)context.getSystemService(LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_toast, null);
	            toastTextView = (TextView) toastLayout.findViewById(R.id.toasttext);
	            if(android.os.Build.VERSION.SDK_INT > 10 || toast == null) {
	              toast = new Toast(context);
	            }
	            toastDefaultYOffset = toast.getYOffset();
	            toast.setView(toastLayout);
	          }

	          switch(toastDuration) {
	            case 3500:
	              toast.setDuration(Toast.LENGTH_LONG);
	              break;
	            case 7000:
	              toast.setDuration(Toast.LENGTH_LONG);

	              if(showOnlyToastRunnable == null) {
	                showOnlyToastRunnable  = new Runnable() {
	                  public void run() {
	                    toast.show();
	                  }
	                };
	              }

	              handler.postDelayed(showOnlyToastRunnable, 3250);
	              break;
	            default:
	              toast.setDuration(Toast.LENGTH_SHORT);
	          }

	          switch(toastPosition) {
	            case 1:
	              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, toastYOffset);
	              break;
	            case 2:
	              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastYOffset);
	              break;
	            default:
	              toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, toastDefaultYOffset);
	              break;
	          }

	          toastTextView.setText(android.text.Html.fromHtml(toastText.toString()));
	          toast.show();
	        }
	      };
	    }

	    showToastRunnable.cancel = cancel;
	    toastText = text;
	    handler.post(showToastRunnable);
	  }
	

	public class NetworkLogger implements Runnable {
		boolean running = false;

		public void stop() {
			running = false;
		}

		public void run() {
			String result;
			running = true;

			while (true) {
				while (running && loggerCommand.checkForExit() == false) {
					if (loggerCommand.stdoutAvailable()) {
						result = loggerCommand.readStdout();
					} else {
						try {
							Thread.sleep(500);
						} catch (Exception e) {
						}
						continue;
					}

					if (running == false) {
						break;
					}

					if (result == null) {
						break;
					}
					showToast(getApplicationContext(), handler, LogInfo.parseLogs(result,getApplicationContext()), false);
					/*final String data = result;
					handler.post(new Runnable() {
					    public void run() {
					        Toast toast = Toast.makeText(LogService.this, LogInfo.parseLogs(data, getApplicationContext()), Toast.LENGTH_LONG);
					        toast.show();
					    }
					 });*/
					
				}

				if (running != false) {
					try {
						Thread.sleep(10000);
					} catch (Exception e) {
						// ignored
					}
				} else {
					break;
				}
			}
		}
	}	


}
