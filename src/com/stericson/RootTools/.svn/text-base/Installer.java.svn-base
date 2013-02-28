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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.util.Log;

class Installer {

    //-------------
    //# Installer #
    //-------------

    private static final String LOG_TAG = "RootTools::Installer";

    private static final String BOGUS_FILE_NAME = "bogus";

    private Context context;
    private String  filesPath;

    public Installer(Context context)
            throws IOException {

        this.context   = context;
        this.filesPath = context.getFilesDir().getCanonicalPath();
    }

    /**
     * This method can be used to unpack a binary from the raw resources folder and store it in
     * /data/data/app.package/files/
     * This is typically useful if you provide your own C- or C++-based binary.
     * This binary can then be executed using sendShell() and its full path.
     *
     * @param sourceId  resource id; typically <code>R.raw.id</code>
     *
     * @param destName  destination file name; appended to /data/data/app.package/files/
     *
     * @param mode      chmod value for this file
     *
     * @return          a <code>boolean</code> which indicates whether or not we were
     *                  able to create the new file.
     */
    protected boolean installBinary(int sourceId, String destName, String mode) {
        File mf = new File(filesPath + File.separator + destName);
        if(!mf.exists()) {
            // First, does our files/ directory even exist?
            // We cannot wait for android to lazily create it as we will soon
            // need it.
            try {
                FileInputStream fis = context.openFileInput(BOGUS_FILE_NAME);
                fis.close();
            } catch (FileNotFoundException e) {
                FileOutputStream fos = null;
                try {
                    fos = context.openFileOutput("bogus", Context.MODE_PRIVATE);
                    fos.write("justcreatedfilesdirectory".getBytes());
                } catch (Exception ex) {
                	if (RootTools.debugMode) {
                		Log.e(LOG_TAG, ex.toString());
                	}
                    return false;
                }
                finally {
                    if(null != fos) {
                        try {
                            fos.close();
                            context.deleteFile(BOGUS_FILE_NAME);
                        } catch (IOException e1) {}
                    }
                }
            }
            catch(IOException ex) {
            	if (RootTools.debugMode) {
            		Log.e(LOG_TAG, ex.toString());
            	}
                return false;
            }

            // Only now can we start creating our actual file
            InputStream iss = context.getResources().openRawResource(sourceId);
            FileOutputStream oss = null;
            try {
                oss = new FileOutputStream(mf);
                byte [] buffer = new byte[4096];
                int len;
                try {
                    while(-1 != (len = iss.read(buffer))) {
                        oss.write(buffer, 0, len);
                    }
                } catch (IOException ex) {
                	if (RootTools.debugMode) {
                		Log.e(LOG_TAG, ex.toString());
                	}
                    return false;
                }
            } catch (FileNotFoundException ex) {
            	if (RootTools.debugMode) {
            		Log.e(LOG_TAG, ex.toString());
            	}
                return false;
            }
            finally {
                if(oss != null) {
                    try {
                        oss.close();
                    } catch (IOException e) {}
                }
            }
            try {
                iss.close();
            } catch (IOException ex) {
            	if (RootTools.debugMode) {
            		Log.e(LOG_TAG, ex.toString());
            	}
                return false;
            }

            try {
            	CommandCapture command = new CommandCapture(0, "chmod " + mode + " " + filesPath + File.separator + destName);
            	Shell.startRootShell().add(command);
            	command.waitForFinish();
			} catch (Exception e) {}
        }
        return true;
    }

    protected boolean isBinaryInstalled(String destName) {
        boolean installed = false;
        File mf = new File(filesPath + File.separator + destName);
        if(mf.exists()) {
            installed = true;
            // TODO: pass mode as argument and check it matches
        }
        return installed;
    }
}
