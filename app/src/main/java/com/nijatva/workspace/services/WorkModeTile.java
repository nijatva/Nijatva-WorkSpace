package com.nijatva.workspace.services;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.nijatva.workspace.R;
import com.nijatva.workspace.receivers.AutomationReceiver;

/** Quick Settings tile: one tap freezes every work app, the next tap unfreezes them. */
public class WorkModeTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean frozen = getSharedPreferences("ui", MODE_PRIVATE)
                .getBoolean("work_mode_frozen", false);
        Intent i = new Intent(frozen
                ? AutomationReceiver.ACTION_UNFREEZE_ALL
                : AutomationReceiver.ACTION_FREEZE_ALL);
        i.setClass(this, AutomationReceiver.class);
        sendBroadcast(i);
        getSharedPreferences("ui", MODE_PRIVATE).edit()
                .putBoolean("work_mode_frozen", !frozen).apply();
        new Handler(Looper.getMainLooper()).postDelayed(this::updateTile, 800);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean frozen = getSharedPreferences("ui", MODE_PRIVATE)
                .getBoolean("work_mode_frozen", false);
        tile.setState(frozen ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.setLabel(getString(R.string.app_name));
        tile.updateTile();
    }
}
