// ILoadIconCallback.aidl
package com.nijatva.workspace.services;

import android.graphics.Bitmap;

interface ILoadIconCallback {
    void callback(in Bitmap icon);
}
