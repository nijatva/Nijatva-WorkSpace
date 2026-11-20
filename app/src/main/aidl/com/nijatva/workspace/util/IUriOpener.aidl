// IUriOpener.aidl
package com.nijatva.workspace.util;

import android.os.ParcelFileDescriptor;

interface IUriOpener {
    ParcelFileDescriptor openFile(in String mode);
}
