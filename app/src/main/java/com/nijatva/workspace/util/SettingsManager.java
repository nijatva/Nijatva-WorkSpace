package com.nijatva.workspace.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nijatva.workspace.ui.DummyActivity;

/**
 * Nijatva Work Space — typed access to every setting, plus the work-profile mirror.
 *
 * <p>{@link LocalStorageManager} stores raw values; this class is the only place that knows what
 * each key means, what its default is, and whether a change also has to be pushed into the work
 * profile.</p>
 *
 * <p>A setting reaches the other profile in one of two ways:</p>
 * <ul>
 *   <li><b>Component enable/disable</b> — applied locally, because the provider and the policy
 *       live in the profile that owns them.</li>
 *   <li><b>Preference mirror</b> — an intent carrying the key and the new value is relayed by
 *       {@link DummyActivity}, which writes the same key in the other profile. Without this the
 *       two halves of the app disagree about auto-freeze and contact search.</li>
 * </ul>
 *
 * <p>This class is instantiated once per profile, from the Application class.</p>
 */
public class SettingsManager {

    /**
     * Extra keys understood by {@link DummyActivity}'s SYNCHRONIZE_PREFERENCE handler.
     *
     * <p>They are repeated here as constants rather than imported, because DummyActivity is still
     * on the rewrite list and does not expose them yet. Values must stay in step with it.</p>
     */
    private static final String EXTRA_PREF_NAME = "name";
    private static final String EXTRA_PREF_BOOLEAN = "boolean";
    private static final String EXTRA_PREF_INT = "int";

    private static volatile SettingsManager sInstance;

    /** Creates the singleton for this profile. */
    public static synchronized void initialize(@NonNull Context context) {
        sInstance = new SettingsManager(context.getApplicationContext());
    }

    /**
     * @return the instance for this profile, or {@code null} before
     *         {@link #initialize(Context)} — callers in long-lived services check this because
     *         a restarted process may not have run the Application class yet.
     */
    @Nullable
    public static SettingsManager getInstance() {
        return sInstance;
    }

    @NonNull
    private final Context mContext;
    @NonNull
    private final LocalStorageManager mStorage;

    private SettingsManager(@NonNull Context context) {
        mContext = context;
        mStorage = LocalStorageManager.getInstance();
    }

    // --- Cross-profile mirroring --------------------------------------------------------------

    /**
     * Replays a boolean setting into the other profile.
     *
     * <p>The intent is first re-addressed to the copy of this app installed in the other
     * profile, then signed, then started — in that order, because the signature covers the
     * component it ends up at.</p>
     */
    private void mirrorBoolean(@NonNull String key, boolean value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra(EXTRA_PREF_NAME, key);
        intent.putExtra(EXTRA_PREF_BOOLEAN, value);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    /** Same as {@link #mirrorBoolean} for integer settings. */
    private void mirrorInt(@NonNull String key, int value) {
        Intent intent = new Intent(DummyActivity.SYNCHRONIZE_PREFERENCE);
        intent.putExtra(EXTRA_PREF_NAME, key);
        intent.putExtra(EXTRA_PREF_INT, value);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Utility.transferIntentToProfile(mContext, intent);
        mContext.startActivity(intent);
    }

    // --- Everything that has to be enforced at start-up ---------------------------------------

    /**
     * Re-applies every setting that is expressed as a component state.
     *
     * <p>Called from the main screen and from the relay after provisioning, because component
     * states survive a reinstall but not a profile re-creation.</p>
     */
    public void applyAll() {
        applyCrossProfileFileChooser();
    }

    // --- File Shuttle -------------------------------------------------------------------------

    /** Turns the documents provider on or off to match the stored preference. */
    public void applyCrossProfileFileChooser() {
        boolean enabled = getCrossProfileFileChooserEnabled();
        setComponentEnabled(MyWorkDocumentsProvider.class, enabled);
    }

    public void setCrossProfileFileChooserEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
        applyCrossProfileFileChooser();
        mirrorBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER, enabled);
    }

    public boolean getCrossProfileFileChooserEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_CROSS_PROFILE_FILE_CHOOSER);
    }

    // --- Contacts -----------------------------------------------------------------------------

    public void setBlockContactsSearchingEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING, enabled);
        mirrorBoolean(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING, enabled);
    }

    public boolean getBlockContactsSearchingEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_BLOCK_CONTACTS_SEARCHING);
    }

    // --- Auto-freeze --------------------------------------------------------------------------

    /**
     * Arms or disarms the freeze engine.
     *
     * <p>Not mirrored: each profile decides for itself whether it is running the engine, and the
     * main profile never freezes anything.</p>
     */
    public void setAutoFreezeServiceEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE, enabled);
    }

    public boolean getAutoFreezeServiceEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_AUTO_FREEZE_SERVICE);
    }

    /** @param seconds delay between screen-off and the freeze sweep; 0 means "immediately" */
    public void setAutoFreezeDelay(int seconds) {
        int clamped = Math.max(0, seconds);
        mStorage.setInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, clamped);
        mirrorInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, clamped);
    }

    /** @return the configured delay in seconds, defaulting to 0 */
    public int getAutoFreezeDelay() {
        return mStorage.getInt(LocalStorageManager.PREF_AUTO_FREEZE_DELAY, 0);
    }

    /** When enabled, the app the user is currently looking at is skipped by the sweep. */
    public void setSkipForegroundEnabled(boolean enabled) {
        mStorage.setBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
        mirrorBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND, enabled);
    }

    public boolean getSkipForegroundEnabled() {
        return mStorage.getBoolean(LocalStorageManager.PREF_DONT_FREEZE_FOREGROUND);
    }

    // --- Helpers ------------------------------------------------------------------------------

    /**
     * Enables or disables a component of this app without killing the process.
     *
     * <p>Used for the documents provider: DocumentsUI caches the roots, so the provider has to
     * disappear from the picker rather than report itself as empty.</p>
     */
    private void setComponentEnabled(@NonNull Class<?> component, boolean enabled) {
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            mContext.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(mContext, component), state, PackageManager.DONT_KILL_APP);
        } catch (IllegalArgumentException e) {
            // Component not declared in this profile's manifest — the provider only exists in
            // the profile that ships the File Shuttle.
            android.util.Log.w("SettingsManager",
                    "Cannot change state of " + component.getName(), e);
        }
    }
}
