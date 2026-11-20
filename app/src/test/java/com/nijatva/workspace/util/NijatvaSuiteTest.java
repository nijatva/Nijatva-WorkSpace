package com.nijatva.workspace.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NijatvaSuiteTest {

    @Test
    public void knownPackagesAreSuite() {
        assertTrue(NijatvaSuite.isSuite("com.nijatva.gallery"));
        assertTrue(NijatvaSuite.isSuite("com.nijatva.file"));
        assertTrue(NijatvaSuite.isSuite("com.nijatva.translate"));
        assertTrue(NijatvaSuite.isSuite("com.nijatva.appstore"));
    }

    @Test
    public void wildcardMatchesFutureApps() {
        assertTrue(NijatvaSuite.isSuite("com.nijatva.brandnew"));
        assertTrue(NijatvaSuite.isSuite("in.nijatva.browser"));
    }

    @Test
    public void otherAppsAreNotSuite() {
        assertFalse(NijatvaSuite.isSuite("com.whatsapp"));
        assertFalse(NijatvaSuite.isSuite("org.telegram.messenger"));
        assertFalse(NijatvaSuite.isSuite("com.android.chrome"));
    }

    @Test
    public void nullIsSafe() {
        assertFalse(NijatvaSuite.isSuite(null));
    }

    @Test
    public void emptyIsSafe() {
        assertFalse(NijatvaSuite.isSuite(""));
    }
}
