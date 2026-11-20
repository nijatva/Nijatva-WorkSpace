package com.nijatva.workspace.receivers;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import java.io.File;

/**
 * Handles the two scheduled automation jobs:
 *  - re-freeze an app whose "leash" timer has expired
 *  - wipe stale files from the File Shuttle staging folder
 *
 * Both run inside the work profile, where this app is the profile owner.
 */
public class AutomationReceiver extends BroadcastReceiver {

    public static final String ACTION_REFREEZE = "com.nijatva.workspace.action.REFREEZE";
    public static final String ACTION_WIPE_SHUTTLE = "com.nijatva.workspace.action.WIPE_SHUTTLE";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_AGE = "age";
    public static final String ACTION_FREEZE_ALL = "com.nijatva.workspace.action.FREEZE_ALL";
    public static final String ACTION_UNFREEZE_ALL = "com.nijatva.workspace.action.UNFREEZE_ALL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_REFREEZE.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_PACKAGE);
            if (pkg == null) return;
            try {
                DevicePolicyManager dpm =
                        (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(context, ShelterDeviceAdminReceiver.class);
                if (dpm != null && dpm.isProfileOwnerApp(context.getPackageName())) {
                    dpm.setApplicationHidden(admin, pkg, true);
                }
            } catch (Exception ignored) {
            }
        } else if (ACTION_FREEZE_ALL.equals(action) || ACTION_UNFREEZE_ALL.equals(action)) {
            boolean freeze = ACTION_FREEZE_ALL.equals(action);
            try {
                DevicePolicyManager dpm =
                        (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName admin = new ComponentName(context, ShelterDeviceAdminReceiver.class);
                if (dpm != null && dpm.isProfileOwnerApp(context.getPackageName())) {
                    android.content.pm.PackageManager pm = context.getPackageManager();
                    for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                        if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                        if (ai.packageName.equals(context.getPackageName())) continue;
                        try {
                            dpm.setApplicationHidden(admin, ai.packageName, freeze);
                        } catch (Exception ignored) { }
                    }
                }
            } catch (Exception ignored) { }
        } else if (ACTION_WIPE_SHUTTLE.equals(action)) {
            long age = intent.getLongExtra(EXTRA_AGE, 0L);
            if (age <= 0) return;
            wipe(context, age);
        }
    }

    private void wipe(Context context, long ageMillis) {
        File dir = new File(Environment.getExternalStorageDirectory(), "Nijatva/Shuttle");
        if (!dir.isDirectory()) return;
        long cutoff = System.currentTimeMillis() - ageMillis;
        File[] files = dir.listFiles();
        if (files == null) return;
        String base;
        try { base = dir.getCanonicalPath(); } catch (Exception e) { base = dir.getAbsolutePath(); }
        for (File f : files) {
            try {
                if (!f.isFile()) continue;
                if (!f.getCanonicalPath().startsWith(base)) continue;
                if (f.lastModified() <= cutoff) f.delete();
            } catch (Exception ignored) {
            }
        }
    }
}
