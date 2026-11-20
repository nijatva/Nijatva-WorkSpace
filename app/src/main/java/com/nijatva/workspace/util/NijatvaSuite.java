package com.nijatva.workspace.util;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registry of the Nijatva suite apps.
 *
 * Nijatva apps talk to each other over intents, never over shared code, so this
 * class only carries package names -- no dependency on any of those apps.
 */
public final class NijatvaSuite {

    /** Media, productivity, comms, web and tools that ship under the Nijatva name. */
    public static final List<String> PACKAGES = Arrays.asList(
            // media
            "com.nijatva.gallery",
            "com.nijatva.photos",
            "com.nijatva.videoplayer",
            "com.nijatva.music.player",
            "com.nijatva.camera",
            "com.nijatva.photo.editor",
            // productivity
            "com.nijatva.file",
            "com.nijatva.notes",
            // comms
            "com.nijatva.dialer",
            "com.nijatva.contacts",
            "com.nijatva.messages",
            // web
            "in.nijatva.browser",
            // tools
            "com.nijatva.translate",
            "com.nijatva.appstore"
    );

    private NijatvaSuite() {
    }

    /** True when the package belongs to the suite (also matches future nijatva.* apps). */
    public static boolean isSuite(String packageName) {
        if (packageName == null) return false;
        if (PACKAGES.contains(packageName)) return true;
        return packageName.startsWith("com.nijatva.") || packageName.startsWith("in.nijatva.");
    }

    /** Suite packages actually installed in this profile. */
    public static List<String> installedPackages(Context context) {
        List<String> found = new ArrayList<>();
        if (context == null) return found;
        PackageManager pm = context.getPackageManager();
        for (String pkg : PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                found.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // not installed here
            }
        }
        return found;
    }
}
