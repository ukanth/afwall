package dev.ukanth.ufirewall.events;

import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

/**
 * Created by ukanth on 25/9/17.
 */

public class RxCommandEvent {
    private static PublishSubject<Object> sSubject = PublishSubject.create();

    private RxCommandEvent() {
        // hidden constructor
    }

    public static Disposable subscribe(@NonNull Consumer<Object> action) {
        return sSubject.subscribe(action);
    }

    public static void publish(@NonNull Object message) {
        sSubject.onNext(message);
    }
}
