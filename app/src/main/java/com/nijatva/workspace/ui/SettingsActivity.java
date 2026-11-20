package com.nijatva.workspace.ui;

import android.content.Context;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.nijatva.workspace.R;
import com.nijatva.workspace.util.ThemeBridge;

/**
 * Nijatva Work Space — host for the settings screen.
 *
 * <p>All the real work is in {@link SettingsFragment}; this Activity only sets up the window,
 * the toolbar and the theme. The theme work is split in two because it has to happen at two
 * different moments:</p>
 *
 * <ul>
 *   <li>{@link #attachBaseContext} — the interface scale is applied before any resource is
 *       inflated, so text is already the right size on the first frame.</li>
 *   <li>{@link #onResume} — a theme or language change made in the settings screen itself is
 *       picked up and the Activity is recreated, so the user sees the change immediately.</li>
 * </ul>
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        // Scale must be in place before inflation; see ThemeBridge.applyScale.
        super.attachBaseContext(ThemeBridge.applyScale(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        ThemeBridge.applyToActivity(this);
        setContentView(R.layout.activity_settings);

        setUpToolbar();
    }

    private void setUpToolbar() {
        setSupportActionBar(findViewById(R.id.settings_toolbar));

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            // Back arrow instead of a close icon: settings is one level deep, nothing more.
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowTitleEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ThemeBridge.themeChanged(this)) {
            // Recreate rather than restyle in place: night mode and locale both need a fresh
            // resource pass, and a half-restyled settings screen looks broken.
            ThemeBridge.applyNightMode(this);
            ThemeBridge.applyLanguage(this);
            recreate();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Same as the system back gesture — finish, do not pop an empty back stack.
        finish();
        return true;
    }
}
