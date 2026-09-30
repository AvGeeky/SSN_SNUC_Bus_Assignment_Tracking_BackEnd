package com.bustracking.bustrack.Services.GPSService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class NeoTrackWindowTest {

    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;

    private NeoTrackService service;
    private HashOperations hash;

    @BeforeEach
    void setUp() {
        service = new NeoTrackService();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        hash = mock(HashOperations.class);
        doReturn(hash).when(redis).opsForHash();
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
    }

    private long windowStart(String regNo, long now) {
        return (Long) ReflectionTestUtils.invokeMethod(service, "windowStart", regNo, now);
    }

    private void lastFetchedTo(String regNo, long to) {
        ((Map<String, Long>) ReflectionTestUtils.getField(service, "lastFetchedTo")).put(regNo, to);
    }

    @Test
    void firstEverFetchLooksBack23Hours() {
        long now = 1_000 * HOUR;
        assertEquals(now - 23 * HOUR, windowStart("TN87H0937", now));
    }

    @Test
    void afterAnOffPeakSleepTheWindowCoversTheWholeGap() {
        long now = 1_000 * HOUR;
        lastFetchedTo("TN87H0937", now - 15 * MINUTE);
        // 15 minutes since the last call, plus the 10s overlap. The old code only ever asked for the last 40s.
        assertEquals(now - 15 * MINUTE - 10_000, windowStart("TN87H0937", now));
    }

    @Test
    void frequentPollsKeepTheMinimumWindow() {
        long now = 1_000 * HOUR;
        lastFetchedTo("TN87H0937", now - 5_000);
        assertEquals(now - 40_000, windowStart("TN87H0937", now));
    }

    @Test
    void anOutageLongerThan23HoursIsCapped() {
        long now = 1_000 * HOUR;
        lastFetchedTo("TN87H0937", now - 40 * HOUR);
        assertEquals(now - 23 * HOUR, windowStart("TN87H0937", now));
    }

    @Test
    void afterARestartTheLastKnownPointInRedisIsUsed() {
        long now = System.currentTimeMillis();
        long lastPoint = now - 3 * HOUR;
        when(hash.get("LIVE_BUS_LOCATIONS", "TN87H0937")).thenReturn(
                "{\"regNo\":\"TN87H0937\",\"latitude\":12.7,\"longitude\":80.2,\"epochMs\":" + lastPoint + ",\"source\":\"NEOTRACKPurple\"}");

        assertEquals(lastPoint - 10_000, windowStart("TN87H0937", now));
    }

    @Test
    void aRedisEntryFromAnotherProviderIsNotTrusted() {
        long now = System.currentTimeMillis();
        when(hash.get("LIVE_BUS_LOCATIONS", "TN87H0937")).thenReturn(
                "{\"regNo\":\"TN87H0937\",\"latitude\":12.7,\"longitude\":80.2,\"epochMs\":" + (now - 60_000) + ",\"source\":\"API_1\"}");

        assertEquals(now - 23 * HOUR, windowStart("TN87H0937", now));
    }
}
