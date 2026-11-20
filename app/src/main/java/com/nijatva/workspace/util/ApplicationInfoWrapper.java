package com.nijatva.workspace.util;

import android.annotation.TargetApi;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Nijatva Work Space — one installed app, in a form that survives the profile boundary.
 *
 * <p>{@link ApplicationInfo} is already {@link Parcelable}, but it does not carry the label we
 * resolved in the work profile or the "hidden" flag that the freeze engine keeps. This wrapper
 * adds exactly those two things and nothing else.</p>
 *
 * <p><b>Parcel contract.</b> The wire order is {@code ApplicationInfo, label, hidden}. Both
 * profiles run the same APK version and exchange this object through the AIDL interface, so the
 * order must not change — an old work-profile service receiving a re-ordered parcel would read
 * garbage. Append new fields at the end, never insert.</p>
 */
public class ApplicationInfoWrapper implements Parcelable, AppSortHelper.Sortable {

    /**
     * Reads the fields back in the same order {@link #writeToParcel} writes them.
     */
    public static final Parcelable.Creator<ApplicationInfoWrapper> CREATOR =
            new Parcelable.Creator<ApplicationInfoWrapper>() {
                @Override
                public ApplicationInfoWrapper[] newArray(int size) {
                    return new ApplicationInfoWrapper[size];
                }

                @Override
                public ApplicationInfoWrapper createFromParcel(Parcel source) {
                    ApplicationInfo info = source.readParcelable(
                            ApplicationInfo.class.getClassLoader());
                    String label = source.readString();
                    boolean hidden = source.readByte() != 0;
                    return new ApplicationInfoWrapper(info, label, hidden);
                }
            };

    @Nullable
    private final ApplicationInfo mInfo;
    @Nullable
    private String mLabel;
    private boolean mHidden;

    /** Parcel-only constructor. */
    private ApplicationInfoWrapper(@Nullable ApplicationInfo info,
                                   @Nullable String label,
                                   boolean hidden) {
        mInfo = info;
        mLabel = label;
        mHidden = hidden;
    }

    public ApplicationInfoWrapper(@NonNull ApplicationInfo info) {
        mInfo = info;
    }

    /**
     * Resolves and caches the display label. Called from whichever profile owns the package
     * manager that can see the app, then parcelled over.
     */
    @NonNull
    public ApplicationInfoWrapper loadLabel(@NonNull PackageManager pm) {
        if (mInfo == null) {
            mLabel = null;
            return this;
        }
        try {
            CharSequence label = pm.getApplicationLabel(mInfo);
            mLabel = label == null ? mInfo.packageName : label.toString();
        } catch (RuntimeException e) {
            // Package removed between listing and labelling — fall back to the package name so
            // the row still renders instead of crashing the list.
            mLabel = mInfo.packageName;
        }
        return this;
    }

    /**
     * Marks the app as frozen (suspended by the work-profile policy).
     *
     * <p>This is the freeze flag the list and the sort rules use. It has nothing to do with
     * {@link ApplicationInfo#enabled}, which is a package-manager state we do not touch.</p>
     */
    @NonNull
    public ApplicationInfoWrapper setHidden(boolean hidden) {
        mHidden = hidden;
        return this;
    }

    @NonNull
    public String getPackageName() {
        return mInfo == null ? "" : mInfo.packageName;
    }

    /** @return the cached label, or the package name when {@link #loadLabel} has not run yet. */
    @Nullable
    @Override
    public String getLabel() {
        if (mLabel != null) return mLabel;
        return mInfo == null ? null : mInfo.packageName;
    }

    @Nullable
    public String getSourceDir() {
        return mInfo == null ? null : mInfo.sourceDir;
    }

    /** Split APKs for a bundle install; {@code null} for a single-APK package. */
    @TargetApi(Build.VERSION_CODES.O)
    @Nullable
    public String[] getSplitApks() {
        return mInfo == null ? null : mInfo.splitSourceDirs;
    }

    /** The package-manager enabled flag — not the freeze state. */
    public boolean getEnabled() {
        return mInfo != null && mInfo.enabled;
    }

    /** The freeze state. Named for {@link AppSortHelper.Sortable}. */
    @Override
    public boolean isHidden() {
        return mHidden;
    }

    public boolean isSystem() {
        return mInfo != null && (mInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** True when both wrappers describe the same package. Does not override {@code equals}. */
    public boolean samePackageAs(@Nullable ApplicationInfoWrapper other) {
        return other != null && getPackageName().equals(other.getPackageName());
    }

    @Nullable
    public ApplicationInfo getInfo() {
        return mInfo;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // Order is a contract: ApplicationInfo, label, hidden.
        dest.writeParcelable(mInfo, flags);
        dest.writeString(mLabel);
        dest.writeByte((byte) (mHidden ? 1 : 0));
    }

    /** Always 0 — a parcelable carries no file descriptors. */
    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "App{" + getPackageName() + (mHidden ? ", frozen" : "") + "}";
    }
}
