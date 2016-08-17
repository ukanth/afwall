package dev.ukanth.ufirewall.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Environment;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.ukanth.ufirewall.R;

/**
 * Created by ukanth on 18/7/15.
 */
public class FileDialog {
    private static final String PARENT_DIR = "..";
    private final String TAG = getClass().getName();

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    private boolean flag;
    private String[] fileList;
    private File currentPath;

    public interface FileSelectedListener {
        void fileSelected(File file);
    }
    public interface DirectorySelectedListener {
        void directorySelected(File directory);
    }
    private ListenerList<FileSelectedListener> fileListenerList = new ListenerList<FileDialog.FileSelectedListener>();
    private ListenerList<DirectorySelectedListener> dirListenerList = new ListenerList<FileDialog.DirectorySelectedListener>();
    private final Activity activity;
    private boolean selectDirectoryOption;
    private String[] fileEndsWith;

    /**
     * @param activity
     * @param path
     *
     */
    public FileDialog(Activity activity, File path, boolean flag) {
        this.activity = activity;
        if (!path.exists()) path = Environment.getExternalStorageDirectory();
        setFlag(flag);
        loadFileList(path,flag);
    }

    /**
     * @return file dialog
     */
    public Dialog createFileDialog() {
        Dialog dialog = null;
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(activity);

        builder.setTitle(currentPath.getPath());
        if (selectDirectoryOption) {
            builder.setPositiveButton(activity.getString(R.string.select_dir), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                   // Log.d(TAG, currentPath.getPath());
                    fireDirectorySelectedEvent(currentPath);
                }
            });
        }

        builder.setItems(fileList, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String fileChosen = fileList[which];
                File chosenFile = getChosenFile(fileChosen);
                if (chosenFile.isDirectory()) {
                    loadFileList(chosenFile,flag);
                    dialog.cancel();
                    dialog.dismiss();
                    showDialog();
                } else fireFileSelectedEvent(chosenFile);
            }
        });

        dialog = builder.show();
        return dialog;
    }


    public void addFileListener(FileSelectedListener listener) {
        fileListenerList.add(listener);
    }

    public void removeFileListener(FileSelectedListener listener) {
        fileListenerList.remove(listener);
    }

    public void setSelectDirectoryOption(boolean selectDirectoryOption) {
        this.selectDirectoryOption = selectDirectoryOption;
    }

    public void addDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.add(listener);
    }

    public void removeDirectoryListener(DirectorySelectedListener listener) {
        dirListenerList.remove(listener);
    }

    /**
     * Show file dialog
     */
    public void showDialog() {
        createFileDialog().show();
    }

    private void fireFileSelectedEvent(final File file) {
        fileListenerList.fireEvent(new ListenerList.FireHandler<FileSelectedListener>() {
            public void fireEvent(FileSelectedListener listener) {
                listener.fileSelected(file);
            }
        });
    }

    private void fireDirectorySelectedEvent(final File directory) {
        dirListenerList.fireEvent(new ListenerList.FireHandler<DirectorySelectedListener>() {
            public void fireEvent(DirectorySelectedListener listener) {
                listener.directorySelected(directory);
            }
        });
    }

    private void loadFileList(File path,final boolean flag) {
        this.currentPath = path;
        //String afwallDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/afwall/";
        List<String> r = new ArrayList<String>();
        if (path.exists()) {
            if (path.getParentFile() != null) r.add(PARENT_DIR);
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String filename) {
                    File sel = new File(dir, filename);
                    if (!sel.canRead()) return false;
                    if (selectDirectoryOption) return sel.isDirectory();

                    //backup.json - [a-z]+.json
                    else {
                        boolean endsWith;
                        if(flag) {
                            Pattern p1 = Pattern.compile("[a-z]+.json");
                            Matcher m1 = p1.matcher(filename);

                            Pattern p2 = Pattern.compile("[a-z]+-[a-z]+-\\d+-\\S*");
                            Matcher m2 = p2.matcher(filename);
                            if(m2.matches() || m1.matches()) {
                                endsWith = true;
                            } else {
                                endsWith = false;
                            }
                        } else {
                            Pattern p1 = Pattern.compile("[a-z]+_[a-z]+.json");
                            Matcher m1 = p1.matcher(filename);

                            Pattern p2 = Pattern.compile("[a-z]+-[a-z]+-[a-z]+-\\d+-\\S*");
                            Matcher m2 = p2.matcher(filename);
                            if(m2.matches() || m1.matches()) {
                                endsWith = true;
                            } else {
                                endsWith = false;
                            }
                        }
                       // boolean endsWith = fileEndsWith != null ? filename.contains(fileEndsWith) : true;
                        return endsWith || sel.isDirectory();
                    }
                }
            };
            String[] fileList1 = path.list(filter);
            if(fileList1 != null) {
                for (String file : fileList1) {
                    r.add(file);
                }
            }

        }
        if(r != null && r.size() > 0) {
            fileList = (String[]) r.toArray(new String[]{});
        }
    }

    private File getChosenFile(String fileChosen) {
        if (fileChosen.equals(PARENT_DIR)) return currentPath.getParentFile();
        else return new File(currentPath, fileChosen);
    }

    public void setFileEndsWith(String[] fileEndsWith,String notContains) {
        this.fileEndsWith = fileEndsWith != null ? fileEndsWith : new String[]{ "" };
    }
}

class ListenerList<L> {
    private List<L> listenerList = new ArrayList<L>();

    public interface FireHandler<L> {
        void fireEvent(L listener);
    }

    public void add(L listener) {
        listenerList.add(listener);
    }

    public void fireEvent(FireHandler<L> fireHandler) {
        List<L> copy = new ArrayList<L>(listenerList);
        for (L l : copy) {
            fireHandler.fireEvent(l);
        }
    }

    public void remove(L listener) {
        listenerList.remove(listener);
    }

    public List<L> getListenerList() {
        return listenerList;
    }
}