package com.nijatva.workspace.util;

import android.app.Activity;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.nijatva.workspace.R;

import java.lang.ref.WeakReference;

/**
 * Nijatva Work Space — progress dialog for one package-install session.
 *
 * <p>Installing into the work profile goes through
 * {@link PackageInstaller#commit} from the profile-owner service, so the UI never gets a normal
 * Activity result back. Instead it registers a {@link SessionCallback} and watches a single
 * session id.</p>
 *
 * <p>Two rules make this safe:</p>
 * <ul>
 *   <li>Every callback is filtered by session id. The installer is shared, so other sessions
 *       (including ones started by the system) also land here.</li>
 *   <li>The callback unregisters itself and dismisses the dialog exactly once, whatever the
 *       outcome. A leaked callback keeps the Activity alive and leaks the dialog window.</li>
 * </ul>
 *
 * <p>The caller registers the callback (see {@code DummyActivity}); this class removes itself.</p>
 */
public class InstallationProgressListener extends PackageInstaller.SessionCallback {

    private static final String TAG = "InstallProgress";

    /** The Activity is held weakly: the dialog must never outlive the screen that shows it. */
    @NonNull
    private final WeakReference<Activity> mActivityRef;

    @NonNull
    private final PackageInstaller mInstaller;

    /** The one session this listener cares about. */
    private final int mSessionId;

    @Nullable
    private AlertDialog mDialog;
    @Nullable
    private ProgressBar mProgress;

    /** Guards against the dialog being torn down twice from different callbacks. */
    private boolean mClosed;

    /**
     * Builds the listener and shows the dialog immediately.
     *
     * @param activity  host screen; used for the dialog window only
     * @param installer installer of the profile that owns the session
     * @param sessionId the session to follow
     */
    public InstallationProgressListener(@NonNull Activity activity,
                                        @NonNull PackageInstaller installer,
                                        int sessionId) {
        mActivityRef = new WeakReference<>(activity);
        mInstaller = installer;
        mSessionId = sessionId;

        show(activity);
    }

    private void show(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup content = (ViewGroup) LayoutInflater.from(activity)
                .inflate(R.layout.progress_dialog, null, false);
        mProgress = content.findViewById(R.id.progress);
        if (mProgress != null) {
            mProgress.setIndeterminate(true);
            mProgress.setMax(100);
        }

        mDialog = new AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(R.string.app_installing)
                .setView(content)
                .create();
        mDialog.show();
    }

    // --- SessionCallback ----------------------------------------------------------------------

    @Override
    public void onCreated(int sessionId) {
        // Nothing to do: the dialog is already on screen.
    }

    @Override
    public void onBadgingChanged(int sessionId) {
        // Icon/label of the session changed; not shown here.
    }

    @Override
    public void onActiveChanged(int sessionId, boolean active) {
        // The session starts and stops being "active" while it copies files.
    }

    @Override
    public void onProgressChanged(int sessionId, float progress) {
        if (sessionId != mSessionId) return;

        final int percent = (int) Math.max(0f, Math.min(1f, progress) * 100f);
        final ProgressBar bar = mProgress;
        if (bar == null) return;

        Activity activity = mActivityRef.get();
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (bar.isIndeterminate() && percent > 0) bar.setIndeterminate(false);
            bar.setProgress(percent);
        });
    }

    @Override
    public void onFinished(int sessionId, boolean success) {
        if (sessionId != mSessionId) return;

        if (!success) {
            Log.w(TAG, "Install session " + sessionId + " failed");
        }
        close();
    }

    // --- Teardown -----------------------------------------------------------------------------

    /**
     * Dismisses the dialog and unregisters from the installer. Safe to call more than once, and
     * safe to call from the host Activity's {@code onDestroy} when the user leaves early.
     */
    public void close() {
        if (mClosed) return;
        mClosed = true;

        Activity activity = mActivityRef.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.runOnUiThread(() -> {
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                mDialog = null;
                mProgress = null;
            });
        } else {
            mDialog = null;
            mProgress = null;
        }

        try {
            mInstaller.unregisterSessionCallback(this);
        } catch (RuntimeException e) {
            // Already unregistered, or the installer process is gone.
            Log.d(TAG, "Callback already released", e);
        }
    }
}
