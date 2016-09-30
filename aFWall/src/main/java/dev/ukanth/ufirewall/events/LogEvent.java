package dev.ukanth.ufirewall.events;

import android.content.Context;

import dev.ukanth.ufirewall.log.LogInfo;

/**
 * Created by ukanth on 21/8/16.
 */

public class LogEvent {
    public final LogInfo logInfo;
    public final Context ctx;

    public LogEvent(LogInfo logInfo, Context ctx) {
        this.logInfo = logInfo;
        this.ctx = ctx;
    }


}
