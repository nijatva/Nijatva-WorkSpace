package com.nijatva.workspace.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Nijatva Work Space — the single owner of the app's local settings.
 *
 * <p>Every preference key below is part of the on-device contract: users who already set the app
 * up have these keys written into {@code prefs} on both the main and the work profile. The
 * <em>names</em> may never change, or existing installs silently lose their configuration and
 * the two profiles stop agreeing about auto-freeze, the File Shuttle and the auth key.</p>
 *
 * <p>The values are stored as plain scalars and as delimited string lists. Nothing here is
 * encrypted: the auth key lives in the profile's own private storage, which Android already
 * isolates from other apps.</p>
 */
public class LocalStorageManager {

    // --- Keys: frozen by the on-device contract. Do not rename. -------------------------------

    /** True while the setup wizard is running, so the app knows a half-provisioned state exists. */
    public static final String PREF_IS_SETTING_UP = "is_setting_up";

    /** True once provisioning has completed at least once. */
    public static final String PREF_HAS_SETUP = "has_setup";

    /** Delimited list of package names that the auto-freeze engine manages inside the work profile. */
    public static final String PREF_AUTO_FREEZE_LIST_WORK_PROFILE = "auto_freeze_list_work_profile";

    /** Whether the File Shuttle documents provider is offered to the system file picker. */
    public static final String PREF_CROSS_PROFILE_FILE_CHOOSER = "cross_profile_file_chooser";

    /** Shared secret used to sign intents handed across the profile boundary. */
    public static final String PREF_AUTH_KEY = "auth_key";

    /** Whether the auto-freeze engine is armed at all. */
    public static final String PREF_AUTO_FREEZE_SERVICE = "auto_freeze_service";

    /** When true, an app in the foreground is never auto-frozen. */
    public static final String PREF_DONT_FREEZE_FOREGROUND = "dont_freeze_foreground";

    /** Minutes after which the File Shuttle wipes its staging area; 0 disables the wipe. */
    public static final String PREF_SHUTTLE_AUTOWIPE = "shuttle_autowipe_minutes";

    /** Delay in seconds between screen-off and the freeze sweep. */
    public static final String PREF_AUTO_FREEZE_DELAY = "auto_freeze_delay";

    /** Index of the active app-list sort order; see {@link AppSortHelper}. */
    public static final String PREF_SORT_MODE = "sort_mode";

    /** Whether every app may be started across the profile boundary, not just allowlisted ones. */
    public static final String PREF_ALLOW_ALL_CROSS_PROFILE = "allow_all_cross_profile";

    /** Whether the work profile is blocked from searching the main profile's contacts. */
    public static final String PREF_BLOCK_CONTACTS_SEARCHING = "block_contacts_searching";

    // --- Storage ------------------------------------------------------------------------------

    /**
     * The preference file name is part of the same on-device contract as the keys: changing it
     * makes every existing install look brand new.
     */
    private static final String PREFS_FILE = "prefs";

    /** Separator for the string-list preferences. Kept as "," for compatibility. */
    private static final String LIST_DIVIDER = ",";

    /** Returned by {@link #getInt(String)} when a key has never been written. */
    public static final int INT_ABSENT = Integer.MIN_VALUE;

    private static volatile LocalStorageManager sInstance;

    private final SharedPreferences mPrefs;

