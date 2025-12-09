package com.bustracking.bustrack.Controller;

import com.bustracking.bustrack.Auth.JwtUtil;
import com.bustracking.bustrack.Services.*;
import com.bustracking.bustrack.Services.GPSService.BusDataService;
import com.bustracking.bustrack.dto.BusRouteStopDTO;
import com.bustracking.bustrack.dto.UserStopFinderDTO;
import com.bustracking.bustrack.entities.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class UserController {


    private final RiderService riderService;
    private final JwtUtil jwtUtil;
    private final BusDataService busDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
    private final VehicleRnoService vehicleRnoService;

    @Autowired
     public UserController(RiderService riderService, JwtUtil jwtUtil, BusDataService busDataService, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, VehicleRnoService vehicleRnoService){
        this.riderService = riderService;
        this.jwtUtil = jwtUtil;
        this.busDataService = busDataService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.vehicleRnoService = vehicleRnoService;
    }

    @GetMapping("/user/buses")
    public ResponseEntity<Map<String, Object>> buses(@RequestHeader(value = "Authorization", required = false)String authHeader){
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "E");
            errorResponse.put("message", "Invalid Token Format");
            return ResponseEntity.status(401).body(errorResponse);
        }

        Map<String, Object> response = new HashMap<>();
        try{
        if (busDataService.getAdminGlobalSwitch()) {
                Map<Object, Object> rawData = redisTemplate.opsForHash().entries(REDIS_HASH_KEY);
                Map<String, Object> cleanData = new HashMap<>();
                for (Map.Entry<Object, Object> entry : rawData.entrySet()) {
                    String key = (String) entry.getKey();
                    String jsonString = (String) entry.getValue();
                    cleanData.put(key, objectMapper.readTree(jsonString));
                }
                response.put("status", "success");
                response.put("message", "All live buses retrieved");
                response.put("data", cleanData);
                return ResponseEntity.ok(response);
        }
        else {
            String jwt = authHeader.substring(7);
            //String userEmail = jwtUtil.extractEmail(jwt);
            UUID riderId=UUID.fromString(jwtUtil.extractRiderId(jwt));
            List<UserStopFinderDTO> data = riderService.findUserStop(riderId,false);
            List<String> busPlateNumbers = data.stream()
                    .map(UserStopFinderDTO::getBusPlateNumber)
                    .distinct()
                    .toList();
            Map<String, Object> busesData = new HashMap<>();
            for (String busNo : busPlateNumbers) {
                String normalizedKey = busNo.replace(" ", "");
                Object rawJson = redisTemplate.opsForHash().get(REDIS_HASH_KEY, normalizedKey);
                if (rawJson != null) {
                    busesData.put(busNo, objectMapper.readTree(rawJson.toString()));
                } else {
                    busesData.put(busNo, null);
                }
            }
            if (busesData.isEmpty()) {
                response.put("status", "error");
                response.put("message", "No buses found or currently offline");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            } else {
                response.put("status", "success");
                response.put("data", busesData);
                return ResponseEntity.ok(response);
            }
        }


        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Internal Server Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/user/findUserRouteById")
    public ResponseEntity<Map<String,Object>> findUserRouteById(@RequestHeader(value = "Authorization", required = false)String authHeader){
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "E");
            errorResponse.put("message", "Invalid Token Format");
            return ResponseEntity.status(401).body(errorResponse);
        }

        String jwt = authHeader.substring(7);

        //String userEmail = jwtUtil.extractEmail(jwt);

        UUID riderId=UUID.fromString(jwtUtil.extractRiderId(jwt));
        List<UserStopFinderDTO> data = riderService.findUserStop(riderId,true);

         List<BusRouteStopDTO> stops = riderService.findFullRouteForRider(riderId);
         //Calling it here only to cache it.
        //riderService.findUserStop(riderId,true);

         Map<String,Object> response=new HashMap<>();
         if(stops!=null){
             response.put("status","S");
             response.put("data",data);
             response.put("busStops",stops);
             response.put("studentsInBus",riderService.studentsInUsersBus(riderId));
             response.put("message","User stop details retrieved successfully");
             return ResponseEntity.ok(response);
         }
         else{
          response.put("status","E");
          response.put("message","User stop details not found / Unauthorized access");
          return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

         }
    }

    @GetMapping("/user/getAllRnoVehicleMapping")
    public ResponseEntity<Map<String,Object>> getrnoVehicleMapping(){
        List<Vehicle_rno_mapping> mappings=vehicleRnoService.getAll();

        Map<String,Object> response=new HashMap<>();
        if(mappings!=null){
            response.put("status","S");
            response.put("result",mappings);
            response.put("message","Mapping retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","Mappings not retrieved successfully");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

    }



}
