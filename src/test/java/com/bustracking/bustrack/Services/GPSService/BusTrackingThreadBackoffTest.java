package com.bustracking.bustrack.Services.GPSService;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusTrackingThreadBackoffTest {

    @Test
    void neverRetriesSoonerThanTheBaseAndNeverLaterThanMaxPlusJitter() {
        for (int run = 0; run < 200; run++) {
            BusTrackingThread.Backoff backoff = new BusTrackingThread.Backoff(65_000, 300_000);
            long first = backoff.nextDelay(0);
            assertTrue(first >= 65_000 && first <= 78_000, "first retry was " + first);   // 65s + up to 20%
            long second = backoff.nextDelay(0);
            assertTrue(second >= 130_000 && second <= 156_000, "second retry was " + second);
            long third = backoff.nextDelay(0);
            assertTrue(third >= 260_000 && third <= 312_000, "third retry was " + third);
            for (int i = 0; i < 10; i++) {
                long later = backoff.nextDelay(0);
                assertTrue(later >= 300_000 && later <= 360_000, "capped retry was " + later);
            }
        }
    }

    @Test
    void retryAfterWinsWhenLongerAndIsCapped() {
        BusTrackingThread.Backoff backoff = new BusTrackingThread.Backoff(10_000, 300_000);
        assertTrue(backoff.nextDelay(120_000) >= 120_000);
        assertTrue(backoff.nextDelay(Long.MAX_VALUE / 4) <= 60 * 60_000 * 1.2 + 1);
    }

    @Test
    void resetStartsOverFromTheBase() {
        BusTrackingThread.Backoff backoff = new BusTrackingThread.Backoff(10_000, 300_000);
        backoff.nextDelay(0);
        backoff.nextDelay(0);
        backoff.nextDelay(0);
        backoff.reset();
        assertEquals(0, backoff.failures());
        assertTrue(backoff.nextDelay(0) <= 12_000);
    }
}
