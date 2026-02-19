package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.dto.BusLocationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Service
public class BusDataService {
    private static final Logger log = LoggerFactory.getLogger(BusDataService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");


    private String url1;
    private String url2;
    private String urlNMTLogin;
    private String urlNMTTrack;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";

    public enum FetchStatus {
        SUCCESS,
        FAILURE
    }

    @PostConstruct
    private void initialiseEnvs() {
        this.url1 = System.getenv("URL1");
        this.url2 = System.getenv("URL2");
        this.urlNMTLogin = System.getenv("URLNMT_LOGIN");
        this.urlNMTTrack = System.getenv("URLNMT_TRACK");

        if (this.url1 == null || this.url2 == null || this.urlNMTTrack ==null || this.urlNMTLogin ==null) {
            throw new RuntimeException("FATAL: Env var URL is missing!");
        }
    }

    public FetchStatus fetchAndPublishApi1() {
        Map<String, String> batchData = new HashMap<>();
        try {
            String response = restTemplate.getForObject(url1, String.class);
            parseApi1(response, batchData);

            if (!batchData.isEmpty()) {
                updateRedis(batchData);
                log.debug("API 1 Success: Updated " + batchData.size() + " buses.");
                return FetchStatus.SUCCESS;
            } else {
                log.warn("API 1 Empty Data");
                return FetchStatus.FAILURE;
            }
        } catch (Exception e) {
            log.error("API 1 Failed: " + e.getMessage());
            return FetchStatus.FAILURE;
        }
    }

    public FetchStatus fetchAndPublishApi2() {
        Map<String, String> batchData = new HashMap<>();
        try {
            String response = restTemplate.getForObject(url2, String.class);
            parseApi2(response, batchData);

            if (!batchData.isEmpty()) {
                updateRedis(batchData);
                log.debug("API 2 Success: Updated " + batchData.size() + " buses.");
                return FetchStatus.SUCCESS;
            } else {
                log.warn("API 2 Empty Data");
                return FetchStatus.FAILURE;
            }
        } catch (Exception e) {
            log.error("API 2 Failed: " + e.getMessage());
            return FetchStatus.FAILURE;
        }
    }


    public boolean storeAuthTokenForNMT() {
        String loginUrl = urlNMTLogin;

        try {
            // 1. Execute POST request.

            String response = restTemplate.postForObject(loginUrl, null, String.class);

            // 2. Parse the nested JSON structure
            JsonNode root = objectMapper.readTree(response);

            if (root.path("success").asBoolean()) {
                String token = root.path("data").path("token").asText();
                log.info("NMT scheduled Login Successful. Token retrieved.");
                addNMTAuthKeyToRedis(token);
                return true;
            } else {
                log.error("Login failed based on API response: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during login request: {}", e.getMessage());
            return false;
        }
    }

    private String getOrRefreshToken() {
        String token = getNMTAuthKeyFromRedis();

        // Lazy-load: if token is null, try to login right now
        if (token == null) {
            log.info("NMT Token expired/missing in Redis. Attempting login...");
            boolean success = storeAuthTokenForNMT();
            if (success) {
                return getNMTAuthKeyFromRedis();
            }
        }
        return token;
    }

    public FetchStatus fetchAndPublishApiNMT() {
        String bearerToken = getOrRefreshToken();
        if (bearerToken == null){
            return FetchStatus.FAILURE;
        }
        Map<String, String> batchData = new HashMap<>();
        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    urlNMTTrack,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            String response = responseEntity.getBody();

            parseApiNMT(response, batchData);

            if (!batchData.isEmpty()) {
                updateRedis(batchData);
                log.debug("API NMT Success: Updated " + batchData.size() + " buses.");
                return FetchStatus.SUCCESS;
            } else {
                log.warn("API NMT Empty Data");
                return FetchStatus.FAILURE;
            }

        }catch (HttpClientErrorException.Unauthorized e) {
            log.error("NMT Token expired/unauthorized. Evicting from Redis.");
            redisTemplate.delete("NMT_TOKEN");
            return FetchStatus.FAILURE;
        }catch (Exception e) {
            log.error("API NMT Failed: " + e.getMessage());
            return FetchStatus.FAILURE;
        }
    }

    private void updateRedis(Map<String, String> data) {
        redisTemplate.opsForHash().putAll(REDIS_HASH_KEY, data);
        redisTemplate.expire(REDIS_HASH_KEY, Duration.ofDays(1));
    }

    private void addNMTAuthKeyToRedis(String key) {
        redisTemplate.opsForValue().set("NMT_TOKEN", key);
        redisTemplate.expire("NMT_TOKEN", Duration.ofDays(7));
    }
    private String getNMTAuthKeyFromRedis(){
        return redisTemplate.opsForValue().get("NMT_TOKEN");
    }




    private void parseApiNMT(String json, Map<String, String> batch) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");

            if (dataArray != null && dataArray.isArray()) {
                List<JsonNode> apiNodes = new ArrayList<>();
                List<String> regNos = new ArrayList<>();

                for (JsonNode node : dataArray) {
                    apiNodes.add(node);
                    regNos.add(node.get("vehicle_name").asText().replace(" ", ""));
                }

                List<Object> cachedData = redisTemplate.opsForHash().multiGet(REDIS_HASH_KEY, new ArrayList<>(regNos));

                for (int i = 0; i < apiNodes.size(); i++) {
                    JsonNode node = apiNodes.get(i);
                    String regNo = regNos.get(i);
                    double newLat = node.get("latitude").asDouble();
                    double newLng = node.get("longitude").asDouble();

                    String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
                    Object cachedJson = cachedData.get(i);

                    //if the cached bus data is not existing like in the first call it puts the current timestamp data
                    if (cachedJson != null) {
                        BusLocationDTO existingBus = objectMapper.readValue(cachedJson.toString(), BusLocationDTO.class);

                        // Update timestamp ONLY if lat/long changed
                        if (existingBus.getLatitude() == newLat && existingBus.getLongitude() == newLng) {
                            timestamp = existingBus.getTimestamp();
                        }
                    }

                    BusLocationDTO bus = BusLocationDTO.builder()
                            .regNo(regNo)
                            .latitude(newLat)
                            .longitude(newLng)
                            .speed(node.get("speed").asDouble())
                            .ignition(node.hasNonNull("acc") ? node.get("acc").asText() : null)
                            .timestamp(timestamp)
                            .source("API_NMT")
                            .build();

                    batch.put(regNo, objectMapper.writeValueAsString(bus));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse and compare NMT API response", e);
        }
    }


    private void parseApi1(String json, Map<String, String> batch) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    BusLocationDTO bus = BusLocationDTO.builder()
                            .regNo(node.get("vehicle_number").asText().replace(" ", ""))
                            .latitude(node.get("lat_message").asDouble())
                            .longitude(node.get("lon_message").asDouble())
                            .speed(node.get("speed").asDouble())
                            .timestamp(node.get("gps_datetime").asText())
                            .source("API_1")
                            .build();
                    batch.put(bus.getRegNo(), objectMapper.writeValueAsString(bus));
                }
            }
        } catch (Exception e) { log.error("Failed to parse API 1 response", e); }
    }

    private void parseApi2(String json, Map<String, String> batch) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataArray = root.get("data");
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    BusLocationDTO bus = BusLocationDTO.builder()
                            .regNo(node.get("RegNo").asText().replace(" ", ""))
                            .latitude(node.get("Lat").asDouble())
                            .longitude(node.get("Lng").asDouble())
                            .speed(node.get("Speed").asDouble())
                            .timestamp(node.get("Time").asText())
                            .odometer(node.get("Odometer").asText())
                            .ignition(node.get("Ignition").asText())
                            .source("API_2")
                            .build();
                    batch.put(bus.getRegNo(), objectMapper.writeValueAsString(bus));
                }
            }
        } catch (Exception e) { log.error("Failed to parse API 2 response", e); }
    }

    public boolean setAdminGlobalSwitch(boolean truth){
        if (truth) {
            redisTemplate.opsForValue().set("ADMIN_TOGGLE","YES");
            return true;
        } else {
            redisTemplate.opsForValue().set("ADMIN_TOGGLE","FALSE");
            return true;
        }
    }

    public boolean getAdminGlobalSwitch(){
        String val =  redisTemplate.opsForValue().get("ADMIN_TOGGLE");
        return "YES".equals(val);
    }
}