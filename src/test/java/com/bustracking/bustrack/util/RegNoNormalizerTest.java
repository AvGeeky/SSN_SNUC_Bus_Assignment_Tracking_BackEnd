package com.bustracking.bustrack.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegNoNormalizerTest {

    @Test
    void differentWritingsOfTheSamePlateMatch() {
        assertEquals("TN87H0937", RegNoNormalizer.normalize("TN87H0937"));
        assertEquals("TN87H0937", RegNoNormalizer.normalize("tn 87 h 0937"));
        assertEquals("TN87H0937", RegNoNormalizer.normalize("TN-87-H-0937"));
        assertEquals("TN87H0937", RegNoNormalizer.normalize("  TN87H0937\t"));
    }

    @Test
    void nullStaysNullAndJunkBecomesEmpty() {
        assertNull(RegNoNormalizer.normalize(null));
        assertEquals("", RegNoNormalizer.normalize(" - "));
    }
}
