package com.nijatva.workspace.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Keeps language / interface scale / night mode applied across activities. */
public class ThemeBridge {
    private static final String PREFS = "ui";
    private static final String[] LANG_CODES = {
            "en", "as", "bn", "brx", "doi", "gu", "hi", "kn", "ks", "kok", "mai",
            "ml", "mni", "mr", "ne", "or", "pa", "sa", "sat", "sd", "ta", "te", "ur"};
    private static String sAppliedSignature = null;

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void applyNightMode(Context c) {
        int mode = prefs(c).getInt("theme", 0);
        if (mode == 1) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        else if (mode == 2) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void applyLanguage(Context c) {
        int idx = prefs(c).getInt("lang", 0);
        if (idx < 0 || idx >= LANG_CODES.length) idx = 0;
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(LANG_CODES[idx]));
    }

    public static Context applyScale(Context base) {
        int scale = prefs(base).getInt("scale", 100);
        if (scale <= 0 || scale == 100) return base;
        float f = Math.max(0.8f, Math.min(1.4f, scale / 100f));
        Configuration cfg = new Configuration();
        cfg.fontScale = f;
        return base.createConfigurationContext(cfg);
    }

    public static void applyToActivity(Activity a) {
        sAppliedSignature = signature(a);
    }

    public static boolean themeChanged(Context c) {
        return sAppliedSignature != null && !sAppliedSignature.equals(signature(c));
    }

    public static String signature(Context c) {
        SharedPreferences p = prefs(c);
        return p.getInt("theme", 0) + "|" + p.getInt("scale", 100) + "|" + p.getInt("lang", 0);
    }

    public static int themedColor(Context c, int attr) {
        android.util.TypedValue v = new android.util.TypedValue();
        if (c.getTheme().resolveAttribute(attr, v, true)) return v.data;
        return 0xFF888888;
    }
}
