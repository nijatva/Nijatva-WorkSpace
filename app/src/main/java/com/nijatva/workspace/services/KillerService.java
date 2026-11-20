package com.nijatva.workspace.services;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nijatva.workspace.util.Utility;

/**
 * Nijatva Work Space — the safety net that shuts the profile-owner service down.
 *
 * <p>Android gives an Activity no callback when the user swipes the app out of Recents, and no
 * callback when the system reclaims the process. The profile-owner service must not outlive the
 * UI that drives it, so this service is started alongside the main screen and treats <em>any</em>
 * sign of its own death as the signal to stop both services:</p>
 *
 * <ul>
 *   <li>{@link #onTaskRemoved} — the task was swiped away.</li>
 *   <li>{@link #onTrimMemory} at background level — the process is about to be killed.</li>
 *   <li>{@link #onDestroy} — the host Activity explicitly stopped it.</li>
 * </ul>
 *
 * <p>The binders to both profiles arrive in the start intent, because a dying service cannot
 * bind to anything new. {@code START_REDELIVER_INTENT} makes sure they are handed back if the
 * system restarts this service.</p>
 */
public class KillerService extends Service {

    private static final String TAG = "KillerService";

    /** Extra bundle holding the two {@code IShelterService} binders. */
    public static final String EXTRA_BUNDLE = "extra";

    /** Binder key for the service running in the main profile. */
    public static final String KEY_MAIN = "main";

    /** Binder key for the service running in the work profile. */
    public static final String KEY_WORK = "work";

    @Nullable
    private IShelterService mServiceMain;
    @Nullable
    private IShelterService mServiceWork;

    /** Makes the shutdown idempotent — three different callbacks can fire in a row. */
    private boolean mStopped;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        captureBinders(intent);
        return START_REDELIVER_INTENT;
    }

    private void captureBinders(@Nullable Intent intent) {
        if (intent == null) return;

        Bundle extras = intent.getBundleExtra(EXTRA_BUNDLE);
        if (extras == null) {
            Log.w(TAG, "Started without binders; nothing to shut down later");
            return;
        }

        IBinder main = extras.getBinder(KEY_MAIN);
        IBinder work = extras.getBinder(KEY_WORK);
        mServiceMain = main == null ? null : IShelterService.Stub.asInterface(main);
        mServiceWork = work == null ? null : IShelterService.Stub.asInterface(work);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Never bound; this service exists only for its lifecycle callbacks.
        return null;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) {
            shutDown("process going to background");
        }
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        shutDown("task removed from Recents");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutDown("service destroyed");
    }

    /**
     * Stops both profile-owner services and then this service.
     *
     * @param reason logged so a bug report shows which lifecycle event triggered the shutdown
     */
    private void shutDown(@Nullable String reason) {
        if (mStopped) return;
        mStopped = true;

        Log.d(TAG, "Shutting down profile-owner services: " + reason);
        Utility.killShelterServices(mServiceMain, mServiceWork);

        stopSelf();
    }
}
