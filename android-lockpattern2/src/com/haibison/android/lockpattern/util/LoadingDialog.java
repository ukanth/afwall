/*
 *   Copyright 2012 Hai Bison
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.haibison.android.lockpattern.util;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.haibison.android.lockpattern.R;

/**
 * An implementation of {@link AsyncTask}, used to show {@link ProgressDialog}
 * while doing some background tasks.
 * 
 * @author Hai Bison
 */
public abstract class LoadingDialog<Params, Progress, Result> extends
        AsyncTask<Params, Progress, Result> {

    private static final String CLASSNAME = LoadingDialog.class.getName();

    private final ProgressDialog mDialog;
    /**
     * Default is {@code 500}ms
     */
    private int mDelayTime = 500;
    /**
     * Flag to use along with {@link #mDelayTime}
     */
    private boolean mFinished = false;

    private Throwable mLastException;

    /**
     * Creates new instance.
     * 
     * @param context
     *            the context.
     * @param cancelable
     *            whether the user can cancel the dialog by tapping outside of
     *            it, or by pressing Back button.
     */
    public LoadingDialog(Context context, boolean cancelable) {
        this(context, cancelable, R.string.alp_42447968_loading);
    }// LoadingDialog()

    /**
     * Creates new instance.
     * 
     * @param context
     *            the context.
     * @param cancelable
     *            whether the user can cancel the dialog by tapping outside of
     *            it, or by pressing Back button.
     * @param msgId
     *            the resource ID of the message to be displayed.
     */
    public LoadingDialog(Context context, boolean cancelable, int msgId) {
        this(context, cancelable, context.getString(msgId));
    }// LoadingDialog()

    /**
     * Creates new instance.
     * 
     * @param context
     *            the context.
     * @param cancelable
     *            whether the user can cancel the dialog by tapping outside of
     *            it, or by pressing Back button.
     * @param msg
     *            the message to display.
     */
    public LoadingDialog(Context context, boolean cancelable, CharSequence msg) {
        mDialog = new ProgressDialog(context);
        mDialog.setCancelable(cancelable);
        mDialog.setMessage(msg);
        mDialog.setIndeterminate(true);

        if (cancelable) {
            mDialog.setCanceledOnTouchOutside(true);
            mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }// onCancel()

            });
        }
    }// LoadingDialog()

    /**
     * If you override this method, you must call {@code super.onPreExecute()}
     * at beginning of the method.
     */
    @Override
    protected void onPreExecute() {
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                if (!mFinished) {
                    try {
                        /*
                         * Sometime the activity has been finished before we
                         * show this dialog, it will raise error.
                         */
                        mDialog.show();
                    } catch (Throwable t) {
                        // TODO
                        Log.e(CLASSNAME, "onPreExecute() - show dialog: " + t);
                    }
                }
            }
        }, getDelayTime());
    }// onPreExecute()

    /**
     * If you override this method, you must call
     * {@code super.onPostExecute(result)} at beginning of the method.
     */
    @Override
    protected void onPostExecute(Result result) {
        doFinish();
    }// onPostExecute()

    /**
     * If you override this method, you must call {@code super.onCancelled()} at
     * beginning of the method.
     */
    @Override
    protected void onCancelled() {
        doFinish();
        super.onCancelled();
    }// onCancelled()

    private void doFinish() {
        mFinished = true;
        try {
            /*
             * Sometime the activity has been finished before we dismiss this
             * dialog, it will raise error.
             */
            mDialog.dismiss();
        } catch (Throwable t) {
            // TODO
            Log.e(CLASSNAME, "doFinish() - dismiss dialog: " + t);
        }
    }// doFinish()

    /**
     * Gets the delay time before showing the dialog.
     * 
     * @return the delay time
     */
    public int getDelayTime() {
        return mDelayTime;
    }// getDelayTime()

    /**
     * Sets the delay time before showing the dialog.
     * 
     * @param delayTime
     *            the delay time to set
     * @return the instance of this dialog, for chaining multiple calls into a
     *         single statement.
     */
    public LoadingDialog<Params, Progress, Result> setDelayTime(int delayTime) {
        mDelayTime = delayTime >= 0 ? delayTime : 0;
        return this;
    }// setDelayTime()

    /**
     * Sets last exception. This method is useful in case an exception raises
     * inside {@link #doInBackground(Void...)}
     * 
     * @param t
     *            {@link Throwable}
     */
    protected void setLastException(Throwable t) {
        mLastException = t;
    }// setLastException()

    /**
     * Gets last exception.
     * 
     * @return {@link Throwable}
     */
    protected Throwable getLastException() {
        return mLastException;
    }// getLastException()

}
