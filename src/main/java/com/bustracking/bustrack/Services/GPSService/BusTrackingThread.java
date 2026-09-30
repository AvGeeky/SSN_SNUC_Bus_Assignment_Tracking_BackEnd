package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.Services.GPSService.BusDataService.FetchResult;
import com.bustracking.bustrack.util.TimestampNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class BusTrackingThread implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BusTrackingThread.class);
    private final BusDataService dataService;

    @Autowired
    public BusTrackingThread(BusDataService dataService) {
        this.dataService=dataService;
    }

    private volatile boolean running = true;

    // Schedule Config
    private static final LocalTime MORNING_START = LocalTime.of(5, 30);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(12, 30);
    private static final LocalTime EVENING_END = LocalTime.of(19, 30);

    // Failure retry: first retry after the base delay, doubling on each consecutive failure up to the max.
    private static final long FAILURE_BASE_MS = 10_000;
    // The 1 minute APIs (Tata, GJ) allow one call per 1m 5s, so a retry may never come sooner than that.
    private static final long ONE_MINUTE_API_FAILURE_BASE_MS = 65_000;
    private static final long FAILURE_MAX_MS = 5 * 60_000;

    // Bad or missing env vars fall back to the defaults instead of crashing startup
    int REFRESH_SECONDS_FAST = GpsSupport.envInt("REFRESH_SECONDS_FAST", 5, 1);
    int REFRESH_MINUTES_SLOW = GpsSupport.envInt("REFRESH_MINUTES_SLOW", 15, 1);

    @Override
    public void run(String... args) {

        Thread thread1 = new Thread(this::eventLoopApi1);
        thread1.setName("API1_JTRACK-Worker");
        thread1.start();

        Thread thread2 = new Thread(this::eventLoopApi2);
        thread2.setName("API2_PAIZO-Worker");
        thread2.start();

        Thread thread3 = new Thread(this::eventLoopApi3);
        thread3.setName("API3_NMT-Worker");
        thread3.start();

        Thread thread4 = new Thread(this::eventLoopApiTata);
        thread4.setName("API4_Tata-Worker");
        thread4.start();

        Thread thread5 = new Thread(this::eventLoopGJTravels);
        thread5.setName("API5_GJ_TRAVELS-Worker");
        thread5.start();
    }

    private void eventLoopApiTata() {
        log.info("API 4 Worker Started...");
        Backoff backoff = new Backoff(ONE_MINUTE_API_FAILURE_BASE_MS, FAILURE_MAX_MS);
        while (running) {
            FetchResult result = safely("API 4", dataService::fetchAndPublishApiTata);
            handleSleepApiOneMinute(result, "API 4", backoff);
        }
    }

    private void eventLoopGJTravels() {
        log.info("API GJ TRAVELS Worker Started...");
        Backoff backoff = new Backoff(ONE_MINUTE_API_FAILURE_BASE_MS, FAILURE_MAX_MS);
        while (running) {
            FetchResult result = safely("API GJ", dataService::fetchAndPublishApi3GJTravels);
            handleSleep(result, "API GJ", backoff);
        }
    }

    private void eventLoopApi1() {
        log.info("API 1 Worker Started...");
        Backoff backoff = new Backoff(FAILURE_BASE_MS, FAILURE_MAX_MS);
        while (running) {
            FetchResult result = safely("API 1", dataService::fetchAndPublishApi1);
            handleSleep(result, "API 1", backoff);
        }
    }

    private void eventLoopApi2() {
        log.info("API 2 Worker Started...");
        Backoff backoff = new Backoff(FAILURE_BASE_MS, FAILURE_MAX_MS);
        while (running) {
            FetchResult result = safely("API 2", dataService::fetchAndPublishApi2);
            handleSleep(result, "API 2", backoff);
        }
    }

    private void eventLoopApi3() {
        log.info("API 3 Worker Started...");
        Backoff backoff = new Backoff(FAILURE_BASE_MS, FAILURE_MAX_MS);
        while (running) {
            FetchResult result = safely("API 3", dataService::fetchAndPublishApiNMT);
            handleSleep(result, "API 3", backoff);
        }
    }

    /**
     * Last line of defence: whatever a poll throws, the worker logs it and carries on.
     * Without this a single unexpected exception would end the thread and that provider would go stale until a redeploy.
     */
    private FetchResult safely(String workerName, Supplier<FetchResult> poll) {
        try {
            return poll.get();
        } catch (VirtualMachineError e) {
            throw e; // OutOfMemoryError etc: not something to retry around
        } catch (Throwable t) {
            log.error("{} worker iteration crashed unexpectedly. Will retry.", workerName, t);
            return FetchResult.failure();
        }
    }

    private void handleSleepApiOneMinute(FetchResult result, String workerName, Backoff backoff) {
        long sleepMillis;

        if (result.isSuccess()) {
            backoff.reset();
            sleepMillis = calculateSleepDurationApiOneMinute();
        } else {
            sleepMillis = backoff.nextDelay(result.retryAfterMillis());
            log.warn("{} 1 minute API failure #{}. Retrying in {}s (rate limit is 1m 5s).",
                    workerName, backoff.failures(), sleepMillis / 1000);
        }

        sleep(sleepMillis);
    }

    private long calculateSleepDurationApiOneMinute() {
        LocalTime now = LocalTime.now(TimestampNormalizer.IST);
        // Strict 65 seconds ( in ms) buffer for the 1m 5s rate limit
        long peakSleep = 65000L;
        long offPeakSleep = (long) REFRESH_MINUTES_SLOW * 60 * 1000;

        boolean isMorningPeak = !now.isBefore(MORNING_START) && now.isBefore(MORNING_END);
        boolean isEveningPeak = !now.isBefore(EVENING_START) && now.isBefore(EVENING_END);

        if (isMorningPeak || isEveningPeak) {
            return peakSleep;
        }

        // Off-Peak Logic (Same as before)
        long millisUntilMorningStart = now.until(MORNING_START, ChronoUnit.MILLIS);
        long millisUntilEveningStart = now.until(EVENING_START, ChronoUnit.MILLIS);

        if (millisUntilMorningStart < 0) millisUntilMorningStart += Duration.ofDays(1).toMillis();
        if (millisUntilEveningStart < 0) millisUntilEveningStart += Duration.ofDays(1).toMillis();

        long nextPeakStart = Math.min(millisUntilMorningStart, millisUntilEveningStart);

        if (nextPeakStart < offPeakSleep && nextPeakStart > 0) {
            return nextPeakStart;
        }

        return offPeakSleep;
    }

    private void handleSleep(FetchResult result, String workerName, Backoff backoff) {
        long sleepMillis;

        if (result.isSuccess()) {
            backoff.reset();
            sleepMillis = calculateSleepDuration();
        } else {
            sleepMillis = backoff.nextDelay(result.retryAfterMillis());
            log.warn("{} failure #{}. Retrying in {}s.", workerName, backoff.failures(), sleepMillis / 1000);
        }

        sleep(sleepMillis);
    }

    private void sleep(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private long calculateSleepDuration() {
        LocalTime now = LocalTime.now(TimestampNormalizer.IST);
        long peakSleep = REFRESH_SECONDS_FAST * 1000L;
        long offPeakSleep = (long) REFRESH_MINUTES_SLOW * 60 * 1000;
        long jitter = ThreadLocalRandom.current().nextLong(0, 3000);

        boolean isMorningPeak = !now.isBefore(MORNING_START) && now.isBefore(MORNING_END);
        boolean isEveningPeak = !now.isBefore(EVENING_START) && now.isBefore(EVENING_END);

        if (isMorningPeak || isEveningPeak) {
            return peakSleep+jitter;
        }

        // Off-Peak Logic: check if sleeping full duration will miss the start of a Peak.
        long millisUntilMorningStart = now.until(MORNING_START, ChronoUnit.MILLIS);
        long millisUntilEveningStart = now.until(EVENING_START, ChronoUnit.MILLIS);

        // Adjust for "tomorrow" if now is late night
        if (millisUntilMorningStart < 0) millisUntilMorningStart += Duration.ofDays(1).toMillis();
        if (millisUntilEveningStart < 0) millisUntilEveningStart += Duration.ofDays(1).toMillis();

        long nextPeakStart = Math.min(millisUntilMorningStart, millisUntilEveningStart);

        // If next peak starts in LESS than 15 minutes, sleep exactly until then.
        if (nextPeakStart < offPeakSleep && nextPeakStart > 0) {

            return nextPeakStart;
        }

        return offPeakSleep;
    }

    /**
     * Exponential backoff for one worker: base, 2x base, 4x base ... capped at max, plus 0-20% jitter.
     * The jitter is only ever added, so the base delay (the provider's rate limit) is a hard floor.
     * A Retry-After from the provider wins if it is longer than the computed delay.
     */
    static final class Backoff {
        private static final long MAX_RETRY_AFTER_MS = 60 * 60_000;

        private final long baseMs;
        private final long maxMs;
        private int failures;

        Backoff(long baseMs, long maxMs) {
            this.baseMs = baseMs;
            this.maxMs = maxMs;
        }

        void reset() {
            failures = 0;
        }

        int failures() {
            return failures;
        }

        long nextDelay(long retryAfterMs) {
            failures = Math.min(failures + 1, 30);
            long exponential = Math.min(maxMs, baseMs << Math.min(failures - 1, 20));
            long delay = Math.max(exponential, Math.min(retryAfterMs, MAX_RETRY_AFTER_MS));
            return delay + ThreadLocalRandom.current().nextLong(0, delay / 5 + 1);
        }
    }
}
