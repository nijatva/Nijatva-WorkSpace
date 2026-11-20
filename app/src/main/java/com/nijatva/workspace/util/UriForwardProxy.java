package com.nijatva.workspace.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Nijatva Work Space — carries a content URI across the profile boundary.
 *
 * <p>A {@link Uri} means nothing in the other profile: the authority, the grant and the
 * {@link ContentResolver} all belong to the profile that created it. What does travel is a
 * {@link IBinder}, so this class hands the other side a tiny {@link IUriOpener} stub that knows
 * how to open <em>this</em> profile's URI on request. The other profile then asks for a file
 * descriptor and reads it through the relayed binder.</p>
 *
 * <p>Used for one-shot transfers only — the APK install path and the File Shuttle. The binder
 * dies with the process that created it, so a proxy must not be cached beyond the operation
 * that created it.</p>
 */
public class UriForwardProxy implements Parcelable {

    private static final String TAG = "UriForwardProxy";

    public static final Parcelable.Creator<UriForwardProxy> CREATOR =
            new Parcelable.Creator<UriForwardProxy>() {
                @Override
                public UriForwardProxy[] newArray(int size) {
                    return new UriForwardProxy[size];
                }

                @Override
                public UriForwardProxy createFromParcel(Parcel source) {
                    IBinder[] holders = new IBinder[1];
                    source.readBinderArray(holders);
                    return new UriForwardProxy(IUriOpener.Stub.asInterface(holders[0]));
                }
            };

    @Nullable
    private final IUriOpener mOpener;

    /** Parcel-only constructor: wraps the stub that arrived from the other profile. */
    private UriForwardProxy(@Nullable IUriOpener opener) {
        mOpener = opener;
    }

    /**
     * Wraps a URI owned by {@code context}'s profile.
     *
     * <p>The resolver is captured here, so the proxy keeps working after the calling Activity
     * goes away — the grant lives on the resolver, not on the Activity.</p>
     */
    public UriForwardProxy(@NonNull Context context, @NonNull Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        mOpener = new IUriOpener.Stub() {
            @Override
            public ParcelFileDescriptor openFile(String mode) {
                if (mode == null) return null;
                try {
                    return resolver.openFileDescriptor(uri, mode);
                } catch (SecurityException e) {
                    // Grant was revoked or never existed — the caller gets a null descriptor and
                    // reports the failure instead of the app dying on a remote call.
                    Log.w(TAG, "No permission to open " + uri, e);
                    return null;
                } catch (Exception e) {
                    Log.w(TAG, "Cannot open " + uri + " (" + mode + ")", e);
                    return null;
                }
            }
        };
    }

    /**
     * Asks the owning profile for a descriptor on the wrapped URI.
     *
     * @param mode any mode accepted by
     *             {@link ContentResolver#openFileDescriptor(Uri, String)}, e.g. {@code "r"}
     * @return the descriptor, or {@code null} when the other side refused or died
     */
    @Nullable
    public ParcelFileDescriptor open(@NonNull String mode) {
        if (mOpener == null) return null;
        try {
            return mOpener.openFile(mode);
        } catch (RemoteException e) {
            Log.w(TAG, "Owning process is gone", e);
            return null;
        }
    }

    /** True when this proxy still has something to talk to. */
    public boolean isUsable() {
        if (mOpener == null) return false;
        try {
            return mOpener.asBinder().isBinderAlive();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // A single-element binder array keeps the wire format identical to the original AIDL
        // handshake, which both profiles already understand.
        dest.writeBinderArray(new IBinder[]{mOpener == null ? null : mOpener.asBinder()});
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
