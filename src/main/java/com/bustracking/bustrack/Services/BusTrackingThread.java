package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.Services.BusDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
public class BusTrackingThread implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BusTrackingThread.class);

    @Autowired
    private BusDataService dataService;

    private volatile boolean running = true;

    // Schedule Config
    private static final LocalTime MORNING_START = LocalTime.of(5, 30);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(13, 0);
    private static final LocalTime EVENING_END = LocalTime.of(19, 30);

    @Override
    public void run(String... args) {
        Thread worker = new Thread(this::eventLoop);
        worker.setName("Bus-Tracking-Worker");
        worker.start();
    }

    private void eventLoop() {
        log.info("Bus Tracking Thread Started...");
        while (running) {

            dataService.fetchAndPublish();

            long sleepMillis = calculateSleepDuration();
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private long calculateSleepDuration() {
        LocalTime now = LocalTime.now();
        long peakSleep = 10 * 1000;         // 10 seconds
        long offPeakSleep = 15 * 60 * 1000; // 15 minutes

        boolean isMorningPeak = !now.isBefore(MORNING_START) && now.isBefore(MORNING_END);
        boolean isEveningPeak = !now.isBefore(EVENING_START) && now.isBefore(EVENING_END);

        if (isMorningPeak || isEveningPeak) {
            return peakSleep;
        }

        // If we are in Off-Peak, check if sleeping 15 mins will make us miss the start of a Peak.

        long millisUntilMorningStart = now.until(MORNING_START, ChronoUnit.MILLIS);
        long millisUntilEveningStart = now.until(EVENING_START, ChronoUnit.MILLIS);

        // Adjust for "tomorrow" if now is late night (e.g. 11 PM)
        if (millisUntilMorningStart < 0) millisUntilMorningStart += Duration.ofDays(1).toMillis();
        if (millisUntilEveningStart < 0) millisUntilEveningStart += Duration.ofDays(1).toMillis();

        long nextPeakStart = Math.min(millisUntilMorningStart, millisUntilEveningStart);

        // If the next peak starts in LESS than 15 minutes, sleep exactly until then.
        // Otherwise, sleep 15 minutes.
        if (nextPeakStart < offPeakSleep && nextPeakStart > 0) {
            log.info("Approaching Peak Window. Adjusting sleep to " + (nextPeakStart/1000) + "s");
            return nextPeakStart;
        }

        return offPeakSleep;
    }
}