package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.dto.BusLocationDTO;
import com.bustracking.bustrack.util.TimestampNormalizer;
import com.bustracking.bustrack.util.TimestampNormalizer.Stamp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static com.bustracking.bustrack.Services.GPSService.GpsSupport.*;

@Service
public class BusDataService {
    private static final Logger log = LoggerFactory.getLogger(BusDataService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String url1;
    private String url2;
    private String urlNMTLogin;
    private String urlNMTTrack;
    private String urlApiTATALogin;
    private String urlApiTATATrack;
    private String APITATA_CLIENTID;
    private String APITATA_CLIENTSECRET;
    private String APITATA_GRANTTYPE;
    private String URL3_GJ;

    private final RestTemplate restTemplate = GpsSupport.newRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
    private static final String NMT_TOKEN_KEY = "NMT_TOKEN";
    private static final String TATA_TOKEN_KEY = "API4_TOKEN";

    // Repeating warnings (bad records, unparseable timestamps) are logged in full at most once per interval per key
    private static final long WARN_INTERVAL_MS = 60_000;
    private final Map<String, Long> lastWarn = new ConcurrentHashMap<>();

    public enum FetchStatus {
        SUCCESS,
        FAILURE
    }

    /**
     * Outcome of one poll. A poll that worked but returned no vehicles is still a SUCCESS.
     * retryAfterMillis is only set when the provider told us when to come back (Retry-After on 429/503).
     */
    public record FetchResult(FetchStatus status, long retryAfterMillis) {
        public static FetchResult success() {
            return new FetchResult(FetchStatus.SUCCESS, 0);
        }

        public static FetchResult failure() {
            return new FetchResult(FetchStatus.FAILURE, 0);
        }

        public static FetchResult failure(long retryAfterMillis) {
            return new FetchResult(FetchStatus.FAILURE, retryAfterMillis);
        }

        public boolean isSuccess() {
            return status == FetchStatus.SUCCESS;
        }
    }

    /** What the parsers found in one response. */
    static final class ParseStats {
        int total;      // records in the response
        int ignored;    // records deliberately not published (e.g. tracker without a registration number)
        int skipped;    // records that were malformed
        String firstError;
    }

    record ParseResult(List<BusLocationDTO> buses, ParseStats stats) {
    }

    @FunctionalInterface
    private interface RecordMapper {
        /** Returns the bus, or null to ignore the record. Throws if the record is malformed. */
        BusLocationDTO map(JsonNode node) throws Exception;
    }

    @PostConstruct
    private void initialiseEnvs() {
        this.url1 = System.getenv("URL1");
        this.url2 = System.getenv("URL2");
        this.URL3_GJ = System.getenv("URL3_GJ");
        this.urlNMTLogin = System.getenv("URLNMT_LOGIN");
        this.urlNMTTrack = System.getenv("URLNMT_TRACK");
        this.urlApiTATALogin = System.getenv("URLAPITATA_LOGIN");
        this.urlApiTATATrack = System.getenv("URLAPITATA_TRACK");
        this.APITATA_CLIENTID = System.getenv("APITATA_CLIENTID");
        this.APITATA_CLIENTSECRET = System.getenv("APITATA_CLIENTSECRET");
        this.APITATA_GRANTTYPE = System.getenv("APITATA_GRANTTYPE");

        List<String> missing = new ArrayList<>();
        requireEnv(missing, "URL1", url1);
        requireEnv(missing, "URL2", url2);
        requireEnv(missing, "URL3_GJ", URL3_GJ);
        requireEnv(missing, "URLNMT_LOGIN", urlNMTLogin);
        requireEnv(missing, "URLNMT_TRACK", urlNMTTrack);
        requireEnv(missing, "URLAPITATA_LOGIN", urlApiTATALogin);
        requireEnv(missing, "URLAPITATA_TRACK", urlApiTATATrack);
        requireEnv(missing, "APITATA_CLIENTID", APITATA_CLIENTID);
        requireEnv(missing, "APITATA_CLIENTSECRET", APITATA_CLIENTSECRET);
        requireEnv(missing, "APITATA_GRANTTYPE", APITATA_GRANTTYPE);

        if (!missing.isEmpty()) {
            throw new IllegalStateException("FATAL: missing required env vars: " + String.join(", ", missing));
        }
    }

    private static void requireEnv(List<String> missing, String name, String value) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    // Fetch + publish, one method per provider. The worker threads call these.

    public FetchResult fetchAndPublishApi1() {
        return runFetch("API 1", null, () -> parseApi1(restTemplate.getForObject(url1, String.class)));
    }

    public FetchResult fetchAndPublishApi2() {
        return runFetch("API 2", null, () -> parseApi2(restTemplate.getForObject(url2, String.class)));
    }

    public FetchResult fetchAndPublishApi3GJTravels() {
        return runFetch("API 3 GJ", null, () -> parseApi3GJ(restTemplate.getForObject(URL3_GJ, String.class)));
    }

    public FetchResult fetchAndPublishApiNMT() {
        return runFetch("API NMT", NMT_TOKEN_KEY, () -> {
            String bearerToken = getOrRefreshToken();
            if (bearerToken == null) {
                throw new IllegalStateException("no NMT auth token available");
            }
            HttpEntity<Void> requestEntity = new HttpEntity<>(bearer(bearerToken));
            ResponseEntity<String> response = restTemplate.exchange(urlNMTTrack, HttpMethod.GET, requestEntity, String.class);
            return parseApiNMT(response.getBody());
        });
    }

    public FetchResult fetchAndPublishApiTata() {
        return runFetch("API 4", TATA_TOKEN_KEY, () -> {
            String bearerToken = getOrRefreshApiTataToken();
            if (bearerToken == null) {
                throw new IllegalStateException("no API 4 auth token available");
            }
            HttpEntity<Void> requestEntity = new HttpEntity<>(bearer(bearerToken));
            ResponseEntity<String> response = restTemplate.exchange(urlApiTATATrack, HttpMethod.GET, requestEntity, String.class);
            return parseApiTata(response.getBody());
        });
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Runs one poll. Everything that can go wrong (auth/token lookups in Redis, HTTP, parsing, the Redis write)
     * happens inside the try, so a worker thread always gets a FetchResult back and never an exception.
     *
     * @param tokenKeyToEvict Redis key of the auth token to drop when the provider answers 401, or null for token-less APIs
     */
    private FetchResult runFetch(String name, String tokenKeyToEvict, Callable<ParseResult> fetcher) {
        try {
            return publish(name, fetcher.call());
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 401 && tokenKeyToEvict != null) {
                log.error("{} token expired/unauthorized. Evicting from Redis.", name);
                evictQuietly(tokenKeyToEvict);
                return FetchResult.failure();
            }
            if (status == 429 || status == 503) {
                long retryAfterMs = retryAfterMillis(e.getResponseHeaders());
                log.warn("{} answered {} (Retry-After: {}s)", name, status, retryAfterMs / 1000);
                return FetchResult.failure(retryAfterMs);
            }
            log.error("{} Failed: {}", name, safeMessage(e));
            return FetchResult.failure();
        } catch (Exception e) {
            log.error("{} Failed: {}", name, safeMessage(e));
            return FetchResult.failure();
        }
    }

    private FetchResult publish(String name, ParseResult result) throws JsonProcessingException {
        ParseStats stats = result.stats();

        if (stats.skipped > 0) {
            warnThrottled(name + ":skipped", "{} skipped {} of {} records (first problem: {})",
                    name, stats.skipped, stats.total, stats.firstError);
        }

        if (result.buses().isEmpty()) {
            if (stats.skipped > 0) {
                // Every record was bad: the format has probably changed
                log.error("{} returned {} records and none were usable", name, stats.total);
                return FetchResult.failure();
            }
            // A valid response with no vehicles (buses off at night, etc.) is a normal outcome
            log.debug("{} returned no vehicles", name);
            return FetchResult.success();
        }

        Map<String, String> batch = new HashMap<>();
        for (BusLocationDTO bus : result.buses()) {
            batch.put(bus.getRegNo(), objectMapper.writeValueAsString(bus));
        }
        updateRedis(batch);
        log.debug("{} Success: Updated {} buses.", name, batch.size());
        return FetchResult.success();
    }

    private void updateRedis(Map<String, String> data) {
        redisTemplate.opsForHash().putAll(REDIS_HASH_KEY, data);
        redisTemplate.expire(REDIS_HASH_KEY, Duration.ofDays(1));
    }

    private void evictQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Could not evict {} from Redis: {}", key, safeMessage(e));
        }
    }

    /** Retry-After is either a number of seconds or an HTTP date. Returns 0 if absent or unreadable. */
    static long retryAfterMillis(HttpHeaders headers) {
        if (headers == null) {
            return 0;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value.trim())) * 1000L;
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                return Math.max(0, when.toInstant().toEpochMilli() - System.currentTimeMillis());
            } catch (DateTimeParseException notDate) {
                return 0;
            }
        }
    }

    private void warnThrottled(String key, String message, Object... args) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(key);
        if (last == null || now - last >= WARN_INTERVAL_MS) {
            lastWarn.put(key, now);
            log.warn(message, args);
        } else {
            log.debug(message, args);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Auth tokens
    // ------------------------------------------------------------------------------------------------------------

    public boolean storeAuthTokenForNMT() {
        try {
            String response = restTemplate.postForObject(urlNMTLogin, null, String.class);
            JsonNode root = objectMapper.readTree(response);

            String token = root.path("data").path("token").asText();
            if (root.path("success").asBoolean() && !token.isBlank()) {
                log.info("NMT scheduled Login Successful. Token retrieved.");
                redisTemplate.opsForValue().set(NMT_TOKEN_KEY, token, Duration.ofDays(7));
                return true;
            } else {
                log.error("Login failed based on API response: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during login request: {}", safeMessage(e));
            return false;
        }
    }

    private String getOrRefreshToken() {
        String token = redisTemplate.opsForValue().get(NMT_TOKEN_KEY);

        // Lazy-load: if token is null, try to login right now
        if (token == null) {
            log.info("NMT Token expired/missing in Redis. Attempting login...");
            if (storeAuthTokenForNMT()) {
                return redisTemplate.opsForValue().get(NMT_TOKEN_KEY);
            }
        }
        return token;
    }

    public boolean storeAuthTokenForApiTata() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", APITATA_CLIENTID);
            map.add("client_secret", APITATA_CLIENTSECRET);
            map.add("grant_type", APITATA_GRANTTYPE);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            String response = restTemplate.postForObject(urlApiTATALogin, request, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.hasNonNull("access_token")) {
                String token = root.get("access_token").asText();
                redisTemplate.opsForValue().set(TATA_TOKEN_KEY, token, Duration.ofMinutes(3500));
                log.info("API 4 Login Successful. Token saved with 3500m TTL.");
                return true;
            } else {
                log.error("API 4 Login failed: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during API 4 login: {}", safeMessage(e));
            return false;
        }
    }

    private String getOrRefreshApiTataToken() {
        String token = redisTemplate.opsForValue().get(TATA_TOKEN_KEY);
        if (token == null) {
            log.info("API 4 Token expired/missing (TTL out). Attempting lazy login...");
            if (storeAuthTokenForApiTata()) {
                return redisTemplate.opsForValue().get(TATA_TOKEN_KEY);
            }
        }
        return token;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Parsers: provider JSON -> BusLocationDTO. A bad record is skipped and counted, it never takes the batch down.
    // A response that is not JSON, or has no "data" array, throws and the poll counts as a failure.
    // ------------------------------------------------------------------------------------------------------------

    private ParseResult parseRecords(String json, String arrayField, RecordMapper mapper) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode array = root == null ? null : root.get(arrayField);
        if (array == null || !array.isArray()) {
            throw new IllegalStateException("response has no '" + arrayField + "' array");
        }

        ParseStats stats = new ParseStats();
        List<BusLocationDTO> buses = new ArrayList<>(array.size());
        for (JsonNode node : array) {
            stats.total++;
            try {
                BusLocationDTO bus = mapper.map(node);
                if (bus == null) {
                    stats.ignored++;
                } else {
                    buses.add(bus);
                }
            } catch (Exception e) {
                stats.skipped++;
                if (stats.firstError == null) {
                    stats.firstError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                }
            }
        }
        return new ParseResult(buses, stats);
    }

    /** Timestamp the provider sent, normalised. If it is unreadable the bus is still published, stamped with the fetch time. */
    private Stamp providerStamp(String source, String raw) {
        return TimestampNormalizer.parse(raw).orElseGet(() -> {
            warnThrottled(source + ":timestamp", "{} sent an unreadable timestamp '{}'. Using fetch time.", source, raw);
            return TimestampNormalizer.now();
        });
    }

    ParseResult parseApi1(String json) throws JsonProcessingException {
        return parseJtrack(json, "API_1");
    }

    ParseResult parseApi3GJ(String json) throws JsonProcessingException {
        return parseJtrack(json, "API_3GJ");
    }

    /** API 1 and API 3 GJ are both jtrack and share a format. */
    private ParseResult parseJtrack(String json, String source) throws JsonProcessingException {
        return parseRecords(json, "data", node -> {
            double lat = requiredDouble(node, "lat_message");
            double lng = requiredDouble(node, "lon_message");
            checkFix(lat, lng);
            Stamp stamp = providerStamp(source, optionalText(node, "gps_datetime"));
            return BusLocationDTO.builder()
                    .regNo(requiredRegNo(node, "vehicle_number"))
                    .latitude(lat)
                    .longitude(lng)
                    .speed(optionalDouble(node, "speed", 0.0))
                    .timestamp(stamp.text())
                    .epochMs(stamp.epochMs())
                    .source(source)
                    .build();
        });
    }

    ParseResult parseApi2(String json) throws JsonProcessingException {
        return parseRecords(json, "data", node -> {
            double lat = requiredDouble(node, "Lat");
            double lng = requiredDouble(node, "Lng");
            checkFix(lat, lng);
            Stamp stamp = providerStamp("API_2", optionalText(node, "Time"));
            return BusLocationDTO.builder()
                    .regNo(requiredRegNo(node, "RegNo"))
                    .latitude(lat)
                    .longitude(lng)
                    .speed(optionalDouble(node, "Speed", 0.0))
                    .timestamp(stamp.text())
                    .epochMs(stamp.epochMs())
                    .odometer(optionalText(node, "Odometer"))
                    .ignition(optionalText(node, "Ignition"))
                    .source("API_2")
                    .build();
        });
    }

    ParseResult parseApiNMT(String json) throws JsonProcessingException {
        ParseResult result = parseRecords(json, "data", node -> {
            double lat = requiredDouble(node, "latitude");
            double lng = requiredDouble(node, "longitude");
            checkFix(lat, lng);
            return BusLocationDTO.builder()
                    .regNo(requiredRegNo(node, "vehicle_name"))
                    .latitude(lat)
                    .longitude(lng)
                    .speed(optionalDouble(node, "speed", 0.0))
                    .ignition(optionalText(node, "acc"))
                    .source("API_NMT")
                    .build();
        });
        applyStableTimestamps(result.buses());
        return result;
    }

    ParseResult parseApiTata(String json) throws JsonProcessingException {
        ParseResult result = parseRecords(json, "vehicles", node -> {
            JsonNode reg = node.get("registrationNumber");
            if (reg == null || reg.isNull() || reg.asText().isBlank()) {
                return null; // tracker not assigned to a vehicle
            }
            double lat = requiredDouble(node, "gpsLatitude");
            double lng = requiredDouble(node, "gpsLongitude");
            checkFix(lat, lng);
            return BusLocationDTO.builder()
                    .regNo(requiredRegNo(node, "registrationNumber"))
                    .latitude(lat)
                    .longitude(lng)
                    .speed(optionalDouble(node, "speed", 0.0))
                    .odometer(optionalText(node, "odometer"))
                    .ignition(node.path("ignitionOn").asBoolean() ? "ON" : "OFF")
                    .source("API_TATA")
                    .build();
        });
        applyStableTimestamps(result.buses());
        return result;
    }

    /**
     * NMT and Tata do not send a GPS time, so the timestamp means "when we first saw the bus at this position".
     * If the position is unchanged since the last poll the previous timestamp is kept, otherwise it is now.
     */
    private void applyStableTimestamps(List<BusLocationDTO> buses) {
        if (buses.isEmpty()) {
            return;
        }
        List<Object> hashKeys = new ArrayList<>(buses.size());
        for (BusLocationDTO bus : buses) {
            hashKeys.add(bus.getRegNo());
        }
        List<Object> cached = redisTemplate.opsForHash().multiGet(REDIS_HASH_KEY, hashKeys);

        Stamp now = TimestampNormalizer.now();
        for (int i = 0; i < buses.size(); i++) {
            BusLocationDTO bus = buses.get(i);
            Stamp stamp = now;

            Object raw = (cached != null && i < cached.size()) ? cached.get(i) : null;
            if (raw != null) {
                try {
                    BusLocationDTO existing = objectMapper.readValue(raw.toString(), BusLocationDTO.class);
                    if (existing.getLatitude() == bus.getLatitude() && existing.getLongitude() == bus.getLongitude()) {
                        stamp = stampOf(existing, now);
                    }
                } catch (Exception ignored) {
                    // unreadable cached entry: treat the bus as new
                }
            }
            bus.setTimestamp(stamp.text());
            bus.setEpochMs(stamp.epochMs());
        }
    }

    /** Entries written before epochMs existed only have the text, which may be in the old dd-MM-yyyy format. */
    private static Stamp stampOf(BusLocationDTO existing, Stamp fallback) {
        if (existing.getEpochMs() > 0) {
            return TimestampNormalizer.of(existing.getEpochMs());
        }
        return TimestampNormalizer.parse(existing.getTimestamp()).orElse(fallback);
    }

    // ------------------------------------------------------------------------------------------------------------

    public boolean setAdminGlobalSwitch(boolean truth) {
        if (truth) {
            redisTemplate.opsForValue().set("ADMIN_TOGGLE", "YES");
            return true;
        } else {
            redisTemplate.opsForValue().set("ADMIN_TOGGLE", "FALSE");
            return true;
        }
    }

    public boolean getAdminGlobalSwitch() {
        String val = redisTemplate.opsForValue().get("ADMIN_TOGGLE");
        return "YES".equals(val);
    }
}
