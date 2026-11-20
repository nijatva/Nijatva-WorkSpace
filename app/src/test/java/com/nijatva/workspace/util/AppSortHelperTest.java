package com.nijatva.workspace.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AppSortHelperTest {

    private static final class App implements AppSortHelper.Sortable {
        private final String label;
        private final boolean hidden;

        App(String label, boolean hidden) {
            this.label = label;
            this.hidden = hidden;
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public boolean isHidden() {
            return hidden;
        }
    }

    private static List<App> sample() {
        return new ArrayList<>(Arrays.asList(
                new App("WhatsApp", false),
                new App("Chrome", true),
                new App("Drive", false),
                new App("Calendar", true)));
    }

    private static String labels(List<App> list) {
        StringBuilder sb = new StringBuilder();
        for (App a : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(a.getLabel());
        }
        return sb.toString();
    }

    @Test
    public void nameAZ_sortsAlphabeticallyIgnoringCase() {
        List<App> list = sample();
        AppSortHelper.sort(list, AppSortHelper.SORT_NAME_AZ);
        assertEquals("Calendar,Chrome,Drive,WhatsApp", labels(list));
    }

    @Test
    public void nameZA_sortsReverseAlphabetically() {
        List<App> list = sample();
        AppSortHelper.sort(list, AppSortHelper.SORT_NAME_ZA);
        assertEquals("WhatsApp,Drive,Chrome,Calendar", labels(list));
    }

    @Test
    public void frozenFirst_putsHiddenAppsOnTop() {
        List<App> list = sample();
        AppSortHelper.sort(list, AppSortHelper.SORT_FROZEN_FIRST);
        assertEquals("Calendar,Chrome,Drive,WhatsApp", labels(list));
        assertEquals(true, list.get(0).isHidden());
        assertEquals(true, list.get(1).isHidden());
    }

    @Test
    public void activeFirst_putsVisibleAppsOnTop() {
        List<App> list = sample();
        AppSortHelper.sort(list, AppSortHelper.SORT_ACTIVE_FIRST);
        assertEquals("Drive,WhatsApp,Calendar,Chrome", labels(list));
        assertEquals(false, list.get(0).isHidden());
    }

    @Test
    public void nullAndEmptyLabels_doNotCrash() {
        List<App> list = new ArrayList<>(Arrays.asList(
                new App(null, false), new App("Alpha", false), new App("", false)));
        AppSortHelper.sort(list, AppSortHelper.SORT_NAME_AZ);
        assertEquals(3, list.size());
        assertEquals("Alpha", list.get(2).getLabel());
    }

    @Test
    public void smallOrNullLists_areHandled() {
        AppSortHelper.sort(null, AppSortHelper.SORT_NAME_AZ);
        List<App> single = new ArrayList<>();
        single.add(new App("Only", false));
        AppSortHelper.sort(single, AppSortHelper.SORT_FROZEN_FIRST);
        assertEquals("Only", labels(single));
    }
}
