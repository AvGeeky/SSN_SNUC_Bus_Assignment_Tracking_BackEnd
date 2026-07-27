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
import com.bustracking.bustrack.entities.User_sessions;

import java.time.LocalTime;
import java.util.*;

@RestController
public class UserController {


    private final RiderService riderService;
    private final JwtUtil jwtUtil;
    private final BusDataService busDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
    private final VehicleRnoService vehicleRnoService;
    private final UserSessionsService sessionService;
    @Autowired
     public UserController(RiderService riderService, JwtUtil jwtUtil, BusDataService busDataService, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, VehicleRnoService vehicleRnoService, UserSessionsService sessionService){
        this.riderService = riderService;
        this.jwtUtil = jwtUtil;
        this.busDataService = busDataService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.vehicleRnoService = vehicleRnoService;

        this.sessionService = sessionService;
    }

    @GetMapping("/user/findUserRouteById")
    public ResponseEntity<Map<String,Object>> findUserRouteById(@RequestHeader(value = "Authorization", required = false) String authHeader){

        String jwt = authHeader.substring(7);
        String type = jwtUtil.extractType(jwt);

        Map<String,Object> response = new HashMap<>();

        if (type.equalsIgnoreCase("guest")){
            response.put("message","Guest does not have a scheduled bus. They can view any bus.");
            return ResponseEntity.ok(response);
        }

        UUID riderId = UUID.fromString(jwtUtil.extractRiderId(jwt));

        try {

            String today = java.time.LocalDate.now().toString();

            Boolean isExamDay = redisTemplate.opsForSet().isMember("exam:dates", today);

            if (Boolean.TRUE.equals(isExamDay)) {
                String note = redisTemplate.opsForValue().get("note:exam:" + today);
                if (note != null) response.put("note_e", note);
            }

            List<UserStopFinderDTO> data = riderService.findUserStop(riderId, true);
            List<BusRouteStopDTO> stops = riderService.findFullRouteForRider(riderId);


            if(data != null && !data.isEmpty()){

                String assignedBus = data.get(0).getBusPlateNumber().replace(" ","");

                String overrideKey = "bus:alternate:" + today + ":" + assignedBus;

                Set<String> alternates = redisTemplate.opsForSet().members(overrideKey);
                  /*
                CHECK FOR ALTERNATE BUS NOTE
                */
                if(alternates != null && !alternates.isEmpty()){
                    String note = redisTemplate.opsForValue().get("note:alternate:" + today);
                    if(note != null) response.put("note_a", note);
                }
            }

            if (stops != null) {

                response.put("status","S");
                response.put("data",data);
                response.put("busStops",stops);
                response.put("studentsInBus",riderService.studentsInUsersBus(riderId));
                response.put("message","User stop details retrieved successfully");

                return ResponseEntity.ok(response);

            } else {

                response.put("status","E");
                response.put("message","User stop details not found / Unauthorized access");

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

        } catch (Exception e) {

            response.put("status","E");
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/user/buses")
    public ResponseEntity<Map<String, Object>> buses(@RequestHeader(value = "Authorization", required = false) String authHeader) {

        String jwt = authHeader.substring(7);
        String type = jwtUtil.extractType(jwt);

        Map<String, Object> response = new HashMap<>();

        try {

            String today = java.time.LocalDate.now().toString();
            Boolean isExamDay = redisTemplate.opsForSet().isMember("exam:dates", today);
            boolean isExamDayAndTime = false;
            if (Boolean.TRUE.equals(isExamDay)){
                LocalTime now = LocalTime.now();
                LocalTime start = LocalTime.of(9, 0);
                LocalTime end = LocalTime.of(16, 30);

                isExamDayAndTime = !now.isBefore(start) && !now.isAfter(end);
            }


            // GLOBAL VIEW CONDITIONS
            if (busDataService.getAdminGlobalSwitch() || type.equalsIgnoreCase("guest") || isExamDayAndTime) {

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

            // USER RESTRICTED VIEW
            UUID riderId = UUID.fromString(jwtUtil.extractRiderId(jwt));

            List<UserStopFinderDTO> data = riderService.findUserStop(riderId, false);
            if (data == null || data.isEmpty()) {
                response.put("status", "error");
                response.put("message", "No buses assigned to this user");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            List<String> userBuses = data.stream()
                    .map(UserStopFinderDTO::getBusPlateNumber)
                    .distinct()
                    .toList();


            Set<String> busesToFetch = new HashSet<>(userBuses);

            // CHECK FOR ALTERNATE BUS OVERRIDES
            for (String bus : userBuses) {

                String normalizedBus = bus.replace(" ", "");
                String overrideKey = "bus:alternate:" + today + ":" + normalizedBus;

                Set<String> alternates = redisTemplate.opsForSet().members(overrideKey);

                if (alternates != null && !alternates.isEmpty()) {

                    busesToFetch.addAll(alternates);

                }
            }


            Map<String, Object> busesData = new HashMap<>();

            for (String busNo : busesToFetch) {

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

        } catch (Exception e) {

            response.put("status", "error");
            response.put("message", "Internal Server Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
    @PostMapping("/user/generateAccessCode")
    public ResponseEntity<Map<String, Object>> generateAccessCode(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        String jwt = authHeader.substring(7);

        String type = jwtUtil.extractType(jwt);

        if ("guest".equalsIgnoreCase(type)) { // if its not there for user default will be the type
            response.put("status", "E");
            response.put("message", "Not allowed for guest");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            String userEmail = jwtUtil.extractEmail(jwt);
            String code = String.valueOf((int) (Math.random() * 900000) + 100000);

            User_sessions session = User_sessions.builder()
                    .username(userEmail)
                    .password(code)
                    .loginType("parent")
                    .build();

            boolean done = sessionService.create_session(session);

            if (done) {
                response.put("status", "S");
                response.put("message", "created the code successfully");
                response.put("code", code);
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "E");
                response.put("message", "Could not create session. It might already exist.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (Exception e) {
            response.put("status", "E");
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/user/getAcessCode")
    public ResponseEntity<Map<String,Object>> getAcessCode(@RequestHeader("Authorization") String authHeader){


        String jwt = authHeader.substring(7);
        String type = jwtUtil.extractType(jwt);

        Map<String,Object> response = new HashMap<>();

        if (type.equalsIgnoreCase("guest")){
            response.put("message","Guest Can Not Have Access Code");
            return ResponseEntity.ok(response);
        }

        String userEmail = jwtUtil.extractEmail(jwt);

        User_sessions data=sessionService.getsessionbyusername(userEmail);

        if(data!=null){
             response.put("status","S");
             response.put("Code",data.getPassword());
             response.put("message","data retrieved successfully");
             return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","data not retrieved successfully");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }



}
