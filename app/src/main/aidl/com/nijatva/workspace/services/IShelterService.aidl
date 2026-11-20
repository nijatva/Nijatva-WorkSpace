// IShelterService.aidl
package com.nijatva.workspace.services;

import android.content.pm.ApplicationInfo;

import com.nijatva.workspace.services.IAppInstallCallback;
import com.nijatva.workspace.services.IGetAppsCallback;
import com.nijatva.workspace.services.ILoadIconCallback;
import com.nijatva.workspace.services.IStartActivityProxy;
import com.nijatva.workspace.util.ApplicationInfoWrapper;
import com.nijatva.workspace.util.UriForwardProxy;

interface IShelterService {
    void ping();
    void stopShelterService(boolean kill);
    void getApps(IGetAppsCallback callback, boolean showAll);
    void loadIcon(in ApplicationInfoWrapper info, ILoadIconCallback callback);
    void installApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void installApk(in UriForwardProxy uri, IAppInstallCallback callback);
    void uninstallApp(in ApplicationInfoWrapper app, IAppInstallCallback callback);
    void freezeApp(in ApplicationInfoWrapper app);
    void unfreezeApp(in ApplicationInfoWrapper app);
    boolean hasUsageStatsPermission();
    boolean hasSystemAlertPermission();
    boolean hasAllFileAccessPermission();
    List<String> getCrossProfileWidgetProviders();
    boolean setCrossProfileWidgetProviderEnabled(String pkgName, boolean enabled);
    void setStartActivityProxy(in IStartActivityProxy proxy);
    List<String> getCrossProfilePackages();
    void setCrossProfilePackages(in List<String> packages);

    // Protect centre: DevicePolicyManager policies that a profile owner may set
    boolean setScreenCaptureDisabled(boolean disabled);
    boolean isScreenCaptureDisabled();
    boolean setUnredactedNotificationsDisabled(boolean disabled);
    boolean isUnredactedNotificationsDisabled();
    boolean setBackupServiceEnabled(boolean enabled);
    boolean isBackupServiceEnabled();
    boolean setBluetoothContactSharingDisabled(boolean disabled);
    boolean isBluetoothContactSharingDisabled();

    // Uninstall guard
    boolean setUninstallBlocked(String packageName, boolean blocked);
    boolean isUninstallBlocked(String packageName);

    // Cross-profile notification listener firewall
    boolean setNotificationListenerFirewall(boolean enabled);
    boolean isNotificationListenerFirewallEnabled();

    // Caller-ID isolation
    boolean setCallerIdIsolation(boolean enabled);
    boolean isCallerIdIsolationEnabled();

    // User-installed trusted CA inventory (read only)
    List<String> getInstalledCaCerts();

    // Inspect (read-only JSON reports)
    String getProfileIntegrity();
    String getAppInventory();
    String savePermissionBaseline();
    String getPermissionChanges();

    // File Shuttle staging folder
    int shuttleFileCount();
    long shuttleSizeBytes();
    int wipeShuttle(long olderThanMillis);

    // Suspend / tamper guard
    boolean setSuspended(String packageName, boolean suspended);
    boolean isSuspended(String packageName);
    boolean setTamperGuard(String packageName, boolean enabled);
    boolean isTamperGuarded(String packageName);

    // Timed unfreeze ("leash")
    boolean unfreezeFor(String packageName, long millis);

    // Audit
    String verifyPolicies();
    String getAppComponents(String packageName);
}
