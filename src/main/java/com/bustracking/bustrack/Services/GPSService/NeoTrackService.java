package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.dto.BusLocationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NeoTrackService {
    private static final Logger log = LoggerFactory.getLogger(NeoTrackService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final List<String> ALL_REG_NOS = Arrays.asList(
            "TN87H0937", "TN87H2371", "TN87H0920", "TN87H0922", "TN87H0954",
            "TN87H0923", "TN87H0995", "TN87H2246", "TN87H0931", "TN87H0991",
            "TN87H2354", "TN87H0963", "TN87H0986", "TN87H0960", "TN87H0934",
            "TN87H2270", "TN87H0933", "TN87H0961", "TN87H2314", "TN87H0982"
    );

    private static final String API_URL = System.getenv("URL3");
    private static final String API_TOKEN = System.getenv("URL3_TOKEN");
    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
    private static final int BATCH_SIZE = 5;


    // IST Formatter for timestamp conversion
    private static final DateTimeFormatter IST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Kolkata"));

    // Schedule Config
    private static final LocalTime MORNING_START = LocalTime.of(5, 30);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(12, 30);
    private static final LocalTime EVENING_END = LocalTime.of(19, 30);

    // --- Components ---
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private volatile boolean running = true;

    @PostConstruct
    public void startWorkers() {

        List<List<String>> partitions = partitionList(ALL_REG_NOS, BATCH_SIZE);

        log.info("Starting NeoTrack Service. Total Buses: {}, Batches: {}", ALL_REG_NOS.size(), partitions.size());

        for (int i = 0; i < partitions.size(); i++) {
            List<String> batch = partitions.get(i);
            int threadId = i + 1;
            executorService.submit(() -> runWorkerLoop(threadId, batch));
        }
    }

    @PreDestroy
    public void stopWorkers() {
        this.running = false;
        executorService.shutdown();
    }

    private void runWorkerLoop(int threadId, List<String> myBuses) {
        String threadName = "Worker-" + threadId;
        log.info("{} started. Managing: {}", threadName, myBuses);
        boolean isFirstRun = true;
        while (running) {
            long loopStart = System.currentTimeMillis();

            Map<String, String> updates = new HashMap<>();

            for (String regNo : myBuses) {
                BusLocationDTO busData = fetchBusData(regNo, threadName,isFirstRun);
                if (busData != null) {
                    try {
                        updates.put(busData.getRegNo(), objectMapper.writeValueAsString(busData));
                    } catch (Exception e) {
                        log.error("{} JSON error for {}: {}", threadName, regNo, e.getMessage());
                    }
                }
            }

            if (!updates.isEmpty()) {
                try {

                    redisTemplate.opsForHash().putAll(REDIS_HASH_KEY, updates);

                    redisTemplate.expire(REDIS_HASH_KEY, Duration.ofDays(1));

                    log.debug("{} updated {} buses.", threadName, updates.size());
                } catch (Exception e) {
                    log.error("{} Redis connection failed. Skipping update.", threadName);
                }
            } else {

                log.trace("{} obtained no data updates.", threadName);
            }
            if (isFirstRun) {
                isFirstRun = false;
            }
            long requiredSleep = calculateSleepDuration();
            long timeSpent = System.currentTimeMillis() - loopStart;
            long actualSleep = Math.max(1000, requiredSleep - timeSpent); // Ensure at least 1s sleep

            try {
                Thread.sleep(actualSleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private BusLocationDTO fetchBusData(String regNo, String threadName, boolean isFirstRun) {
        try {
            long now = System.currentTimeMillis();
            long startTime;

            //Logic to switch between 23 hours and 40 seconds
            if (isFirstRun) {

                startTime = now - (23L * 60 * 60 * 1000);
                log.info("{} - Initial fetch for {}: Looking back 23 hours.", threadName, regNo);
            } else {
                startTime = now - 40000;
            }

            Map<String, String> body = new HashMap<>();
            body.put("regNo", regNo);
            body.put("token", API_TOKEN);
            body.put("to", String.valueOf(now));

            // 3. Use the calculated startTime
            body.put("from", String.valueOf(startTime));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, entity, String.class);

            if (response.getBody() == null) return null;

            return parseBestLocation(response.getBody(), regNo);

        } catch (ResourceAccessException e) {
            log.warn("{} - Server Unavailable / Network Error for {}: {}", threadName, regNo, e.getMessage());
            return null;
        } catch (HttpServerErrorException e) {
            log.warn("{} - Server returned error {} for {}", threadName, e.getStatusCode(), regNo);
            return null;
        } catch (Exception e) {
            log.error("{} - Unexpected error fetching {}: {}", threadName, regNo, e.getMessage());
            return null;
        }
    }

    private BusLocationDTO parseBestLocation(String json, String regNo) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");

            if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
                return null; // Empty array means no update
            }

            // Find latest timestamp in the array
            JsonNode bestNode = null;
            long maxTime = -1;

            for (JsonNode node : dataArray) {
                long nodeTime = node.get("time").asLong();
                if (nodeTime > maxTime) {
                    maxTime = nodeTime;
                    bestNode = node;
                }
            }

            if (bestNode == null) return null;

            String istTime = IST_FORMATTER.format(Instant.ofEpochMilli(maxTime));

            return BusLocationDTO.builder()
                    .regNo(regNo)
                    .latitude(bestNode.get("latitude").asDouble())
                    .longitude(bestNode.get("longitude").asDouble())
                    .speed(bestNode.get("speed").asDouble())
                    .timestamp(istTime)
                    .source("NEOTRACKPurple")
                    .build();

        } catch (Exception e) {
            log.error("Parse Error for " + regNo, e);
            return null;
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(new ArrayList<>(
                    list.subList(i, Math.min(i + size, list.size()))
            ));
        }
        return partitions;
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

        // Logic to sleep until the next peak starts
        long millisUntilMorning = now.until(MORNING_START, ChronoUnit.MILLIS);
        long millisUntilEvening = now.until(EVENING_START, ChronoUnit.MILLIS);

        if (millisUntilMorning < 0) millisUntilMorning += Duration.ofDays(1).toMillis();
        if (millisUntilEvening < 0) millisUntilEvening += Duration.ofDays(1).toMillis();

        long nextPeakStart = Math.min(millisUntilMorning, millisUntilEvening);

        if (nextPeakStart < offPeakSleep && nextPeakStart > 0) {
            return nextPeakStart;
        }

        return offPeakSleep;
    }
}