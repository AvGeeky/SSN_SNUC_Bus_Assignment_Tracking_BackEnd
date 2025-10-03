package com.bustracking.bustrack.Controller;
import com.bustracking.bustrack.dto.ProfileRequest;
import com.bustracking.bustrack.entities.Rider;
import com.bustracking.bustrack.entities.Stop;
import com.bustracking.bustrack.Services.StopService;
import com.bustracking.bustrack.entities.Bus;
import com.bustracking.bustrack.Services.BusService;
import com.bustracking.bustrack.Services.RiderService;
import com.bustracking.bustrack.Services.ProfileService;
import com.bustracking.bustrack.entities.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
@RestController
public class AdminController {
     private final  StopService StopService;
     private final BusService BusService;
    private final RiderService RiderService;
    private final ProfileService ProfileService;
     @Autowired
     public AdminController(StopService StopService, BusService busService, RiderService riderService, ProfileService profileService){
         this.StopService=StopService;
         this.BusService = busService;
         this.RiderService = riderService;
         ProfileService = profileService;
     }
     @PostMapping("/admin/insertStops")
     public ResponseEntity<Map<String,Object>> createStop(@RequestBody Map<String,Object> requestBody){
         Stop stop=Stop.builder()
                 .name((String) requestBody.get("name"))
                 .lat(requestBody.get("lat") == null ? null : Double.valueOf((String)requestBody.get("lat")))
                 .lng(requestBody.get("lng") == null ? null : Double.valueOf((String)requestBody.get("lng")))
                 .build();
         Boolean done=StopService.create_stop(stop);
         Map<String, Object> response = new HashMap<>();
         if(done) {

             response.put("status", "S");
             response.put("message", "stop record inserted successfully");
             response.put("data", stop);
             return ResponseEntity.ok(response);
         }
         else{
             response.put("status", "E");
             response.put("message", "stop record not inserted successfully");
             return ResponseEntity.status(503).body(response);
         }
     }
     @PostMapping("/admin/deleteStops")
     public ResponseEntity<Map<String,Object>> deleteStop(@RequestBody Map<String,Object> requestBody){
            Boolean done=StopService.delete_stop(UUID.fromString(requestBody.get("id").toString()));
            Map<String,Object> response=new HashMap<>();
            if(done){
                response.put("status","S");
                response.put("message"," stop deleted successfully");
                return ResponseEntity.ok(response);
            }
            else{
                response.put("status","E");
                response.put("message","stop not deleted successfully");
                return ResponseEntity.status(503).body(response);
            }
     }
     @GetMapping("/admin/getAllStops")
     public ResponseEntity<Map<String,Object>> getAllStops(){
         List<Stop> stops=StopService.getAll();
         Map<String,Object> response=new HashMap<>();
        if(stops!=null) {
            response.put("status", "S");
            response.put("data", stops);
            response.put("message","stops retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
           response.put("status","E");
           response.put("message","stops not retrieved successfully");
           return ResponseEntity.status(503).body(response);
        }
     }
    @GetMapping("/admin/getStopById")
    public ResponseEntity<Map<String,Object>> getStopById(@RequestBody Map<String,Object> requestBody){
        Stop stop=StopService.getById(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(stop!=null){
            response.put("status","S");
            response.put("data",stop);
            response.put("message","stop details retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","stop details not retrieved successfully");
            return ResponseEntity.status(503).body(response);

        }
    }
     @PostMapping("/admin/insertBus")
     public ResponseEntity<Map<String,Object>> createBus(@RequestBody Map<String,Object> requestBody){
         Bus bus=Bus.builder()
                 .capacity(Integer.valueOf(requestBody.get("capacity").toString()))
                 .busNumber((String)requestBody.get("bus_number"))
                 .brand((String) requestBody.get("brand"))
                 .createdAt(Instant.now())
                 .build();
         Boolean done=BusService.create_bus(bus);
         Map<String,Object> response=new HashMap<>();
         if(done) {
             response.put("status", "S");
             response.put("message", "bus record inserted successfully");
             response.put("data", bus);
             return ResponseEntity.ok(response);
         }
         else{
             response.put("status", "E");
             response.put("message", "bus record not inserted successfully");
             return ResponseEntity.status(503).body(response);
         }

     }
    @GetMapping("/admin/getAllBus")
    public ResponseEntity<Map<String,Object>> getAllBuses(){
        List<Bus> buses=BusService.getAll();
        Map<String, Object> response = new HashMap<>();
        if(buses!=null) {

            response.put("status", "S");
            response.put("message","records retrieved successfully");
            response.put("data", buses);
            return ResponseEntity.ok(response);
        }
        else{
          response.put("status","E");
          response.put("message","records not retrieved successfully");
          return ResponseEntity.status(503).body(response);
        }
    }
    @GetMapping("/admin/getBusById")
    public ResponseEntity<Map<String,Object>> getBusById(@RequestBody Map<String,Object> requestBody){
         Bus bus=BusService.getById(UUID.fromString(requestBody.get("id").toString()));
         Map<String,Object> response=new HashMap<>();
         if(bus!=null){
             response.put("status","S");
             response.put("data",bus);
             response.put("message","bus details retrieved successfully");
             return ResponseEntity.ok(response);
         }
         else{
          response.put("status","E");
          response.put("message","bus details not retrieved successfully");
          return ResponseEntity.status(503).body(response);

         }
    }
    @PostMapping("/admin/deleteBus")
    public ResponseEntity<Map<String,Object>> deleteBus(@RequestBody Map<String,Object> requestBody){
        Boolean done=BusService.delete_bus(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message"," bus deleted successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","bus not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @PostMapping("/admin/updateBus")
    public ResponseEntity<Map<String,Object>> updateBus(@RequestBody Map<String,Object> requestBody){
        Bus bus=Bus.builder()
                .id(UUID.fromString(requestBody.get("id").toString()))
                .capacity(Integer.valueOf(requestBody.get("capacity").toString()))
                .busNumber((String)requestBody.get("bus_number"))
                .brand((String) requestBody.get("brand"))
                .createdAt(Instant.now())
                .build();
        Boolean done=BusService.update_bus_capacity(bus);
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","bus details updated successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","bus details not updated successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @PostMapping("/admin/insertRider")
    public ResponseEntity<Map<String,Object>> createRider(@RequestBody Map<String,Object> requestBody){
        Rider rider=Rider.builder()
                .name((String)requestBody.get("name"))
                .year(Integer.valueOf(requestBody.get("year").toString()))
                .department((String) requestBody.get("department"))
                .college((String)requestBody.get("college"))
                .email((String)requestBody.get("email"))
                .homeStopId(UUID.fromString(requestBody.get("home_stop_id").toString()))
                .digitalId((String)requestBody.get("digital_id"))
                .createdAt(Instant.now())
                .build();
       Boolean done=RiderService.create_rider(rider);
        Map<String,Object> response=new HashMap<>();
        if(done) {
            response.put("status", "S");
            response.put("message", "rider record inserted successfully");
            response.put("data", rider);
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status", "E");
            response.put("message", "rider record not inserted successfully");
            return ResponseEntity.status(503).body(response);
        }

    }
    @GetMapping("/admin/getAllRiders")
    public ResponseEntity<Map<String,Object>> getAllRiders(){
        List<Rider> riders=RiderService.getAll();
        Map<String, Object> response = new HashMap<>();
        if(riders!=null) {

            response.put("status", "S");
            response.put("message","records retrieved successfully");
            response.put("data", riders);
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","records not retrieved successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @GetMapping("/admin/getRiderById")
    public ResponseEntity<Map<String,Object>> getRiderById(@RequestBody Map<String,Object> requestBody){
        Rider rider=RiderService.getById(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(rider!=null){
            response.put("status","S");
            response.put("data",rider);
            response.put("message","rider details retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","rider details not retrieved successfully");
            return ResponseEntity.status(503).body(response);

        }
    }
    @PostMapping("/admin/deleteRider")
    public ResponseEntity<Map<String,Object>> deleteRider(@RequestBody Map<String,Object> requestBody){
        Boolean done=RiderService.delete_rider(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message"," rider deleted successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","rider not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @PostMapping("/admin/updateRider")
    public ResponseEntity<Map<String,Object>> updateRider(@RequestBody Map<String,Object> requestBody){
        Rider rider=Rider.builder()
                .id(UUID.fromString(requestBody.get("id").toString()))
                .name((String)requestBody.get("name"))
                .year(Integer.valueOf(requestBody.get("year").toString()))
                .department((String) requestBody.get("department"))
                .college((String)requestBody.get("college"))
                .email((String)requestBody.get("email"))
                .homeStopId(UUID.fromString(requestBody.get("home_stop_id").toString()))
                .createdAt(Instant.now())
                .build();
        Boolean done=RiderService.update_rider(rider);
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","rider details updated successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","rider details not updated successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @PostMapping("/admin/insertProfile")
    public ResponseEntity<Map<String,Object>> createProfile(@RequestBody ProfileRequest dto){
        Map<String,Object> resp = new HashMap<>();
        try {
            ProfileService.create_full_profile(dto);
            resp.put("status","S");
            resp.put("message","profile created");
            return ResponseEntity.ok(resp);
        }
        catch (Exception e) {
            resp.put("status","E"); resp.put("message",e);
            return ResponseEntity.status(500).body(resp);
        }
        }



    @GetMapping("/admin/getAllProfiles")
    public ResponseEntity<Map<String,Object>> getAllProfiles(){
        List<Profile> profiles=ProfileService.getAll();
        Map<String, Object> response = new HashMap<>();
        if(profiles!=null) {

            response.put("status", "S");
            response.put("message","records retrieved successfully");
            response.put("data", profiles);
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","records not retrieved successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @GetMapping("/admin/getProfileById")
    public ResponseEntity<Map<String,Object>> getProfileById(@RequestBody Map<String,Object> requestBody){
        Map<String,Object> result=ProfileService.getById(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(result!=null){
            response.put("status","S");
            response.put("data",result);
            response.put("message","profile details retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","profile details not retrieved successfully");
            return ResponseEntity.status(503).body(response);

        }
    }
    @PostMapping("/admin/deleteProfile")
    public ResponseEntity<Map<String,Object>> deleteProfile(@RequestBody Map<String,Object> requestBody){
        Boolean done=ProfileService.delete_profile(UUID.fromString(requestBody.get("id").toString()));
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message"," Profile deleted successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","Profile not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }

    @PostMapping("/admin/updateProfile")
    public ResponseEntity<Map<String,Object>> updateProfile(@RequestBody Map<String,Object> requestBody){
        Profile profile=Profile.builder()
                .name((String)requestBody.get("name"))
                .id(UUID.fromString(requestBody.get("id").toString()))
                .status((String)requestBody.get("status"))
                .createdAt(Instant.now())
                .build();
        Boolean done=ProfileService.update_Profile_Status(profile);
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","profile details updated successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","profile details not updated successfully");
            return ResponseEntity.status(503).body(response);
        }
    }





}
