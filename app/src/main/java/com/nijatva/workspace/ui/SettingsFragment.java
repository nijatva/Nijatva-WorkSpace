package com.nijatva.workspace.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.TwoStatePreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.nijatva.workspace.R;
import com.nijatva.workspace.services.IGetAppsCallback;
import com.nijatva.workspace.services.IShelterService;
import com.nijatva.workspace.util.ApplicationInfoWrapper;
import com.nijatva.workspace.util.LocalStorageManager;
import com.nijatva.workspace.util.SettingsManager;
import com.nijatva.workspace.util.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
    private static final String SETTINGS_VERSION = "settings_version";
    private static final String SETTINGS_SOURCE_CODE = "settings_source_code";
    private static final String SETTINGS_FEEDBACK = "settings_feedback";
    private static final String SETTINGS_SUPPORT = "settings_support";
    private static final String SETTINGS_BASED_ON = "settings_based_on";
    private static final String SETTINGS_GMS_STATUS = "settings_gms_status";
    private static final String SETTINGS_PROTECT_SCREEN = "settings_protect_screen_capture";
    private static final String SETTINGS_PROTECT_NOTIF = "settings_protect_notif_privacy";
    private static final String SETTINGS_PROTECT_BACKUP = "settings_protect_backup";
    private static final String SETTINGS_PROTECT_BLUETOOTH = "settings_protect_bluetooth";
    private static final String SETTINGS_PROTECT_STATUS = "settings_protect_status";
    private static final String SETTINGS_TRANSFER_AUTOWIPE = "settings_transfer_autowipe";
    private static final String SETTINGS_TRANSFER_CLEAN = "settings_transfer_clean";
    private static final String SETTINGS_INSPECT_POLICIES = "settings_inspect_policies";
    private static final String SETTINGS_INSPECT_INTEGRITY = "settings_inspect_integrity";
    private static final String SETTINGS_INSPECT_INVENTORY = "settings_inspect_inventory";
    private static final String SETTINGS_INSPECT_SNAPSHOT = "settings_inspect_snapshot";
    private static final String SETTINGS_PROTECT_NOTIF_LISTENERS = "settings_protect_notif_listeners";
    private static final String SETTINGS_PROTECT_CALLER_ID = "settings_protect_caller_id";
    private static final String SETTINGS_PROTECT_CA = "settings_protect_ca";
    private static final String SETTINGS_PER_APP_CROSS_PROFILE = "settings_per_app_cross_profile";
    private static final String SETTINGS_ALLOW_SUITE = "settings_allow_suite";
    private static final String SETTINGS_SUITE_APPS = "settings_suite_apps";
    private static final String SETTINGS_ALLOW_ALL_CROSS_PROFILE = "settings_allow_all_cross_profile";
    private static final String SETTINGS_CROSS_PROFILE_FILE_CHOOSER = "settings_cross_profile_file_chooser";
    private static final String SETTINGS_BLOCK_CONTACTS_SEARCHING = "settings_block_contacts_searching";
    private static final String SETTINGS_AUTO_FREEZE_SERVICE = "settings_auto_freeze_service";
    private static final String SETTINGS_AUTO_FREEZE_DELAY = "settings_auto_freeze_delay";
    private static final String SETTINGS_SKIP_FOREGROUND = "settings_dont_freeze_foreground";

    private static final int[] AUTO_FREEZE_DELAY_SECONDS = new int[]{0, 60, 2 * 60, 5 * 60, 15 * 60};

    private SettingsManager mManager = SettingsManager.getInstance();
    private IShelterService mServiceWork = null;

    private TwoStatePreference mPrefCrossProfileFileChooser = null;
    private TwoStatePreference mPrefBlockContactsSearching = null;
    private TwoStatePreference mPrefAutoFreezeService = null;
    private TwoStatePreference mPrefSkipForeground = null;

    private DropDownPreference mPrefAutoFreezeDelay = null;
    private TwoStatePreference mPrefAllowAllCrossProfile = null;
    private TwoStatePreference mPrefProtectScreen = null;
    private Preference mPrefProtectStatus = null;
    private android.content.BroadcastReceiver mProfileReceiver = null;
    private TwoStatePreference mPrefProtectNotif = null;
    private TwoStatePreference mPrefProtectBackup = null;
    private TwoStatePreference mPrefProtectBluetooth = null;
    private TwoStatePreference mPrefProtectNotifListeners = null;
    private TwoStatePreference mPrefProtectCallerId = null;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(androidx.preference.R.id.recycler_view), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPaddingRelative(0, 0, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.preferences_settings);
        mServiceWork = IShelterService.Stub.asInterface(
                ((Bundle) getActivity().getIntent().getParcelableExtra("extras")).getBinder("profile_service"));

        // Set the displayed version
        try {
            // Runtime GMS check (the build-time guard lives in app/build.gradle)
        Preference gms = findPreference(SETTINGS_GMS_STATUS);
        if (gms != null) {
            gms.setSummary(isGmsInstalled()
                    ? R.string.gms_installed : R.string.gms_not_installed);
        }
        findPreference(SETTINGS_VERSION).setSummary(
                    getContext().getPackageManager().getPackageInfo(
                            getContext().getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // WTF?
        }

        // Open source code url on click
        findPreference(SETTINGS_SOURCE_CODE)
                .setOnPreferenceClickListener(this::openSummaryUrl);
        findPreference(SETTINGS_FEEDBACK)
                .setOnPreferenceClickListener(this::openFeedback);
        findPreference(SETTINGS_SUPPORT)
                .setOnPreferenceClickListener(this::openSupport);
        findPreference(SETTINGS_BASED_ON)
                .setOnPreferenceClickListener(this::openSummaryUrl);
        findPreference(SETTINGS_PER_APP_CROSS_PROFILE)
                .setOnPreferenceClickListener(this::openPerAppCrossProfile);
        findPreference(SETTINGS_ALLOW_SUITE)
                .setOnPreferenceClickListener(this::allowAllSuiteApps);
        findPreference(SETTINGS_SUITE_APPS)
                .setOnPreferenceClickListener(this::openSuiteApps);

        // === Interactions ===
        mPrefCrossProfileFileChooser = (TwoStatePreference) findPreference(SETTINGS_CROSS_PROFILE_FILE_CHOOSER);
        mPrefCrossProfileFileChooser.setChecked(mManager.getCrossProfileFileChooserEnabled());
        mPrefCrossProfileFileChooser.setOnPreferenceChangeListener(this);
        mPrefBlockContactsSearching = (TwoStatePreference) findPreference(SETTINGS_BLOCK_CONTACTS_SEARCHING);
        mPrefBlockContactsSearching.setChecked(mManager.getBlockContactsSearchingEnabled());
        mPrefBlockContactsSearching.setOnPreferenceChangeListener(this);

        // === Services ===
        mPrefAutoFreezeService = (TwoStatePreference) findPreference(SETTINGS_AUTO_FREEZE_SERVICE);
        mPrefAutoFreezeService.setChecked(mManager.getAutoFreezeServiceEnabled());
        mPrefAutoFreezeService.setOnPreferenceChangeListener(this);
        mPrefAutoFreezeDelay = findPreference(SETTINGS_AUTO_FREEZE_DELAY);
        mPrefAutoFreezeDelay.setOnPreferenceChangeListener(this);
        mPrefAutoFreezeDelay.setEntries(Arrays.stream(AUTO_FREEZE_DELAY_SECONDS).mapToObj((it) -> getString(R.string.format_minutes, it / 60)).toArray(String[]::new));
        mPrefAutoFreezeDelay.setEntryValues(Arrays.stream(AUTO_FREEZE_DELAY_SECONDS).mapToObj(String::valueOf).toArray(String[]::new));
        updateAutoFreezeDelay();
        mPrefSkipForeground = (TwoStatePreference) findPreference(SETTINGS_SKIP_FOREGROUND);
        mPrefSkipForeground.setChecked(mManager.getSkipForegroundEnabled());
        mPrefSkipForeground.setOnPreferenceChangeListener(this);

        // ---- Transfer: shuttle auto-wipe ----
        androidx.preference.DropDownPreference wipe =
                (androidx.preference.DropDownPreference) findPreference(SETTINGS_TRANSFER_AUTOWIPE);
        if (wipe != null) {
            wipe.setOnPreferenceChangeListener(this);
            updateWipeSummary(wipe);
        }
        Preference clean = findPreference(SETTINGS_TRANSFER_CLEAN);
        if (clean != null) {
            clean.setOnPreferenceClickListener(this::cleanShuttle);
            clean.setSummary(shuttleSummary());
        }

        // ---- Inspect (read-only reports) ----
        Preference inspPol = findPreference(SETTINGS_INSPECT_POLICIES);
        if (inspPol != null) inspPol.setOnPreferenceClickListener(this::showPolicyCheck);
        Preference inspInt = findPreference(SETTINGS_INSPECT_INTEGRITY);
        if (inspInt != null) inspInt.setOnPreferenceClickListener(this::showIntegrity);
        Preference inspInv = findPreference(SETTINGS_INSPECT_INVENTORY);
        if (inspInv != null) inspInv.setOnPreferenceClickListener(this::showInventory);
        Preference inspSnap = findPreference(SETTINGS_INSPECT_SNAPSHOT);
        if (inspSnap != null) inspSnap.setOnPreferenceClickListener(this::showPermissionChanges);

        // ---- Protect centre ----
        mPrefProtectScreen = (TwoStatePreference) findPreference(SETTINGS_PROTECT_SCREEN);
        mPrefProtectNotif = (TwoStatePreference) findPreference(SETTINGS_PROTECT_NOTIF);
        mPrefProtectBackup = (TwoStatePreference) findPreference(SETTINGS_PROTECT_BACKUP);
        mPrefProtectBluetooth = (TwoStatePreference) findPreference(SETTINGS_PROTECT_BLUETOOTH);
        mPrefProtectNotifListeners =
                (TwoStatePreference) findPreference(SETTINGS_PROTECT_NOTIF_LISTENERS);
        mPrefProtectCallerId = (TwoStatePreference) findPreference(SETTINGS_PROTECT_CALLER_ID);
        TwoStatePreference[] protectSwitches = {
                mPrefProtectScreen, mPrefProtectNotif, mPrefProtectBackup, mPrefProtectBluetooth,
                mPrefProtectNotifListeners, mPrefProtectCallerId};
        for (TwoStatePreference p : protectSwitches) {
            if (p != null) {
                p.setOnPreferenceChangeListener(this);
                p.setChecked(readProtectState(p));
            }
        }
        Preference ca = findPreference(SETTINGS_PROTECT_CA);
        if (ca != null) ca.setOnPreferenceClickListener(this::openCaList);
        mPrefProtectStatus = findPreference(SETTINGS_PROTECT_STATUS);
        if (mPrefProtectStatus != null) mPrefProtectStatus.setSummary(workProfileStatus());

        // Work-profile lifecycle: keep the status row live while this screen is open
        mProfileReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mPrefProtectStatus != null) {
                    mPrefProtectStatus.setSummary(workProfileStatus());
                }
            }
        };

        // Allow-all-apps cross-profile switch
        mPrefAllowAllCrossProfile = (TwoStatePreference) findPreference(SETTINGS_ALLOW_ALL_CROSS_PROFILE);
        mPrefAllowAllCrossProfile.setChecked(LocalStorageManager.getInstance()
                .getBoolean(LocalStorageManager.PREF_ALLOW_ALL_CROSS_PROFILE));
        mPrefAllowAllCrossProfile.setOnPreferenceChangeListener(this);
        if (mPrefAllowAllCrossProfile.isChecked()) {
            // Re-apply so newly cloned apps are covered too
            applyCrossProfileForAll(true);
        }

        // Disable FileSuttle on Q for now
        // Supported on R and beyond
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            mPrefCrossProfileFileChooser.setEnabled(false);
        }

        // Disable FileShuttle for Android Go
        // as it requires SYSTEM_ALERT_WINDOW which
        // is not allowed on Go devices
        ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am.isLowRamDevice()) {
            mPrefCrossProfileFileChooser.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mProfileReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            try {
                getContext().registerReceiver(mProfileReceiver, filter);
            } catch (Exception ignored) {
            }
        }
        if (mPrefProtectStatus != null) mPrefProtectStatus.setSummary(workProfileStatus());

        // Update all preferences that may change when returning
        // i.e. preferences that open another dialog for picking
        updateAutoFreezeDelay();
    }

    @Override
    public void onPause() {
        if (mProfileReceiver != null) {
            try {
                getContext().unregisterReceiver(mProfileReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onPause();
    }

    private void updateAutoFreezeDelay() {
        mPrefAutoFreezeDelay.setSummary(getString(R.string.format_minutes, mManager.getAutoFreezeDelay() / 60));
    }

    // Allow every app in the work profile to interact with the personal space
    // (DevicePolicyManager.setCrossProfilePackages). Removes Android's
    // "Access blocked by your IT admin" block for cross-profile actions.
    private void applyCrossProfileForAll(final boolean enabled) {
        if (mServiceWork == null) {
            toast("Work profile not connected");
            return;
        }
        if (!enabled) {
            try {
                mServiceWork.setCrossProfilePackages(new ArrayList<>());
                toast("Cross-profile access reset");
            } catch (RemoteException e) {
                toast("Could not reset cross-profile access");
            }
            return;
        }
        try {
            mServiceWork.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(List<ApplicationInfoWrapper> apps) {
                    final List<String> pkgs = new ArrayList<>();
                    for (ApplicationInfoWrapper app : apps) {
                        pkgs.add(app.getPackageName());
                    }
                    try {
                        mServiceWork.setCrossProfilePackages(pkgs);
                        toast(getString(R.string.settings_allow_all_cross_profile_done, pkgs.size()));
                    } catch (RemoteException e) {
                        toast("Could not set cross-profile access");
                    }
                }
            }, true);
        } catch (RemoteException e) {
            toast("Could not read work profile apps");
        }
    }

    /** Reads the real Android policy state, never just the switch position. */
    // ---------- Transfer ----------

    private void updateWipeSummary(androidx.preference.DropDownPreference pref) {
        String v = pref.getValue();
        CharSequence[] vals = pref.getEntryValues();
        CharSequence[] ents = pref.getEntries();
        if (vals == null || ents == null) return;
        for (int i = 0; i < vals.length && i < ents.length; i++) {
            if (vals[i].toString().equals(v)) {
                pref.setSummary(ents[i]);
                return;
            }
        }
    }

    private String shuttleSummary() {
        if (mServiceWork == null) return getString(R.string.inspect_unavailable);
        try {
            int n = mServiceWork.shuttleFileCount();
            if (n == 0) return getString(R.string.transfer_empty);
            long size = mServiceWork.shuttleSizeBytes();
            return getString(R.string.transfer_summary, n,
                    android.text.format.Formatter.formatShortFileSize(getContext(), size));
        } catch (RemoteException e) {
            return getString(R.string.inspect_unavailable);
        }
    }

    private boolean cleanShuttle(Preference pref) {
        if (mServiceWork == null) {
            toast(getString(R.string.inspect_unavailable));
            return true;
        }
        try {
            long age = LocalStorageManager.getInstance()
                    .getInt(LocalStorageManager.PREF_SHUTTLE_AUTOWIPE);
            int n = mServiceWork.wipeShuttle(age);
            toast(getString(R.string.transfer_cleaned, n));
            pref.setSummary(shuttleSummary());
        } catch (RemoteException e) {
            toast(getString(R.string.inspect_unavailable));
        }
        return true;
    }

    // ---------- Inspect: read-only reports ----------

    private boolean showPolicyCheck(Preference pref) {
        if (mServiceWork == null) { toast(getString(R.string.inspect_unavailable)); return true; }
        try {
            final String json = mServiceWork.verifyPolicies();
            org.json.JSONObject o = new org.json.JSONObject(json);
            StringBuilder sb = new StringBuilder();
            sb.append("Screenshots blocked: ").append(o.optBoolean("screenCapture") ? "OK" : "off").append("\n");
            sb.append("Notification privacy: ").append(o.optBoolean("notifPrivacy") ? "OK" : "off").append("\n");
            sb.append("Backup blocked: ").append(o.optBoolean("backupBlocked") ? "OK" : "off").append("\n");
            sb.append("Bluetooth contacts blocked: ").append(o.optBoolean("bluetoothBlocked") ? "OK" : "off").append("\n");
            sb.append("Notification firewall: ").append(o.optBoolean("notifFirewall") ? "OK" : "off").append("\n");
            sb.append("Caller-ID isolated: ").append(o.optBoolean("callerIdIsolated") ? "OK" : "off").append("\n");
            sb.append("Profile owner: ").append(o.optBoolean("profileOwner") ? "OK" : "NO");
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.inspect_policies)
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.inspect_copy, (d, w) -> copyText(json))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            toast(getString(R.string.inspect_unavailable));
        }
        return true;
    }

    private boolean showIntegrity(Preference pref) {
        if (mServiceWork == null) { toast(getString(R.string.inspect_unavailable)); return true; }
        try {
            final String json = mServiceWork.getProfileIntegrity();
            org.json.JSONObject o = new org.json.JSONObject(json);
            StringBuilder sb = new StringBuilder();
            sb.append("Profile owner: ").append(o.optBoolean("profileOwner") ? "OK" : "NO").append("\n");
            sb.append("Admin active: ").append(o.optBoolean("adminActive") ? "OK" : "NO").append("\n");
            sb.append("Profile available: ").append(o.optBoolean("profileAvailable") ? "OK" : "NO").append("\n");
            sb.append("Package: ").append(o.optString("packageName"));
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.inspect_integrity)
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.inspect_copy, (d, w) -> copyText(json))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            toast(getString(R.string.inspect_unavailable));
        }
        return true;
    }

    private boolean showInventory(Preference pref) {
        if (mServiceWork == null) { toast(getString(R.string.inspect_unavailable)); return true; }
        try {
            final String json = mServiceWork.getAppInventory();
            org.json.JSONArray arr = new org.json.JSONArray(json);
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < arr.length() && shown < 12; i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                sb.append(o.optString("package")).append("  v").append(o.optString("versionName")).append("\n");
                sb.append("   target ").append(o.optInt("targetSdk"))
                  .append(" | perms ").append(o.optInt("permissionsGranted")).append("/")
                  .append(o.optInt("permissionsRequested")).append("\n");
                sb.append("   sha256 ").append(shortSha(o.optString("signature"))).append("\n\n");
                shown++;
            }
            if (arr.length() > shown) sb.append("... ").append(arr.length() - shown).append(" more\n");
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.inspect_inventory) + " (" + arr.length() + ")")
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.inspect_copy, (d, w) -> copyText(json))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            toast(getString(R.string.inspect_unavailable));
        }
        return true;
    }

    private boolean showPermissionChanges(Preference pref) {
        if (mServiceWork == null) { toast(getString(R.string.inspect_unavailable)); return true; }
        try {
            final String changes = mServiceWork.getPermissionChanges();
            org.json.JSONArray arr = new org.json.JSONArray(changes);
            if (arr.length() == 0) {
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.inspect_snapshot)
                        .setMessage(getString(R.string.inspect_no_changes))
                        .setPositiveButton(R.string.inspect_save_baseline, (d, w) -> saveBaseline())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.length() && i < 20; i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                sb.append(o.optString("package")).append("\n   ")
                  .append(o.optString("permission").replace("android.permission.", ""))
                  .append(": ").append(o.optBoolean("before") ? "granted" : "denied")
                  .append(" -> ").append(o.optBoolean("after") ? "GRANTED" : "DENIED").append("\n\n");
            }
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.inspect_changes, arr.length()))
                    .setMessage(sb.toString())
                    .setPositiveButton(R.string.inspect_copy, (d, w) -> copyText(changes))
                    .setNeutralButton(R.string.inspect_save_baseline, (d, w) -> saveBaseline())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            toast(getString(R.string.inspect_unavailable));
        }
        return true;
    }

    private void saveBaseline() {
        if (mServiceWork == null) return;
        try {
            int n = Integer.parseInt(mServiceWork.savePermissionBaseline());
            toast(getString(R.string.inspect_baseline_saved, n));
        } catch (Exception e) {
            toast(getString(R.string.inspect_unavailable));
        }
    }

    private String shortSha(String sha) {
        if (sha == null) return "";
        return sha.length() < 16 ? sha : sha.substring(0, 16) + "...";
    }

    private void copyText(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("report", text));
            toast(getString(R.string.inspect_copied));
        }
    }

    private boolean readProtectState(TwoStatePreference pref) {
        if (mServiceWork == null) return false;
        try {
            if (pref == mPrefProtectScreen) return mServiceWork.isScreenCaptureDisabled();
            if (pref == mPrefProtectNotif) return mServiceWork.isUnredactedNotificationsDisabled();
            if (pref == mPrefProtectBackup) return !mServiceWork.isBackupServiceEnabled();
            if (pref == mPrefProtectBluetooth) return mServiceWork.isBluetoothContactSharingDisabled();
            if (pref == mPrefProtectNotifListeners)
                return mServiceWork.isNotificationListenerFirewallEnabled();
            if (pref == mPrefProtectCallerId) return mServiceWork.isCallerIdIsolationEnabled();
        } catch (RemoteException ignored) {
        }
        return false;
    }

    private boolean applyProtectState(TwoStatePreference pref, boolean on) {
        if (mServiceWork == null) {
            toast("Work profile not connected");
            return false;
        }
        try {
            if (pref == mPrefProtectScreen) return mServiceWork.setScreenCaptureDisabled(on);
            if (pref == mPrefProtectNotif) return mServiceWork.setUnredactedNotificationsDisabled(on);
            if (pref == mPrefProtectBackup) return mServiceWork.setBackupServiceEnabled(!on);
            if (pref == mPrefProtectBluetooth) return mServiceWork.setBluetoothContactSharingDisabled(on);
            if (pref == mPrefProtectNotifListeners)
                return mServiceWork.setNotificationListenerFirewall(on);
            if (pref == mPrefProtectCallerId) return mServiceWork.setCallerIdIsolation(on);
        } catch (RemoteException ignored) {
        }
        return false;
    }

    private boolean openCaList(Preference pref) {
        if (mServiceWork == null) {
            toast("Work profile not connected");
            return true;
        }
        try {
            List<String> certs = mServiceWork.getInstalledCaCerts();
            if (certs == null || certs.isEmpty()) {
                toast(getString(R.string.protect_ca_none));
                return true;
            }
            new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.protect_ca_count, certs.size()))
                    .setItems(certs.toArray(new CharSequence[0]), null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } catch (RemoteException e) {
            toast(getString(R.string.protect_status_unavailable));
        }
        return true;
    }

    private String workProfileStatus() {
        try {
            android.os.UserManager um =
                    (android.os.UserManager) getContext().getSystemService(Context.USER_SERVICE);
            if (um == null) return getString(R.string.protect_status_unavailable);
            android.os.UserHandle me = android.os.Process.myUserHandle();
            for (android.os.UserHandle u : um.getUserProfiles()) {
                if (!u.equals(me)) {
                    boolean quiet = um.isQuietModeEnabled(u);
                    return getString(quiet
                            ? R.string.protect_status_paused : R.string.protect_status_active);
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.protect_status_unavailable);
    }

    private boolean isGmsInstalled() {
        try {
            getContext().getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String msg) {
        if (getContext() == null) return;
        android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // Build a QR bitmap for the UPI payment link (pure ZXing, no network)
    private android.graphics.Bitmap makeQr(String data, int size) {
        try {
            com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(data, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            return android.graphics.Bitmap.createBitmap(pixels, size, size,
                    android.graphics.Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    // List the suite: installed apps can be opened, missing ones say so
    private boolean openSuiteApps(Preference pref) {
        final java.util.List<String> pkgs = com.nijatva.workspace.util.NijatvaSuite.PACKAGES;
        final android.content.pm.PackageManager pm = getContext().getPackageManager();
        final java.util.List<String> available = new java.util.ArrayList<>();
        CharSequence[] labels = new CharSequence[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            String name = prettyName(pkg);
            boolean installed;
            try {
                pm.getPackageInfo(pkg, 0);
                installed = true;
                available.add(pkg);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                installed = false;
            }
            labels[i] = name + (installed
                    ? " — " + getString(R.string.suite_installed)
                    : " — " + getString(R.string.suite_not_installed));
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.settings_suite_apps)
                .setItems(labels, (dialog, which) -> {
                    String pkg = pkgs.get(which);
                    if (!available.contains(pkg)) {
                        toast(getString(R.string.suite_not_installed));
                        return;
                    }
                    Intent launch = pm.getLaunchIntentForPackage(pkg);
                    if (launch == null) {
                        toast(getString(R.string.suite_cannot_launch));
                        return;
                    }
                    try {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launch);
                    } catch (Exception e) {
                        toast(getString(R.string.suite_cannot_launch));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }

    private String prettyName(String pkg) {
        String s = pkg.substring(pkg.lastIndexOf('.') + 1);
        if (s.isEmpty()) return pkg;
        return "Nijatva " + Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Allow every installed Nijatva suite app to interact across profiles
    private boolean allowAllSuiteApps(Preference pref) {
        if (mServiceWork == null) {
            toast("Work profile not connected");
            return true;
        }
        try {
            mServiceWork.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(List<ApplicationInfoWrapper> apps) {
                    List<String> suite = new ArrayList<>();
                    for (ApplicationInfoWrapper app : apps) {
                        if (com.nijatva.workspace.util.NijatvaSuite
                                .isSuite(app.getPackageName())) {
                            suite.add(app.getPackageName());
                        }
                    }
                    if (suite.isEmpty()) {
                        toast(getString(R.string.suite_none_installed));
                        return;
                    }
                    try {
                        List<String> current = mServiceWork.getCrossProfilePackages();
                        for (String pkg : suite) {
                            if (!current.contains(pkg)) current.add(pkg);
                        }
                        mServiceWork.setCrossProfilePackages(current);
                        toast(getString(R.string.suite_allowed_done, suite.size()));
                    } catch (RemoteException e) {
                        toast("Could not save cross-profile access");
                    }
                }
            }, true);
        } catch (RemoteException e) {
            toast("Could not read work profile apps");
        }
        return true;
    }

    // Pick exactly which work apps may talk to the personal space
    private boolean openPerAppCrossProfile(Preference pref) {
        if (mServiceWork == null) {
            toast("Work profile not connected");
            return true;
        }
        try {
            mServiceWork.getApps(new IGetAppsCallback.Stub() {
                @Override
                public void callback(final List<ApplicationInfoWrapper> apps) {
                    List<String> current;
                    try {
                        current = mServiceWork.getCrossProfilePackages();
                    } catch (RemoteException e) {
                        current = new ArrayList<>();
                    }
                    final List<ApplicationInfoWrapper> appList = apps;
                    final boolean[] checked = new boolean[appList.size()];
                    final CharSequence[] labels = new CharSequence[appList.size()];
                    for (int i = 0; i < appList.size(); i++) {
                        labels[i] = appList.get(i).getLabel();
                        checked[i] = current.contains(appList.get(i).getPackageName());
                    }
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> new AlertDialog.Builder(getContext())
                            .setTitle(R.string.settings_per_app_cross_profile)
                            .setMultiChoiceItems(labels, checked,
                                    (dialog, which, isChecked) -> checked[which] = isChecked)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                                List<String> pkgs = new ArrayList<>();
                                for (int i = 0; i < appList.size(); i++) {
                                    if (checked[i]) pkgs.add(appList.get(i).getPackageName());
                                }
                                try {
                                    mServiceWork.setCrossProfilePackages(pkgs);
                                    toast(getString(
                                            R.string.settings_per_app_cross_profile_done, pkgs.size()));
                                } catch (RemoteException e) {
                                    toast("Could not save cross-profile access");
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show());
                }
            }, true);
        } catch (RemoteException e) {
            toast("Could not read work profile apps");
        }
        return true;
    }

    private boolean openSupport(Preference pref) {
        final String upi = "Vasudevupi@upi";
        String upiUri = "upi://pay?pa=" + upi + "&pn=Vasudev%20Dangaya&cu=INR";
        float d = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout box = new android.widget.LinearLayout(getContext());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = (int) (20 * d);
        box.setPadding(pad, pad, pad, pad);

        android.graphics.Bitmap bmp = makeQr(upiUri, 512);
        if (bmp != null) {
            android.widget.ImageView iv = new android.widget.ImageView(getContext());
            iv.setImageBitmap(bmp);
            int side = (int) (220 * d);
            box.addView(iv, new android.widget.LinearLayout.LayoutParams(side, side));
        } else {
            toast(getString(R.string.support_qr_failed));
        }

        android.widget.TextView idView = new android.widget.TextView(getContext());
        idView.setText(getString(R.string.support_upi_label) + ": " + upi);
        idView.setTextSize(16);
        idView.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (14 * d);
        box.addView(idView, lp);

        android.widget.TextView note = new android.widget.TextView(getContext());
        note.setText(getString(R.string.support_note));
        note.setTextSize(12);
        note.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = (int) (8 * d);
        box.addView(note, lp2);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.support_title)
                .setView(box)
                .setPositiveButton(R.string.support_copy, (dialog, which) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("UPI ID", upi));
                        toast(getString(R.string.support_copied));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }

    private boolean openFeedback(Preference pref) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:vasudev.work@rediffmail.com"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "MyWork feedback");
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("No email app found");
        }
        return true;
    }

    private boolean openSummaryUrl(Preference pref) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(pref.getSummary().toString()));
        startActivity(intent);
        return true;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newState) {
        if (preference == mPrefCrossProfileFileChooser) {
            boolean enabled = (boolean) newState;
            if (!enabled) {
                mManager.setCrossProfileFileChooserEnabled(false);
                return true;
            }

            // File Shuttle needs four things. Check all of them and show exactly
            // which one is missing instead of bouncing the user to a settings page.
            boolean filesPersonal = Utility.checkAllFileAccessPermission();
            boolean overlayPersonal = Utility.checkSystemAlertPermission(getContext());
            boolean filesWork = false;
            boolean overlayWork = false;
            if (mServiceWork != null) {
                try {
                    filesWork = mServiceWork.hasAllFileAccessPermission();
                    overlayWork = mServiceWork.hasSystemAlertPermission();
                } catch (RemoteException e) {
                    // leave false; reported below
                }
            }

            if (filesPersonal && overlayPersonal && filesWork && overlayWork) {
                mManager.setCrossProfileFileChooserEnabled(true);
                return true;
            }

            String ok = getString(R.string.file_shuttle_req_ok);
            String missing = getString(R.string.file_shuttle_req_missing);
            String status = getString(R.string.file_shuttle_req_all_files_personal) + ": "
                    + (filesPersonal ? ok : missing)
                    + "\n" + getString(R.string.file_shuttle_req_all_files_work) + ": "
                    + (filesWork ? ok : missing)
                    + "\n" + getString(R.string.file_shuttle_req_overlay_personal) + ": "
                    + (overlayPersonal ? ok : missing)
                    + "\n" + getString(R.string.file_shuttle_req_overlay_work) + ": "
                    + (overlayWork ? ok : missing)
                    + "\n\n" + getString(R.string.file_shuttle_req_tail);
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.file_shuttle_req_title)
                    .setMessage(status)
                    .setPositiveButton(R.string.file_shuttle_req_all_files_btn, (dialog, which) ->
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)))
                    .setNeutralButton(R.string.file_shuttle_req_overlay_btn, (dialog, which) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getContext().getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return false;
        } else if (preference == mPrefBlockContactsSearching) {
            mManager.setBlockContactsSearchingEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefAutoFreezeService) {
            mManager.setAutoFreezeServiceEnabled((boolean) newState);
            return true;
        } else if (preference == mPrefAutoFreezeDelay) {
            mManager.setAutoFreezeDelay(Integer.parseInt((String) newState));
            updateAutoFreezeDelay();
            return true;
        } else if (SETTINGS_TRANSFER_AUTOWIPE.equals(preference.getKey())) {
            try {
                int millis = Integer.parseInt(String.valueOf(newState));
                LocalStorageManager.getInstance()
                        .setInt(LocalStorageManager.PREF_SHUTTLE_AUTOWIPE, millis);
                if (preference instanceof androidx.preference.DropDownPreference) {
                    androidx.preference.DropDownPreference dp =
                            (androidx.preference.DropDownPreference) preference;
                    dp.setValue(String.valueOf(millis));
                    updateWipeSummary(dp);
                }
            } catch (Exception ignored) { }
            return true;
        } else if (preference == mPrefProtectScreen || preference == mPrefProtectNotif
                || preference == mPrefProtectBackup || preference == mPrefProtectBluetooth
                || preference == mPrefProtectNotifListeners
                || preference == mPrefProtectCallerId) {
            boolean on = (boolean) newState;
            boolean ok = applyProtectState((TwoStatePreference) preference, on);
            if (!ok) {
                toast(getString(R.string.protect_failed));
                return false;
            }
            return true;
        } else if (preference == mPrefAllowAllCrossProfile) {
            boolean enabled = (boolean) newState;
            LocalStorageManager.getInstance()
                    .setBoolean(LocalStorageManager.PREF_ALLOW_ALL_CROSS_PROFILE, enabled);
            applyCrossProfileForAll(enabled);
            return true;
        } else if (preference == mPrefSkipForeground) {
            boolean enabled = (boolean) newState;
            if (!enabled) {
                mManager.setSkipForegroundEnabled(false);
                return true;
            }

            boolean hasPermission = ensureSpecialAccessPermission(() -> {
                try {
                    return mServiceWork.hasUsageStatsPermission() && Utility.checkUsageStatsPermission(getContext());
                } catch (RemoteException e) {
                    return false;
                }
            }, R.string.request_usage_stats, Settings.ACTION_USAGE_ACCESS_SETTINGS);

            if (!hasPermission)
                return false;

            mManager.setSkipForegroundEnabled(true);
            return true;
        } else {
            return false;
        }
    }

    private interface CheckPermissionCallback {
        boolean check();
    }

    private boolean ensureSpecialAccessPermission(CheckPermissionCallback checkPermission, int alertRes, String settingsAction) {
        if (!checkPermission.check()) {
            new AlertDialog.Builder(getContext())
                    .setMessage(alertRes)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> startActivity(new Intent(settingsAction)))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> dialog.dismiss())
                    .show();
            return false;
        } else {
            return true;
        }
    }
}
