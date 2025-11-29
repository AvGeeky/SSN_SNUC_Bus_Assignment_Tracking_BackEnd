package com.bustracking.bustrack.Controller;

import com.bustracking.bustrack.Auth.JwtUtil;
import com.bustracking.bustrack.Services.BusService;
import com.bustracking.bustrack.Services.ProfileService;
import com.bustracking.bustrack.Services.RiderService;
import com.bustracking.bustrack.Services.StopService;
import com.bustracking.bustrack.dto.BusRouteStopDTO;
import com.bustracking.bustrack.dto.ProfileRequest;
import com.bustracking.bustrack.dto.ProfileResponse;
import com.bustracking.bustrack.dto.UserStopFinderDTO;
import com.bustracking.bustrack.entities.Bus;
import com.bustracking.bustrack.entities.Profile;
import com.bustracking.bustrack.entities.Rider;
import com.bustracking.bustrack.entities.Stop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class UserController {


    private final RiderService riderService;
    private final JwtUtil jwtUtil;

    @Autowired
     public UserController(RiderService riderService, JwtUtil jwtUtil){
        this.riderService = riderService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/user/findUserRouteById")
    public ResponseEntity<Map<String,Object>> findUserRouteById(@RequestHeader(value = "Authorization", required = false)String authHeader){
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "E");
            errorResponse.put("message", "Invalid Token Format");
            return ResponseEntity.status(401).body(errorResponse);
        }

        // Extract the actual token
        String jwt = authHeader.substring(7);

        String userEmail = jwtUtil.extractEmail(jwt);

       // System.out.println("user email from token: " + userEmail);
         //UUID riderId=UUID.fromString(requestBody.get("id").toString());
        UUID riderId=UUID.fromString(jwtUtil.extractRiderId(jwt));
         List<UserStopFinderDTO> data = riderService.findUserStop(riderId);

         List<BusRouteStopDTO> stops = riderService.findFullRouteForRider(riderId);
         Map<String,Object> response=new HashMap<>();
         if(stops!=null && data.stream().allMatch(dto -> userEmail.equals(dto.getRiderEmail()))){
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
          return ResponseEntity.status(503).body(response);

         }
    }



}
