package com.nijatva.workspace.services;

import android.app.Activity;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.nijatva.workspace.R;
import com.nijatva.workspace.ShelterApplication;
import com.nijatva.workspace.receivers.ShelterDeviceAdminReceiver;
import com.nijatva.workspace.ui.DummyActivity;
import com.nijatva.workspace.util.ApplicationInfoWrapper;
import com.nijatva.workspace.util.FileProviderProxy;
import com.nijatva.workspace.util.UriForwardProxy;
import com.nijatva.workspace.util.Utility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class ShelterService extends Service {
    public static final int RESULT_CANNOT_INSTALL_SYSTEM_APP = 100001;

    private static final int NOTIFICATION_ID = 0x49a11;
    private DevicePolicyManager mPolicyManager = null;
    private boolean mIsProfileOwner = false;
    private PackageManager mPackageManager = null;
    private ComponentName mAdminComponent = null;
    // When we need to start an activity, we need something else to do it for us
    // as per background limitation of Android 10
    // We mostly need this for app cloning / installation
    // (we could probably just invoke DummyActivity directly from the other side,
    //  but there are cases where DummyActivity isn't needed, e.g. when cloning
    //  system applications. These cases can be handled without DummyActivity
    //  and without any visual interference.)
    // Note that this proxy can only start activity that is accessible to the
    // main profile and within the application itself.
    private IStartActivityProxy mStartActivityProxy = null;
    private IShelterService.Stub mBinder = new IShelterService.Stub() {
        @Override
        public void ping() {
            // Do nothing, just let the other side know we are alive
        }

        @Override
        public void stopShelterService(boolean kill) {
            // dirty: just wait for some time and kill this service itself
            new Thread(() -> {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {

                }

                ((ShelterApplication) getApplication()).unbindShelterService();

                if (kill && !(mIsProfileOwner && FreezeService.hasPendingAppToFreeze())) {
                    // Just kill the entire process if this signal is received and the process has nothing to do
                    System.exit(0);
                }
            }).start();
        }

        @Override
        public void getApps(IGetAppsCallback callback, boolean showAll) {
            new Thread(() -> {
                int pmFlags = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES;
                List<ApplicationInfoWrapper> list = mPackageManager.getInstalledApplications(pmFlags)
                        .stream()
                        .filter((it) -> !it.packageName.equals(getPackageName()))
                        .filter((it) -> {
                            boolean isSystem = (it.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                            boolean isHidden = isHidden(it.packageName);
                            boolean isInstalled = (it.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                            boolean canLaunch = mPackageManager.getLaunchIntentForPackage(it.packageName) != null;

                            return showAll || (!isSystem && isInstalled) || isHidden || canLaunch;
                        })
                        .map(ApplicationInfoWrapper::new)
                        .map((it) -> it.loadLabel(mPackageManager)
                                .setHidden(isHidden(it.getPackageName())))
                        .sorted((x, y) -> {
                            // Sort hidden apps at the last
                            if (x.isHidden() && !y.isHidden()) {
                                return 1;
                            } else if (!x.isHidden() && y.isHidden()) {
                                return -1;
                            } else {
                                return x.getLabel().compareTo(y.getLabel());
                            }
                        })
                        .collect(Collectors.toList());

                try {
                    callback.callback(list);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }

        @Override
        public void loadIcon(ApplicationInfoWrapper info, ILoadIconCallback callback) {
            new Thread(() -> {
                Bitmap icon = Utility.drawableToBitmap(info.getInfo().loadUnbadgedIcon(mPackageManager));

                try {
                    callback.callback(icon);
                } catch (RemoteException e) {
                    // Do Nothing
                }
            }).start();
        }

        @Override
        public void installApp(ApplicationInfoWrapper app, IAppInstallCallback callback) throws RemoteException {
            if (!app.isSystem()) {
                // Installing a non-system app requires firing up PackageInstaller
                // Delegate this operation to DummyActivity because
                // Only it can receive a result
                Intent intent = new Intent(DummyActivity.INSTALL_PACKAGE);
                intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
                intent.putExtra("package", app.getPackageName());
                intent.putExtra("apk", app.getSourceDir());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    intent.putExtra("split_apks", app.getSplitApks());

                // Send the callback to the DummyActivity
                Bundle callbackExtra = new Bundle();
                callbackExtra.putBinder("callback", callback.asBinder());
                intent.putExtra("callback", callbackExtra);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);
                if (mStartActivityProxy != null)
                    mStartActivityProxy.startActivity(intent);
            } else {
                if (mIsProfileOwner) {
                    // We can only enable system apps in our own profile
                    mPolicyManager.enableSystemApp(
                            mAdminComponent,
                            app.getPackageName());

                    // Also set the hidden state to false.
                    mPolicyManager.setApplicationHidden(
                            mAdminComponent,
                            app.getPackageName(), false);

                    callback.callback(Activity.RESULT_OK);
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP);
                }
            }
        }

        @Override
        public void installApk(UriForwardProxy uriForwarder, IAppInstallCallback callback) throws RemoteException {
            // Directly install an APK through a given Fd
            // instead of installing an existing one
            Intent intent = new Intent(DummyActivity.INSTALL_PACKAGE);
            intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
            // Generate a content Uri pointing to the Fd
            // DummyActivity is expected to release the Fd after finishing
            Uri uri = FileProviderProxy.setUriForwardProxy(uriForwarder, "apk");
            intent.putExtra("direct_install_apk", uri);

            // Send the callback to the DummyActivity
            Bundle callbackExtra = new Bundle();
            callbackExtra.putBinder("callback", callback.asBinder());
            intent.putExtra("callback", callbackExtra);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            DummyActivity.registerSameProcessRequest(intent);
            if (mStartActivityProxy != null)
                mStartActivityProxy.startActivity(intent);
        }

        @Override
        public void uninstallApp(ApplicationInfoWrapper app, IAppInstallCallback callback) throws RemoteException {
            if (!app.isSystem()) {
                // Similarly, fire up DummyActivity to do uninstallation for us
                Intent intent = new Intent(DummyActivity.UNINSTALL_PACKAGE);
                intent.setComponent(new ComponentName(ShelterService.this, DummyActivity.class));
                intent.putExtra("package", app.getPackageName());

                // Send the callback to the DummyActivity
                Bundle callbackExtra = new Bundle();
                callbackExtra.putBinder("callback", callback.asBinder());
                intent.putExtra("callback", callbackExtra);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                DummyActivity.registerSameProcessRequest(intent);

                if (mStartActivityProxy != null)
                    mStartActivityProxy.startActivity(intent);
            } else {
                if (mIsProfileOwner) {
                    // This is essentially the same as disabling the system app
                    // There is no way to reverse the "enableSystemApp" operation here
                    mPolicyManager.setApplicationHidden(
                            mAdminComponent,
                            app.getPackageName(), true);
                    callback.callback(Activity.RESULT_OK);
                } else {
                    callback.callback(RESULT_CANNOT_INSTALL_SYSTEM_APP);
                }
            }
        }

        @Override
        public void freezeApp(ApplicationInfoWrapper app) {
            if (!mIsProfileOwner)
                throw new IllegalArgumentException("Cannot freeze app without being profile owner");

            mPolicyManager.setApplicationHidden(
                    mAdminComponent,
                    app.getPackageName(), true);
        }

        @Override
        public void unfreezeApp(ApplicationInfoWrapper app) {
            if (!mIsProfileOwner)
                throw new IllegalArgumentException("Cannot unfreeze app without being profile owner");

            mPolicyManager.setApplicationHidden(
                    mAdminComponent,
                    app.getPackageName(), false);
        }

        @Override
        public boolean hasUsageStatsPermission() {
            return Utility.checkUsageStatsPermission(ShelterService.this);
        }

        @Override
        public boolean hasSystemAlertPermission() {
            return Utility.checkSystemAlertPermission(ShelterService.this);
        }

        @Override
        public boolean hasAllFileAccessPermission() {
            return Utility.checkAllFileAccessPermission();
        }

        @Override
        public List<String> getCrossProfileWidgetProviders() {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile widget providers without being profile owner");
            return mPolicyManager.getCrossProfileWidgetProviders(mAdminComponent);
        }

        @Override
        public boolean setCrossProfileWidgetProviderEnabled(String pkgName, boolean enabled) {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile widget providers without being profile owner");
            if (enabled) {
                return mPolicyManager.addCrossProfileWidgetProvider(mAdminComponent, pkgName);
            } else {
                return mPolicyManager.removeCrossProfileWidgetProvider(mAdminComponent, pkgName);
            }
        }

        @Override
        public void setStartActivityProxy(IStartActivityProxy proxy) {
            mStartActivityProxy = proxy;
        }

        @Override
        public List<String> getCrossProfilePackages() throws RemoteException {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile packages without being profile owner");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                throw new IllegalStateException("Cross-profile packages support is only available on Android 11 and later");
            return new ArrayList<>(mPolicyManager.getCrossProfilePackages(mAdminComponent));
        }

        @Override
        public void setCrossProfilePackages(List<String> packages) throws RemoteException {
            if (!mIsProfileOwner)
                throw new IllegalStateException("Cannot access cross-profile packages without being profile owner");
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                throw new IllegalStateException("Cross-profile packages support is only available on Android 11 and later");
            mPolicyManager.setCrossProfilePackages(mAdminComponent, new HashSet<>(packages));
        }

        // ---- Protect centre policies (profile owner only, all offline) ----

        @Override
        public boolean setScreenCaptureDisabled(boolean disabled) {
            if (!mIsProfileOwner) return false;
            try {
                mPolicyManager.setScreenCaptureDisabled(mAdminComponent, disabled);
                return mPolicyManager.getScreenCaptureDisabled(mAdminComponent) == disabled;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isScreenCaptureDisabled() {
            if (!mIsProfileOwner) return false;
            try {
                return mPolicyManager.getScreenCaptureDisabled(mAdminComponent);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean setUnredactedNotificationsDisabled(boolean disabled) {
            if (!mIsProfileOwner) return false;
            try {
                // Keep every other keyguard flag intact, only touch this one bit
                int flags = mPolicyManager.getKeyguardDisabledFeatures(mAdminComponent);
                if (disabled) {
                    flags |= DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
                } else {
                    flags &= ~DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
                }
                mPolicyManager.setKeyguardDisabledFeatures(mAdminComponent, flags);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isUnredactedNotificationsDisabled() {
            if (!mIsProfileOwner) return false;
            try {
                int flags = mPolicyManager.getKeyguardDisabledFeatures(mAdminComponent);
                return (flags & DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS) != 0;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean setBackupServiceEnabled(boolean enabled) {
            if (!mIsProfileOwner) return false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
            try {
                mPolicyManager.setBackupServiceEnabled(mAdminComponent, enabled);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isBackupServiceEnabled() {
            if (!mIsProfileOwner) return false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
            try {
                return mPolicyManager.isBackupServiceEnabled(mAdminComponent);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean setBluetoothContactSharingDisabled(boolean disabled) {
            if (!mIsProfileOwner) return false;
            try {
                mPolicyManager.setBluetoothContactSharingDisabled(mAdminComponent, disabled);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isBluetoothContactSharingDisabled() {
            if (!mIsProfileOwner) return false;
            try {
                return mPolicyManager.getBluetoothContactSharingDisabled(mAdminComponent);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean setUninstallBlocked(String packageName, boolean blocked) {
            if (!mIsProfileOwner) return false;
            try {
                mPolicyManager.setUninstallBlocked(mAdminComponent, packageName, blocked);
                return mPolicyManager.isUninstallBlocked(mAdminComponent, packageName) == blocked;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isUninstallBlocked(String packageName) {
            if (!mIsProfileOwner) return false;
            try {
                return mPolicyManager.isUninstallBlocked(mAdminComponent, packageName);
            } catch (Exception e) {
                return false;
            }
        }

        // --- Cross-profile notification listener firewall (API 26+) ---
        @Override
        public boolean setNotificationListenerFirewall(boolean enabled) {
            if (!mIsProfileOwner) return false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
            try {
                if (enabled) {
                    // empty list = only system notification listeners may listen
                    mPolicyManager.setPermittedCrossProfileNotificationListeners(
                            mAdminComponent, new ArrayList<String>());
                } else {
                    // null = remove the restriction
                    mPolicyManager.setPermittedCrossProfileNotificationListeners(
                            mAdminComponent, null);
                }
                return isNotificationListenerFirewallEnabled() == enabled;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isNotificationListenerFirewallEnabled() {
            if (!mIsProfileOwner) return false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
            try {
                List<String> allowed =
                        mPolicyManager.getPermittedCrossProfileNotificationListeners(mAdminComponent);
                // null means "all allowed"; an empty list means "system only"
                return allowed != null && allowed.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        // --- Caller-ID isolation ---
        @Override
        public boolean setCallerIdIsolation(boolean enabled) {
            if (!mIsProfileOwner) return false;
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    if (enabled) {
                        mPolicyManager.setManagedProfileCallerIdAccessPolicy(
                                new android.app.admin.PackagePolicy(
                                        android.app.admin.PackagePolicy
                                                .PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM,
                                        new HashSet<String>()));
                    } else {
                        mPolicyManager.setManagedProfileCallerIdAccessPolicy(null);
                    }
                } else {
                    mPolicyManager.setCrossProfileCallerIdDisabled(mAdminComponent, enabled);
                }
                return isCallerIdIsolationEnabled() == enabled;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean isCallerIdIsolationEnabled() {
            if (!mIsProfileOwner) return false;
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    android.app.admin.PackagePolicy policy =
                            mPolicyManager.getManagedProfileCallerIdAccessPolicy();
                    return policy != null && policy.getPackageNames() != null
                            && policy.getPackageNames().isEmpty();
                }
                return mPolicyManager.getCrossProfileCallerIdDisabled(mAdminComponent);
            } catch (Exception e) {
                return false;
            }
        }

        // --- Trusted CA inventory (read only, transparency) ---
        @Override
        public boolean setSuspended(String packageName, boolean suspended) {
            if (!mIsProfileOwner) return false;
            try {
                String[] failed = mPolicyManager.setPackagesSuspended(
                        mAdminComponent, new String[]{packageName}, suspended);
                return failed == null || failed.length == 0;
            } catch (Exception e) { return false; }
        }

        @Override
        public boolean isSuspended(String packageName) {
            if (!mIsProfileOwner) return false;
            try { return mPolicyManager.isPackageSuspended(mAdminComponent, packageName); }
            catch (Exception e) { return false; }
        }

        @Override
        public boolean setTamperGuard(String packageName, boolean enabled) {
            if (!mIsProfileOwner || Build.VERSION.SDK_INT < 30) return false;
            try {
                List<String> current =
                        new ArrayList<>(mPolicyManager.getUserControlDisabledPackages(mAdminComponent));
                if (enabled && !current.contains(packageName)) current.add(packageName);
                if (!enabled) current.remove(packageName);
                mPolicyManager.setUserControlDisabledPackages(mAdminComponent, current);
                return isTamperGuarded(packageName) == enabled;
            } catch (Exception e) { return false; }
        }

        @Override
        public boolean isTamperGuarded(String packageName) {
            if (!mIsProfileOwner || Build.VERSION.SDK_INT < 30) return false;
            try {
                List<String> current = mPolicyManager.getUserControlDisabledPackages(mAdminComponent);
                return current != null && current.contains(packageName);
            } catch (Exception e) { return false; }
        }

        @Override
        public boolean unfreezeFor(String packageName, long millis) {
            if (!mIsProfileOwner) return false;
            try {
                mPolicyManager.setApplicationHidden(mAdminComponent, packageName, false);
                android.content.Intent i = new android.content.Intent(
                        com.nijatva.workspace.receivers.AutomationReceiver.ACTION_REFREEZE);
                i.setClass(ShelterService.this,
                        com.nijatva.workspace.receivers.AutomationReceiver.class);
                i.putExtra(com.nijatva.workspace.receivers.AutomationReceiver.EXTRA_PACKAGE,
                        packageName);
                android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                        ShelterService.this, packageName.hashCode(), i,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_IMMUTABLE);
                android.app.AlarmManager am = (android.app.AlarmManager)
                        getSystemService(ALARM_SERVICE);
                if (am != null) {
                    am.set(android.app.AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + millis, pi);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String getAppComponents(String packageName) {
            return com.nijatva.workspace.util.AppInspector
                    .appComponents(ShelterService.this, packageName);
        }

        @Override
        public String verifyPolicies() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                o.put("screenCapture", isScreenCaptureDisabled());
                o.put("notifPrivacy", isUnredactedNotificationsDisabled());
                o.put("backupBlocked", !isBackupServiceEnabled());
                o.put("bluetoothBlocked", isBluetoothContactSharingDisabled());
                o.put("notifFirewall", isNotificationListenerFirewallEnabled());
                o.put("callerIdIsolated", isCallerIdIsolationEnabled());
                o.put("profileOwner", mIsProfileOwner);
            } catch (Exception e) {
                try { o.put("error", e.getClass().getSimpleName()); } catch (Exception ignored) { }
            }
            return o.toString();
        }

        private java.io.File shuttleDir() {
            return new java.io.File(android.os.Environment.getExternalStorageDirectory(),
                    "Nijatva/Shuttle");
        }

        @Override
        public int shuttleFileCount() {
            java.io.File dir = shuttleDir();
            if (!dir.isDirectory()) return 0;
            String[] names = dir.list();
            return names == null ? 0 : names.length;
        }

        @Override
        public long shuttleSizeBytes() {
            java.io.File dir = shuttleDir();
            if (!dir.isDirectory()) return 0L;
            long total = 0;
            java.io.File[] files = dir.listFiles();
            if (files != null) for (java.io.File f : files) if (f.isFile()) total += f.length();
            return total;
        }

        @Override
        public int wipeShuttle(long olderThanMillis) {
            java.io.File dir = shuttleDir();
            if (!dir.isDirectory()) return 0;
            long cutoff = olderThanMillis <= 0 ? Long.MAX_VALUE
                    : System.currentTimeMillis() - olderThanMillis;
            int removed = 0;
            java.io.File[] files = dir.listFiles();
            if (files == null) return 0;
            String base;
            try { base = dir.getCanonicalPath(); } catch (Exception e) { base = dir.getAbsolutePath(); }
            for (java.io.File f : files) {
                try {
                    if (!f.isFile()) continue;
                    // never delete outside the staging folder
                    if (!f.getCanonicalPath().startsWith(base)) continue;
                    if (f.lastModified() <= cutoff) {
                        if (f.delete()) removed++;
                    }
                } catch (Exception ignored) { }
            }
            return removed;
        }

        @Override
        public String getProfileIntegrity() {
            return com.nijatva.workspace.util.AppInspector
                    .profileIntegrity(ShelterService.this, mAdminComponent);
        }

        @Override
        public String getAppInventory() {
            return com.nijatva.workspace.util.AppInspector
                    .appInventory(ShelterService.this);
        }

        @Override
        public String savePermissionBaseline() {
            int n = com.nijatva.workspace.util.PermissionSnapshot
                    .save(ShelterService.this);
            return String.valueOf(n);
        }

        @Override
        public String getPermissionChanges() {
            return com.nijatva.workspace.util.PermissionSnapshot
                    .diff(ShelterService.this);
        }

        @Override
        public List<String> getInstalledCaCerts() {
            List<String> out = new ArrayList<>();
            if (!mIsProfileOwner) return out;
            try {
                List<byte[]> certs = mPolicyManager.getInstalledCaCerts(mAdminComponent);
                if (certs == null) return out;
                java.security.cert.CertificateFactory cf =
                        java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                for (byte[] der : certs) {
                    try {
                        java.security.cert.X509Certificate c =
                                (java.security.cert.X509Certificate) cf.generateCertificate(
                                        new java.io.ByteArrayInputStream(der));
                        StringBuilder fp = new StringBuilder();
                        for (byte b : md.digest(c.getEncoded())) {
                            fp.append(String.format("%02X", b));
                        }
                        String fpStr = fp.toString();
                        out.add(c.getSubjectDN().getName() + " | SHA-256: "
                                + fpStr.substring(0, Math.min(16, fpStr.length())) + "...");
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            return out;
        }
    };

    @Override
    public void onCreate() {
        mPolicyManager = getSystemService(DevicePolicyManager.class);
        mPackageManager = getPackageManager();
        mIsProfileOwner = mPolicyManager.isProfileOwnerApp(getPackageName());
        mAdminComponent = new ComponentName(getApplicationContext(), ShelterDeviceAdminReceiver.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getBooleanExtra("foreground", false)) {
            setForeground();
        }
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // Stop our foreground notification (if it was created at all) when
        // all clients have disconnected.
        // This helps to ensure no notification is left when the Shelter activity
        // is closed.
        stopForeground(true);
        return false;
    }

    private boolean isHidden(String packageName) {
        return mIsProfileOwner && mPolicyManager.isApplicationHidden(mAdminComponent, packageName);
    }

    private void setForeground() {
        startForeground(NOTIFICATION_ID, Utility.buildNotification(this,
                getString(R.string.app_name),
                getString(R.string.service_title),
                getString(R.string.service_desc),
                R.drawable.ic_notification_white_24dp));
    }
}
