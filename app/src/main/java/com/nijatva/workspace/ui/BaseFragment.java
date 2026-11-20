package com.nijatva.workspace.ui;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Nijatva Work Space — shared fragment base.
 *
 * <p>Fragments in this app are detached and re-attached constantly (tab switches, configuration
 * changes, work-profile rebinds), so every asynchronous callback needs a cheap way to ask
 * "is there still an Activity to talk to?". This base class answers that question once and
 * gives subclasses a safe way to hop back to the main thread.</p>
 *
 * <p>Nothing here touches the work profile, the profile-owner APIs or the cross-profile
 * relay — it is pure UI plumbing, which is why it is the smallest file in the app.</p>
 */
public abstract class BaseFragment extends Fragment {

    /**
     * Runs {@code task} on the UI thread, or drops it silently when the fragment is no longer
     * attached to an Activity.
     *
     * <p>Dropping is deliberate: a callback that arrives after the user has left the screen has
     * nothing left to update, and posting it would only risk a crash on a destroyed view.</p>
     *
     * @param task work to run on the main thread; {@code null} is ignored
     */
    protected void runOnUiThread(@Nullable Runnable task) {
        if (task == null) return;

        Activity host = getActivity();
        if (host == null) return;

        host.runOnUiThread(task);
    }

    /**
     * Runs {@code task} on the UI thread only if the fragment currently has a live view.
     *
     * <p>Use this instead of {@link #runOnUiThread(Runnable)} when the task touches views —
     * for example refreshing a list row or closing a sheet.</p>
     */
    protected void runOnUiThreadIfVisible(@Nullable Runnable task) {
        if (task == null) return;
        if (!isAdded() || getView() == null) return;

        Activity host = getActivity();
        if (host == null) return;

        host.runOnUiThread(task);
    }

    /** True when this fragment is attached and its view is still alive. */
    protected boolean isAlive() {
        return isAdded() && getActivity() != null && getView() != null;
    }

    /** Non-null assertion helper for code paths where the Activity is known to exist. */
    @NonNull
    protected Activity requireHostActivity() {
        Activity host = getActivity();
        if (host == null) {
            throw new IllegalStateException("Fragment " + getClass().getSimpleName()
                    + " is not attached to an Activity");
        }
        return host;
    }
}
