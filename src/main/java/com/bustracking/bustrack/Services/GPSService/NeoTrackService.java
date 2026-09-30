package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.dto.BusLocationDTO;
import com.bustracking.bustrack.util.RegNoNormalizer;
import com.bustracking.bustrack.util.TimestampNormalizer;
import com.bustracking.bustrack.util.TimestampNormalizer.Stamp;
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
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

import static com.bustracking.bustrack.Services.GPSService.GpsSupport.*;

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

    private String apiUrl;
    private String apiToken;
    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
    private static final String SOURCE = "NEOTRACKPurple";
    private static final int BATCH_SIZE = 1;

    // The API is asked for the points between "from" and "to". "from" is the end of the last successful call for that bus
    // (minus a small overlap), so nothing is missed however long the sleep or an outage was.
    private static final long MAX_LOOKBACK_MS = 23L * 60 * 60 * 1000;
    private static final long MIN_WINDOW_MS = 40_000;
    private static final long WINDOW_OVERLAP_MS = 10_000;

    // Schedule Config
    private static final LocalTime MORNING_START = LocalTime.of(5, 30);
    private static final LocalTime MORNING_END = LocalTime.of(9, 0);
    private static final LocalTime EVENING_START = LocalTime.of(12, 30);
    private static final LocalTime EVENING_END = LocalTime.of(19, 30);

    // --- Components ---
    private final RestTemplate restTemplate = GpsSupport.newRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // regNo -> "to" of the last call that reached the provider and returned a readable answer
    private final Map<String, Long> lastFetchedTo = new ConcurrentHashMap<>();

    int REFRESH_SECONDS_FAST = GpsSupport.envInt("REFRESH_SECONDS_FAST", 10, 1); // Default 10s
    int REFRESH_MINUTES_SLOW = GpsSupport.envInt("REFRESH_MINUTES_SLOW", 15, 1); // Default 15m

    private volatile boolean running = true;

    @PostConstruct
    public void startWorkers() {
        this.apiUrl = System.getenv("URL3");
        this.apiToken = System.getenv("URL3_TOKEN");

        List<String> missing = new ArrayList<>();
        if (apiUrl == null || apiUrl.isBlank()) missing.add("URL3");
        if (apiToken == null || apiToken.isBlank()) missing.add("URL3_TOKEN");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("FATAL: missing required env vars: " + String.join(", ", missing));
        }

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
        executorService.shutdownNow(); // interrupts workers that are sleeping
    }

    private void runWorkerLoop(int threadId, List<String> myBuses) {
        String threadName = "Worker-" + threadId;
        log.info("{} started. Managing: {}", threadName, myBuses);
        while (running) {
            long loopStart = System.currentTimeMillis();

            try {
                pollOnce(threadName, myBuses);
            } catch (Exception e) {
                // Never let one bad iteration end the worker
                log.error("{} iteration failed: {}", threadName, safeMessage(e));
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

    private void pollOnce(String threadName, List<String> myBuses) {
        Map<String, String> updates = new HashMap<>();

        for (String regNo : myBuses) {
            BusLocationDTO busData = fetchBusData(regNo, threadName);
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
    }

    private BusLocationDTO fetchBusData(String regNo, String threadName) {
        try {
            long now = System.currentTimeMillis();
            long startTime = windowStart(regNo, now);

            if (now - startTime > 5 * 60_000) {
                log.info("{} - Fetching {} from the last {} minutes.", threadName, regNo, (now - startTime) / 60_000);
            }

            Map<String, String> body = new HashMap<>();
            body.put("regNo", regNo);
            body.put("token", apiToken);
            body.put("to", String.valueOf(now));
            body.put("from", String.valueOf(startTime));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getBody() == null) return null;

            BusLocationDTO bus = parseBestLocation(response.getBody(), regNo);

            // The provider answered and the answer was readable (even if it held no points): everything up to "now" is covered.
            lastFetchedTo.put(regNo, now);
            return bus;

        } catch (ResourceAccessException e) {
            log.warn("{} - Server Unavailable / Network Error for {}: {}", threadName, regNo, safeMessage(e));
            return null;
        } catch (HttpServerErrorException e) {
            log.warn("{} - Server returned error {} for {}", threadName, e.getStatusCode(), regNo);
            return null;
        } catch (Exception e) {
            log.error("{} - Unexpected error fetching {}: {}", threadName, regNo, safeMessage(e));
            return null;
        }
    }

    /**
     * Start of the time window to request for a bus.
     * Normally the end of the previous successful call. After a restart, the time of the bus's last known point in Redis.
     * Never shorter than MIN_WINDOW_MS and never longer than MAX_LOOKBACK_MS.
     */
    private long windowStart(String regNo, long now) {
        long oldest = now - MAX_LOOKBACK_MS;

        Long since = lastFetchedTo.get(regNo);
        if (since == null) {
            since = lastKnownPointFromRedis(regNo);
        }
        if (since == null) {
            return oldest;
        }
        long from = Math.min(since - WINDOW_OVERLAP_MS, now - MIN_WINDOW_MS);
        return Math.max(from, oldest);
    }

    private Long lastKnownPointFromRedis(String regNo) {
        try {
            Object raw = redisTemplate.opsForHash().get(REDIS_HASH_KEY, RegNoNormalizer.normalize(regNo));
            if (raw == null) {
                return null;
            }
            BusLocationDTO existing = objectMapper.readValue(raw.toString(), BusLocationDTO.class);
            if (!SOURCE.equals(existing.getSource())) {
                return null; // last point came from another provider: no idea what NeoTrack has
            }
            if (existing.getEpochMs() > 0) {
                return existing.getEpochMs();
            }
            return TimestampNormalizer.parse(existing.getTimestamp()).map(Stamp::epochMs).orElse(null);
        } catch (Exception e) {
            return null; // fall back to the full lookback
        }
    }

    /** Newest usable point in the response, or null if the response holds none. Throws if the response is unreadable. */
    private BusLocationDTO parseBestLocation(String json, String regNo) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode dataArray = root == null ? null : root.get("data");

        if (dataArray == null || !dataArray.isArray()) {
            throw new IllegalStateException("response has no 'data' array");
        }
        if (dataArray.isEmpty()) {
            return null; // Empty array means no update
        }

        // Find latest timestamp in the array, skipping malformed points
        JsonNode bestNode = null;
        long maxTime = -1;
        int skipped = 0;

        for (JsonNode node : dataArray) {
            try {
                long nodeTime = requiredLong(node, "time");
                checkFix(requiredDouble(node, "latitude"), requiredDouble(node, "longitude"));
                if (nodeTime > maxTime) {
                    maxTime = nodeTime;
                    bestNode = node;
                }
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }

        if (bestNode == null) {
            throw new IllegalStateException("none of the " + dataArray.size() + " points for " + regNo + " were usable");
        }
        if (skipped > 0) {
            log.debug("Skipped {} malformed points for {}", skipped, regNo);
        }

        Stamp stamp = TimestampNormalizer.of(maxTime);

        return BusLocationDTO.builder()
                .regNo(RegNoNormalizer.normalize(regNo))
                .latitude(requiredDouble(bestNode, "latitude"))
                .longitude(requiredDouble(bestNode, "longitude"))
                .speed(optionalDouble(bestNode, "speed", 0.0))
                .timestamp(stamp.text())
                .epochMs(stamp.epochMs())
                .source(SOURCE)
                .build();
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
        LocalTime now = LocalTime.now(TimestampNormalizer.IST);
        long peakSleep = REFRESH_SECONDS_FAST * 1000L;         // 10 seconds
        long offPeakSleep = (long) REFRESH_MINUTES_SLOW * 60 * 1000; // 15 minutes

        long jitter = ThreadLocalRandom.current().nextLong(0, 3000);

        boolean isMorningPeak = !now.isBefore(MORNING_START) && now.isBefore(MORNING_END);
        boolean isEveningPeak = !now.isBefore(EVENING_START) && now.isBefore(EVENING_END);

        if (isMorningPeak || isEveningPeak) {
            return peakSleep+jitter;
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
