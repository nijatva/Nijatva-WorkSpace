package com.nijatva.workspace.util;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;

/**
 * Read-only inspection of the profile and the apps inside it.
 * Everything here is local: PackageManager, DevicePolicyManager and UserManager queries.
 */
public final class AppInspector {

    private AppInspector() {
    }

    /** Profile integrity: are we really the profile owner, is the admin alive, is the profile usable. */
    public static String profileIntegrity(Context ctx, ComponentName admin) {
        JSONObject out = new JSONObject();
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            out.put("profileOwner", dpm != null && dpm.isProfileOwnerApp(ctx.getPackageName()));
            out.put("adminActive", dpm != null && admin != null && dpm.isAdminActive(admin));
            boolean available = true;
            try {
                UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE);
                if (um != null) available = !um.isQuietModeEnabled(android.os.Process.myUserHandle());
            } catch (Exception ignored) {
            }
            out.put("profileAvailable", available);
            out.put("packageName", ctx.getPackageName());
        } catch (Exception e) {
            try { out.put("error", e.getClass().getSimpleName()); } catch (Exception ignored) { }
        }
        return out.toString();
    }

    /** Inventory of every visible app: version, SDKs, installer, signing SHA-256, permissions. */
    public static String appInventory(Context ctx) {
        JSONArray apps = new JSONArray();
        try {
            PackageManager pm = ctx.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                JSONObject o = new JSONObject();
                try {
                    o.put("package", pi.packageName);
                    o.put("versionName", pi.versionName == null ? "" : pi.versionName);
                    o.put("versionCode", Build.VERSION.SDK_INT >= 28
                            ? pi.getLongVersionCode() : pi.versionCode);
                    o.put("targetSdk", pi.applicationInfo == null ? 0 : pi.applicationInfo.targetSdkVersion);
                    o.put("minSdk", pi.applicationInfo == null ? 0 : pi.applicationInfo.minSdkVersion);
                    o.put("installer", pm.getInstallerPackageName(pi.packageName));
                    o.put("signature", signingSha256(pm, pi.packageName));
                    int requested = pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length;
                    int granted = 0;
                    if (pi.requestedPermissions != null) {
                        for (String p : pi.requestedPermissions) {
                            if (pm.checkPermission(p, pi.packageName) == PackageManager.PERMISSION_GRANTED) {
                                granted++;
                            }
                        }
                    }
                    o.put("permissionsRequested", requested);
                    o.put("permissionsGranted", granted);
                } catch (Exception ignored) {
                }
                apps.put(o);
            }
        } catch (Exception ignored) {
        }
        return apps.toString();
    }

    /** SHA-256 of the signing certificate, or "" when it cannot be read. */
    public static String signingSha256(PackageManager pm, String pkg) {
        try {
            PackageInfo pi;
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi.signingInfo == null) return "";
                sigs = pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
            } else {
                pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
                sigs = pi.signatures;
            }
            if (sigs == null || sigs.length == 0) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Exported component counts + signature for one package (read-only). */
    public static String appComponents(Context ctx, String pkg) {
        JSONObject o = new JSONObject();
        try {
            PackageManager pm = ctx.getPackageManager();
            int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS;
            PackageInfo pi = pm.getPackageInfo(pkg, flags);
            int expAct = 0, expSvc = 0, expRcv = 0, expPrv = 0;
            if (pi.activities != null) {
                for (android.content.pm.ActivityInfo a : pi.activities) if (a.exported) expAct++;
            }
            if (pi.services != null) {
                for (android.content.pm.ServiceInfo s2 : pi.services) if (s2.exported) expSvc++;
            }
            if (pi.receivers != null) {
                for (android.content.pm.ActivityInfo r : pi.receivers) if (r.exported) expRcv++;
            }
            if (pi.providers != null) {
                for (android.content.pm.ProviderInfo p : pi.providers) if (p.exported) expPrv++;
            }
            o.put("package", pkg);
            o.put("exportedActivities", expAct);
            o.put("exportedServices", expSvc);
            o.put("exportedReceivers", expRcv);
            o.put("exportedProviders", expPrv);
            o.put("signature", signingSha256(pm, pkg));
        } catch (Exception e) {
            try { o.put("error", e.getClass().getSimpleName()); } catch (Exception ignored) { }
        }
        return o.toString();
    }
}
