package com.nijatva.workspace.receivers;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Nijatva Open API.
 *
 * Other Nijatva apps (same signing key) can ask Work Space to freeze, unfreeze or launch
 * a package. Guarded by a signature-level permission, so only apps signed with the same
 * key can call it.
 */
public class ApiReceiver extends BroadcastReceiver {

    public static final String ACTION_FREEZE = "com.nijatva.workspace.action.FREEZE";
    public static final String ACTION_UNFREEZE = "com.nijatva.workspace.action.UNFREEZE";
    public static final String ACTION_LAUNCH = "com.nijatva.workspace.action.LAUNCH";
    public static final String EXTRA_PACKAGE = "package";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        if (pkg == null || pkg.isEmpty()) return;

        String action = intent.getAction();
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, ShelterDeviceAdminReceiver.class);
        boolean owner = dpm != null && dpm.isProfileOwnerApp(context.getPackageName());
        try {
            if (ACTION_FREEZE.equals(action)) {
                if (owner) dpm.setApplicationHidden(admin, pkg, true);
            } else if (ACTION_UNFREEZE.equals(action)) {
                if (owner) dpm.setApplicationHidden(admin, pkg, false);
            } else if (ACTION_LAUNCH.equals(action)) {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
