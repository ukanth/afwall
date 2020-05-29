package dev.ukanth.ufirewall.events;

import android.content.Context;

/**
 * Created by ukanth on 21/8/16.
 */

public class RulesEvent {
    public final Context ctx;
    public final String message;

    public RulesEvent(String message,Context ctx) {
        this.message = message;
        this.ctx = ctx;
    }
}
