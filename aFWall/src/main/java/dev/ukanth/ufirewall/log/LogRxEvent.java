package dev.ukanth.ufirewall.log;

import android.support.annotation.NonNull;

import dev.ukanth.ufirewall.events.LogEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.subjects.PublishSubject;

/**
 * Created by ukanth on 25/9/17.
 */

public class LogRxEvent {
    private static PublishSubject<LogEvent> sSubject = PublishSubject.create();

    private LogRxEvent() {
        // hidden constructor
    }

    public static Disposable subscribe(@NonNull Consumer<LogEvent> action) {
        return sSubject.subscribe(action);
    }

    public static void publish(@NonNull LogEvent message) {
        sSubject.onNext(message);
    }

    public static PublishSubject getSubject() {
        return sSubject;
    }
}
