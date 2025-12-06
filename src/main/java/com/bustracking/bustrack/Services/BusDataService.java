package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.DBConfig.Databasetest;
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

    @PostConstruct
    private void initialiseEnvs() {

        this.url1 = System.getenv("URL1");
        this.url2 = System.getenv("URL2");

        if (this.url1 == null) {
            throw new RuntimeException("FATAL: Env var URL1 is missing!");
        }
    }


    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";

    public void fetchAndPublish() {
        Map<String, String> batchData = new HashMap<>();

        try {
            String response1 = restTemplate.getForObject(url1, String.class);
            parseApi1(response1, batchData);
        } catch (Exception e) {
            log.error("API 1 Failed: " + e.getMessage());
        }

        try {
            String response2 = restTemplate.getForObject(url2, String.class);
            parseApi2(response2, batchData);
        } catch (Exception e) {
            log.error("API 2 Failed: " + e.getMessage());
        }

        if (!batchData.isEmpty()) {
            redisTemplate.opsForHash().putAll(REDIS_HASH_KEY, batchData);
            redisTemplate.expire(REDIS_HASH_KEY, Duration.ofMinutes(20));
            log.debug("Updated " + batchData.size() + " buses in Redis.");
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

                    String rawReg = node.get("RegNo").asText();
                    String normalizedReg = rawReg.replace(" ", "");

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
        }
        else{
            redisTemplate.opsForValue().set("ADMIN_TOGGLE","FALSE");
            return true;
        }

    }
    public boolean getAdminGlobalSwitch(){
        String val =  redisTemplate.opsForValue().get("ADMIN_TOGGLE");
        if (val == null) {
            return false;
        }
        return "YES".equals(val);
    }
}