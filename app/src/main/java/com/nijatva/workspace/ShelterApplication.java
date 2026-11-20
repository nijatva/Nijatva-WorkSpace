package com.nijatva.workspace;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import com.nijatva.workspace.services.FileShuttleService;
import com.nijatva.workspace.services.ShelterService;
import com.nijatva.workspace.util.LocalStorageManager;
import com.nijatva.workspace.util.SettingsManager;
import com.nijatva.workspace.util.ThemeBridge;

public class ShelterApplication extends Application {
    private ServiceConnection mShelterServiceConnection = null;
    private ServiceConnection mFileShuttleServiceConnection = null;

    @Override
    public void onCreate() {
        super.onCreate();
        LocalStorageManager.initialize(this);
        SettingsManager.initialize(this);
        ThemeBridge.applyNightMode(this);
        ThemeBridge.applyLanguage(this);
    }

    public void bindShelterService(ServiceConnection conn, boolean foreground) {
        unbindShelterService();
        Intent intent = new Intent(getApplicationContext(), ShelterService.class);
        intent.putExtra("foreground", foreground);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mShelterServiceConnection = conn;
    }

    public void bindFileShuttleService(ServiceConnection conn) {
        unbindFileShuttleService();;
        Intent intent = new Intent(getApplicationContext(), FileShuttleService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        mFileShuttleServiceConnection = conn;
    }

    public void unbindShelterService() {
        if (mShelterServiceConnection != null) {
            try {
                unbindService(mShelterServiceConnection);
            } catch (Exception e) {
                // This method call might fail if the service is already unbound
                // just ignore anything that might happen.
                // We will be stopping already if this would ever happen.
            }
        }

        mShelterServiceConnection = null;
    }

    public void unbindFileShuttleService() {
        if (mFileShuttleServiceConnection != null) {
            try {
                unbindService(mFileShuttleServiceConnection);
            } catch (Exception e) {
                // ...
            }
        }

        mFileShuttleServiceConnection = null;
    }
}
