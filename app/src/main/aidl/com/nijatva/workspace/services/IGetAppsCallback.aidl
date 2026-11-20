// IGetAppsCallback.aidl
package com.nijatva.workspace.services;

import com.nijatva.workspace.util.ApplicationInfoWrapper;

interface IGetAppsCallback {
    void callback(in List<ApplicationInfoWrapper> apps);
}
