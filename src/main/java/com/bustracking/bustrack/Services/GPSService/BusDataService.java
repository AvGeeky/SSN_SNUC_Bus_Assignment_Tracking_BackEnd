package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.dto.BusLocationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class BusDataService {
    private static final Logger log = LoggerFactory.getLogger(BusDataService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String url1;
    private String url2;

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

        if (this.url1 == null || this.url2 == null) {
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

    private void updateRedis(Map<String, String> data) {
        redisTemplate.opsForHash().putAll(REDIS_HASH_KEY, data);
        redisTemplate.expire(REDIS_HASH_KEY, Duration.ofDays(1));
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