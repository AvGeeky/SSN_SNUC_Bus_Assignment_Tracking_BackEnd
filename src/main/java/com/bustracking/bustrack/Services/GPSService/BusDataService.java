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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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
    private String urlApiTATALogin;
    private String urlApiTATATrack;
    private String APITATA_CLIENTID;
    private String APITATA_CLIENTSECRET;
    private String APITATA_GRANTTYPE;

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
        this.urlApiTATALogin = System.getenv("URLAPITATA_LOGIN");
        this.urlApiTATATrack = System.getenv("URLAPITATA_TRACK");
        this.APITATA_CLIENTID = System.getenv("APITATA_CLIENTID");
        this.APITATA_CLIENTSECRET = System.getenv("APITATA_CLIENTSECRET");
        this.APITATA_GRANTTYPE = System.getenv("APITATA_GRANTTYPE");

        if (this.url1 == null || this.url2 == null || this.urlNMTTrack ==null || this.urlNMTLogin ==null || this.urlApiTATALogin == null || this.urlApiTATATrack == null || this.APITATA_CLIENTID == null || this.APITATA_CLIENTSECRET == null || this.APITATA_GRANTTYPE == null) {
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



    public boolean storeAuthTokenForApiTata() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", APITATA_CLIENTID);
            map.add("client_secret",APITATA_CLIENTSECRET);
            map.add("grant_type", APITATA_GRANTTYPE);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            String response = restTemplate.postForObject(urlApiTATALogin, request, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("access_token")) {
                String token = root.get("access_token").asText();

                // Save with 3500 minute TTL
                redisTemplate.opsForValue().set("API4_TOKEN", token);
                redisTemplate.expire("API4_TOKEN", Duration.ofMinutes(3500));

                log.info("API 4 Login Successful. Token saved with 3500m TTL.");
                return true;
            } else {
                log.error("API 4 Login failed: {}", response);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during API 4 login: {}", e.getMessage());
            return false;
        }
    }
    private String getOrRefreshApiTataToken() {
        String token = redisTemplate.opsForValue().get("API4_TOKEN");
        if (token == null) {
            log.info("API 4 Token expired/missing (TTL out). Attempting lazy login...");
            if (storeAuthTokenForApiTata()) {
                return redisTemplate.opsForValue().get("API4_TOKEN");
            }
        }
        return token;
    }
    public FetchStatus fetchAndPublishApiTata() {
        String bearerToken = getOrRefreshApiTataToken();
        if (bearerToken == null) return FetchStatus.FAILURE;

        Map<String, String> batchData = new HashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(bearerToken);
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    urlApiTATATrack, HttpMethod.GET, requestEntity, String.class
            );

            parseApiTata(responseEntity.getBody(), batchData);

            if (!batchData.isEmpty()) {
                updateRedis(batchData);
                log.debug("API 4 Success: Updated " + batchData.size() + " buses.");
                return FetchStatus.SUCCESS;
            } else {
                return FetchStatus.FAILURE;
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("API 4 Token unauthorized early. Evicting from Redis.");
            redisTemplate.delete("API4_TOKEN");
            return FetchStatus.FAILURE;
        } catch (Exception e) {
            log.error("API 4 General Failure: " + e.getMessage());
            return FetchStatus.FAILURE;
        }
    }

    private void parseApiTata(String json, Map<String, String> batch) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode dataArray = root.get("vehicles");

            if (dataArray != null && dataArray.isArray()) {
                List<JsonNode> apiNodes = new ArrayList<>();
                List<String> regNos = new ArrayList<>();

                for (JsonNode node : dataArray) {

                    if (node.has("registrationNumber")) {
                        apiNodes.add(node);
                        regNos.add(node.get("registrationNumber").asText().replace(" ", ""));
                    }
                }

                if (regNos.isEmpty()) return;

                List<Object> cachedData = redisTemplate.opsForHash().multiGet(REDIS_HASH_KEY, new ArrayList<>(regNos));

                for (int i = 0; i < apiNodes.size(); i++) {
                    JsonNode node = apiNodes.get(i);
                    String regNo = regNos.get(i);

                    double newLat = node.get("gpsLatitude").asDouble();
                    double newLng = node.get("gpsLongitude").asDouble();

                    String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
                    Object cachedJson = cachedData.get(i);

                    if (cachedJson != null) {
                        BusLocationDTO existingBus = objectMapper.readValue(cachedJson.toString(), BusLocationDTO.class);

                        if (existingBus.getLatitude() == newLat && existingBus.getLongitude() == newLng) {
                            timestamp = existingBus.getTimestamp();
                        }
                    }

                    BusLocationDTO bus = BusLocationDTO.builder()
                            .regNo(regNo)
                            .latitude(newLat)
                            .longitude(newLng)
                            .speed(node.get("speed").asDouble())

                            .odometer(node.hasNonNull("odometer") ? node.get("odometer").asText() : null)

                            .ignition(node.path("ignitionOn").asBoolean() ? "ON" : "OFF")
                            .timestamp(timestamp)
                            .source("API_TATA")
                            .build();

                    batch.put(regNo, objectMapper.writeValueAsString(bus));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Tata API response", e);
        }

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