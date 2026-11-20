package com.nijatva.workspace.util;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.FileNotFoundException;

/**
 * Nijatva Work Space — a FileProvider whose only job is to forward one URI from the other
 * profile.
 *
 * <p>The work profile cannot read a main-profile {@code content://} URI, and the package
 * installer insists on one. So the main profile hands over a {@link UriForwardProxy} (a binder),
 * this provider publishes a local {@code content://com.nijatva.workspace.files/forward/...} URI,
 * and when the installer opens it the request is relayed over the binder.</p>
 *
 * <p>The authority and the {@code /forward/} path are baked into the manifest and into URIs
 * already handed to the system, so both are fixed. Only one forward is in flight at a time —
 * installs are serialised — and the slot is cleared with {@link #clearForwardProxy()} when the
 * operation ends.</p>
 */
public class FileProviderProxy extends FileProvider {

    /** Must match {@code android:authorities} in the manifest. */
    private static final String AUTHORITY_NAME = "com.nijatva.workspace.files";

    /** Any path under this prefix is relayed instead of served from disk. */
    private static final String FORWARD_PATH_PREFIX = "/forward/";

    /** The single in-flight forward. Guarded by {@code this} for cross-thread visibility. */
    @Nullable
    private static volatile UriForwardProxy sProxy;

    /**
     * Publishes a proxy and returns a URI the other profile can open.
     *
     * @param proxy  binder that can open the real URI in the owning profile
     * @param suffix short label used in the file name, e.g. {@code "apk"}
     * @return a local {@code content://} URI to hand to the installer
     */
    @NonNull
    public static synchronized Uri setUriForwardProxy(@Nullable UriForwardProxy proxy,
                                                      @NonNull String suffix) {
        sProxy = proxy;
        String safeSuffix = suffix.isEmpty() ? "bin" : suffix;
        return Uri.parse("content://" + AUTHORITY_NAME + FORWARD_PATH_PREFIX + "temp." + safeSuffix);
    }

    /** Releases the in-flight forward. Called when the install succeeds, fails or is cancelled. */
    public static synchronized void clearForwardProxy() {
        sProxy = null;
    }

    /** True when a forward is currently published. */
    public static boolean hasForwardProxy() {
        return sProxy != null;
    }

    @NonNull
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        String path = uri.getPath();
        if (path != null && path.startsWith(FORWARD_PATH_PREFIX)) {
            UriForwardProxy proxy = sProxy;
            if (proxy == null) {
                throw new FileNotFoundException("No forward registered for " + uri);
            }
            ParcelFileDescriptor descriptor = proxy.open(mode);
            if (descriptor == null) {
                throw new FileNotFoundException("Other profile refused to open " + uri);
            }
            return descriptor;
        }
        return super.openFile(uri, mode);
    }
}
