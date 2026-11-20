package com.nijatva.workspace.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Local permission baseline: save once, compare later.
 * Android has no API that reports historical permission changes, so Nijatva keeps its own snapshots.
 */
public final class PermissionSnapshot {

    private static final String FILE = "permission-baseline.json";

    private PermissionSnapshot() {
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE);
    }

    /** Saves a baseline and returns how many permission entries were recorded. */
    public static int save(Context ctx) {
        JSONObject all = new JSONObject();
        int count = 0;
        try {
            PackageManager pm = ctx.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                if (pi.requestedPermissions == null) continue;
                JSONObject perms = new JSONObject();
                for (String p : pi.requestedPermissions) {
                    boolean granted =
                            pm.checkPermission(p, pi.packageName) == PackageManager.PERMISSION_GRANTED;
                    perms.put(p, granted);
                    count++;
                }
                all.put(pi.packageName, perms);
            }
            try (FileOutputStream out = new FileOutputStream(file(ctx))) {
                out.write(all.toString().getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
        }
        return count;
    }

    public static boolean hasBaseline(Context ctx) {
        return file(ctx).exists();
    }

    /** Returns a JSON array of {package, permission, before, after} for every difference. */
    public static String diff(Context ctx) {
        JSONArray changes = new JSONArray();
        File f = file(ctx);
        if (!f.exists()) return changes.toString();
        try {
            byte[] buf = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                //noinspection ResultOfMethodCallIgnored
                in.read(buf);
            }
            JSONObject old = new JSONObject(new String(buf, "UTF-8"));
            PackageManager pm = ctx.getPackageManager();
            for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
                if (pi.requestedPermissions == null) continue;
                JSONObject oldPerms = old.optJSONObject(pi.packageName);
                for (String p : pi.requestedPermissions) {
                    boolean now =
                            pm.checkPermission(p, pi.packageName) == PackageManager.PERMISSION_GRANTED;
                    boolean before = oldPerms != null && oldPerms.optBoolean(p, false);
                    if (before != now) {
                        JSONObject o = new JSONObject();
                        o.put("package", pi.packageName);
                        o.put("permission", p);
                        o.put("before", before);
                        o.put("after", now);
                        changes.put(o);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return changes.toString();
    }
}