    private LocalStorageManager(@NonNull Context context) {
        mPrefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    /**
     * Creates the singleton. Called once from the Application class of each profile; calling it
     * again replaces the instance, which is what the setup wizard relies on after a profile
     * switch.
     */
    public static synchronized void initialize(@NonNull Context context) {
        sInstance = new LocalStorageManager(context);
    }

    /**
     * @throws IllegalStateException when used before {@link #initialize(Context)} — a programming
     *                               error, not a recoverable state, because every entry point
     *                               goes through the Application class.
     */
    @NonNull
    public static LocalStorageManager getInstance() {
        LocalStorageManager instance = sInstance;
        if (instance == null) {
            throw new IllegalStateException(
                    "LocalStorageManager.initialize() must run in Application.onCreate()");
        }
        return instance;
    }

    /** True once {@link #initialize(Context)} has run in this process. */
    public static boolean isInitialized() {
        return sInstance != null;
    }

    // --- Scalar values ------------------------------------------------------------------------

    /** Removes a key entirely; reads of that key then fall back to their defaults. */
    public void remove(@Nullable String pref) {
        if (pref == null) return;
        mPrefs.edit().remove(pref).apply();
    }

    public boolean getBoolean(@Nullable String pref) {
        return getBoolean(pref, false);
    }

    public boolean getBoolean(@Nullable String pref, boolean fallback) {
        if (pref == null) return fallback;
        return mPrefs.getBoolean(pref, fallback);
    }

    public void setBoolean(@Nullable String pref, boolean value) {
        if (pref == null) return;
        mPrefs.edit().putBoolean(pref, value).apply();
    }

    /** @return the stored int, or {@link #INT_ABSENT} when the key was never written. */
    public int getInt(@Nullable String pref) {
        if (pref == null) return INT_ABSENT;
        return mPrefs.getInt(pref, INT_ABSENT);
    }

    /** @return the stored int, or {@code fallback} when the key was never written. */
    public int getInt(@Nullable String pref, int fallback) {
        int stored = getInt(pref);
        return stored == INT_ABSENT ? fallback : stored;
    }

    public void setInt(@Nullable String pref, int value) {
        if (pref == null) return;
        mPrefs.edit().putInt(pref, value).apply();
    }

    /** @return the stored string, or {@code null} when the key was never written. */
    @Nullable
    public String getString(@Nullable String pref) {
        if (pref == null) return null;
        return mPrefs.getString(pref, null);
    }

    /** @return the stored string, or {@code fallback} when absent or blank. */
    @NonNull
    public String getString(@Nullable String pref, @NonNull String fallback) {
        String stored = getString(pref);
        return stored == null ? fallback : stored;
    }

    public void setString(@Nullable String pref, @Nullable String value) {
        if (pref == null) return;
        if (value == null) {
            remove(pref);
        } else {
            mPrefs.edit().putString(pref, value).apply();
        }
    }

    // --- String lists -------------------------------------------------------------------------

    /**
     * Reads a delimited list.
     *
     * <p>An absent key, an empty value and stray separators all yield an empty array — never an
     * array containing a single empty string, which is what a naive {@code split(",")} produces
     * and which used to make "is this package in the list" answer wrongly.</p>
     */
    @NonNull
    public String[] getStringList(@Nullable String pref) {
        if (pref == null) return new String[0];

        String raw = mPrefs.getString(pref, null);
        if (raw == null || raw.isEmpty()) return new String[0];

        String[] parts = raw.split(LIST_DIVIDER);
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out.toArray(new String[0]);
    }

    /** Same as {@link #getStringList(String)} but as an immutable list, for newer call sites. */
    @NonNull
    public List<String> getStringListAsList(@Nullable String pref) {
        String[] array = getStringList(pref);
        if (array.length == 0) return Collections.emptyList();
        List<String> out = new ArrayList<>(array.length);
        Collections.addAll(out, array);
        return Collections.unmodifiableList(out);
    }

    public void setStringList(@Nullable String pref, @Nullable String[] list) {
        if (pref == null) return;
        if (list == null || list.length == 0) {
            remove(pref);
            return;
        }
        mPrefs.edit().putString(pref, join(list)).apply();
    }

    public void setStringList(@Nullable String pref, @Nullable List<String> list) {
        if (list == null) {
            setStringList(pref, (String[]) null);
            return;
        }
        setStringList(pref, list.toArray(new String[0]));
    }

    /** True when {@code item} is present in the list stored under {@code pref}. */
    public boolean stringListContains(@Nullable String pref, @Nullable String item) {
        if (item == null) return false;
        for (String entry : getStringList(pref)) {
            if (entry.equals(item)) return true;
        }
        return false;
    }

    /**
     * Adds {@code item} to the list if it is not already there.
     *
     * @return true when the list actually changed, so callers can skip redundant UI refreshes
     */
    public boolean appendStringList(@Nullable String pref, @Nullable String item) {
        if (pref == null || item == null || item.isEmpty()) return false;

        List<String> current = new ArrayList<>(getStringListAsList(pref));
        if (current.contains(item)) return false;

        current.add(item);
        mPrefs.edit().putString(pref, join(current.toArray(new String[0]))).apply();
        return true;
    }

    /**
     * Removes every occurrence of {@code item}.
     *
     * @return true when the list actually changed
     */
    public boolean removeFromStringList(@Nullable String pref, @Nullable String item) {
        if (pref == null || item == null) return false;

        List<String> current = new ArrayList<>(getStringListAsList(pref));
        if (!current.removeIf(item::equals)) return false;

        if (current.isEmpty()) {
            remove(pref);
        } else {
            mPrefs.edit().putString(pref, join(current.toArray(new String[0]))).apply();
        }
        return true;
    }

    /** Wipes every Nijatva setting. Used when a work profile is torn down. */
    public void clearAll() {
        mPrefs.edit().clear().apply();
    }

    // --- Internals ----------------------------------------------------------------------------

    /**
     * Joins without going through {@link Utility}: this class is loaded before the rest of the
     * app initialises, so it must not depend on anything in it.
     */
    @NonNull
    private static String join(@NonNull String[] list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.length; i++) {
            if (i > 0) sb.append(LIST_DIVIDER);
            sb.append(list[i]);
        }
        return sb.toString();
    }
}
