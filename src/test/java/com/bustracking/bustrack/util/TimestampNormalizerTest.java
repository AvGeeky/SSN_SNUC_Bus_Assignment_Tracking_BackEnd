package com.bustracking.bustrack.util;

import com.bustracking.bustrack.util.TimestampNormalizer.Stamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampNormalizerTest {

    private static Stamp parse(String raw) {
        return TimestampNormalizer.parse(raw).orElseThrow(() -> new AssertionError("could not parse " + raw));
    }

    @Test
    void bothProviderFormatsBecomeTheSameStampInIst() {
        Stamp iso = parse("2026-09-30 12:18:45");
        Stamp dmy = parse("30-09-2026 12:18:45");

        assertEquals("2026-09-30 12:18:45", iso.text());
        assertEquals(iso, dmy);
        // 12:18:45 IST is 06:48:45 UTC
        assertEquals(Instant.parse("2026-09-30T06:48:45Z").toEpochMilli(), iso.epochMs());
    }

    @Test
    void otherShapesAreUnderstood() {
        long expected = parse("2026-09-30 12:18:45").epochMs();
        assertEquals(expected, parse("2026-09-30T12:18:45").epochMs());
        assertEquals(expected, parse("2026-09-30 12:18:45.250").epochMs() / 1000 * 1000);
        assertEquals(expected, parse("2026-09-30T06:48:45Z").epochMs());
        assertEquals(expected, parse(String.valueOf(expected)).epochMs());
        assertEquals(expected, parse(String.valueOf(expected / 1000)).epochMs());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(TimestampNormalizer.parse(null).isEmpty());
        assertTrue(TimestampNormalizer.parse("  ").isEmpty());
        assertTrue(TimestampNormalizer.parse("yesterday").isEmpty());
        assertTrue(TimestampNormalizer.parse("31-31-2026 12:00:00").isEmpty());
    }

    @Test
    void ofEpochFormatsInIstRegardlessOfJvmZone() {
        assertEquals("2026-09-30 12:18:45", TimestampNormalizer.of(parse("30-09-2026 12:18:45").epochMs()).text());
    }
}
