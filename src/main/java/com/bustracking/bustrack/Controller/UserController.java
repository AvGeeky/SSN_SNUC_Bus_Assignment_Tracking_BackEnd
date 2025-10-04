package com.bustracking.bustrack.Controller;

import com.bustracking.bustrack.Auth.JwtUtil;
import com.bustracking.bustrack.Services.BusService;
import com.bustracking.bustrack.Services.ProfileService;
import com.bustracking.bustrack.Services.RiderService;
import com.bustracking.bustrack.Services.StopService;
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
    public ResponseEntity<Map<String,Object>> findUserRouteById(@RequestBody Map<String,Object> requestBody,@CookieValue("jwt") String jwt){
        String userEmail = jwtUtil.extractEmail(jwt);
       // System.out.println("user email from token: " + userEmail);
         List<UserStopFinderDTO> stops = riderService.findUserStop(UUID.fromString(requestBody.get("id").toString()));
         Map<String,Object> response=new HashMap<>();
         if(stops!=null && stops.stream().allMatch(dto -> userEmail.equals(dto.getRiderEmail()))){
             response.put("status","S");
             response.put("data",stops);
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
