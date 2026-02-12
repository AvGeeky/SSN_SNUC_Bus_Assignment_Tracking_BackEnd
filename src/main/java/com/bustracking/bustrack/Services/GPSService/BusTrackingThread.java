package com.bustracking.bustrack.Services.GPSService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BusTrackingThread implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BusTrackingThread.class);

    @Autowired
    private BusDataService dataService;

    private volatile boolean running = true;

    // Schedule Config
    private static final LocalTime MORNING_START = LocalTime.of(5, 30);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(12, 30);
    private static final LocalTime EVENING_END = LocalTime.of(19, 30);

    // Safety check for Env vars to avoid startup crash if missing
    int REFRESH_SECONDS_FAST = System.getenv("REFRESH_SECONDS_FAST") != null ?
            Integer.parseInt(System.getenv("REFRESH_SECONDS_FAST")) : 5;
    int REFRESH_MINUTES_SLOW = System.getenv("REFRESH_MINUTES_SLOW") != null ?
            Integer.parseInt(System.getenv("REFRESH_MINUTES_SLOW")) : 15;

    @Override
    public void run(String... args) {

        Thread thread1 = new Thread(this::eventLoopApi1);
        thread1.setName("API1-Worker");
        thread1.start();

        Thread thread2 = new Thread(this::eventLoopApi2);
        thread2.setName("API2-Worker");
        thread2.start();
    }

    private void eventLoopApi1() {
        log.info("API 1 Worker Started...");
        while (running) {
            BusDataService.FetchStatus status = dataService.fetchAndPublishApi1();
            handleSleep(status, "API 1");
        }
    }

    private void eventLoopApi2() {
        log.info("API 2 Worker Started...");
        while (running) {
            BusDataService.FetchStatus status = dataService.fetchAndPublishApi2();
            handleSleep(status, "API 2");
        }
    }

    private void handleSleep(BusDataService.FetchStatus status, String workerName) {
        long sleepMillis;

        if (status == BusDataService.FetchStatus.SUCCESS) {
            sleepMillis = calculateSleepDuration();
        } else {
            log.warn(workerName + " failure. Retrying in 10s.");
            sleepMillis = 10000;
        }

        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private long calculateSleepDuration() {
        LocalTime now = LocalTime.now();
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
}