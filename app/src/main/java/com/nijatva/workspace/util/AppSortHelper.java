package com.nijatva.workspace.util;

import java.util.List;

/**
 * Sorting rules for the app list.
 *
 * Deliberately free of Android classes so the rules can be unit-tested on the
 * JVM without a device or Robolectric.
 */
public final class AppSortHelper {

    public static final int SORT_NAME_AZ = 0;
    public static final int SORT_NAME_ZA = 1;
    public static final int SORT_FROZEN_FIRST = 2;
    public static final int SORT_ACTIVE_FIRST = 3;
    public static final int SORT_RECENT = 4;

    /** The minimum an item needs to expose to be sortable. */
    public interface Sortable {
        String getLabel();
        boolean isHidden();
    }

    private AppSortHelper() {
    }

    /** Recently installed first; needs a package -> firstInstallTime map. */
    public static <T extends Sortable> void sortRecent(List<T> list,
                                                       java.util.Map<String, Long> installTimes,
                                                       java.util.function.Function<T, String> packageOf) {
        if (list == null || list.size() < 2) return;
        list.sort((a, b) -> {
            long ta = installTimes.getOrDefault(packageOf.apply(a), 0L);
            long tb = installTimes.getOrDefault(packageOf.apply(b), 0L);
            if (ta != tb) return Long.compare(tb, ta);
            return a.getLabel() == null ? 0 : a.getLabel().compareToIgnoreCase(
                    b.getLabel() == null ? "" : b.getLabel());
        });
    }

    public static <T extends Sortable> void sort(List<T> list, int mode) {
        if (list == null || list.size() < 2) return;
        switch (mode) {
            case SORT_NAME_ZA:
                list.sort((a, b) -> compareLabels(b, a));
                break;
            case SORT_FROZEN_FIRST:
                list.sort((a, b) -> a.isHidden() == b.isHidden()
                        ? compareLabels(a, b)
                        : (a.isHidden() ? -1 : 1));
                break;
            case SORT_ACTIVE_FIRST:
                list.sort((a, b) -> a.isHidden() == b.isHidden()
                        ? compareLabels(a, b)
                        : (a.isHidden() ? 1 : -1));
                break;
            case SORT_NAME_AZ:
            default:
                list.sort(AppSortHelper::compareLabels);
                break;
        }
    }

    private static int compareLabels(Sortable a, Sortable b) {
        String la = a.getLabel() == null ? "" : a.getLabel();
        String lb = b.getLabel() == null ? "" : b.getLabel();
        return la.compareToIgnoreCase(lb);
    }
}
