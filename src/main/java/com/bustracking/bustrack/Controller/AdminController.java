package com.bustracking.bustrack.Controller;
import com.bustracking.bustrack.Services.*;
import com.bustracking.bustrack.dto.ProfileFullDto;
import com.bustracking.bustrack.dto.ProfileRequest;
import com.bustracking.bustrack.dto.ProfileResponse;
import com.bustracking.bustrack.entities.Rider;
import com.bustracking.bustrack.entities.Stop;
import com.bustracking.bustrack.entities.Bus;
import com.bustracking.bustrack.entities.Vehicle_rno_mapping;
import com.bustracking.bustrack.entities.Profile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
@RestController
public class AdminController {
     private final  StopService StopService;
     private final BusService BusService;
    private final RiderService RiderService;
    private final ProfileService ProfileService;
    private final VehicleRnoService VehicleRnoService;
    private final BusDataService busDataService;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private static final String REDIS_HASH_KEY = "LIVE_BUS_LOCATIONS";
     @Autowired
     public AdminController(ObjectMapper objectMapper, StopService StopService, BusService busService, RiderService riderService, ProfileService profileService, VehicleRnoService vehicleRnoService, StringRedisTemplate redisTemplate, BusDataService busDataService){
         this.StopService=StopService;
         this.BusService = busService;
         this.RiderService = riderService;
         ProfileService = profileService;
         VehicleRnoService = vehicleRnoService;
         this.redisTemplate = redisTemplate;
         this.objectMapper = objectMapper;
         this.busDataService = busDataService;
     }

    @GetMapping("/admin/buses")
    public ResponseEntity<Map<String, Object>> buses(@RequestParam(required = false) String busNo) {

        Map<String, Object> response = new HashMap<>();

        try {

            if (busNo == null || busNo.trim().isEmpty()) {
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
                String normalizedKey = busNo.replace(" ", "");
                Object rawJson = redisTemplate.opsForHash().get(REDIS_HASH_KEY, normalizedKey);
                if (rawJson != null) {
                    response.put("status", "success");
                    response.put("data", objectMapper.readTree(rawJson.toString()));
                    return ResponseEntity.ok(response);
                } else {
                    response.put("status", "error");
                    response.put("message", "Bus not found or currently offline: " + busNo);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Internal Server Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
    @PostMapping("/admin/updateGlobalViewToggle")
    public ResponseEntity<Map<String,Object>> updateGlobalViewToggle (@RequestBody Map<String,Object> requestBody) {
        Boolean toggle = Boolean.valueOf(requestBody.get("global_view_toggle").toString());
        Boolean done = busDataService.setAdminGlobalSwitch(toggle);
        Map<String, Object> response = new HashMap<>();
        if (done) {
            response.put("status", "S");
            response.put("message", " global view toggle updated successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "E");
            response.put("message", "global view toggle not updated");
            return ResponseEntity.status(503).body(response);
        }
    }
    @GetMapping("/admin/viewGlobalViewToggle")
    public ResponseEntity<Map<String,Object>> viewGlobalViewToggle () {
        Boolean toggle = busDataService.getAdminGlobalSwitch();
        Map<String, Object> response = new HashMap<>();

            response.put("status", "S");
            response.put("message", " global view toggle fetched successfully");
            response.put("toggle",toggle);
            return ResponseEntity.ok(response);
    }
     @DeleteMapping("/admin/deleteStops")
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
    @PostMapping("/admin/getStopById")
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
    @PostMapping("/admin/getBusById")
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
    @DeleteMapping("/admin/deleteBus")
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
    @PostMapping("/admin/getRiderById")
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
    @DeleteMapping("/admin/deleteRider")
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
                .digitalId(requestBody.get("digital_id").toString())
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
    @PostMapping("/admin/insertProfileFromExcel")
    public ResponseEntity<Map<String, Object>> createProfileFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("profileName") String profileName,
            @RequestParam("profileStatus") String profileStatus) {

        Map<String, Object> resp = new HashMap<>();
        try {
            // Call the new service method on the injected bean
            ProfileService.create_full_profile_from_excel(file, profileName, profileStatus);

            resp.put("status", "S");
            resp.put("message", "Profile created successfully from Excel file.");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("status", "E");
            // Add the error message for debugging
            resp.put("message", "Failed to process Excel file: " + e.getMessage());
            e.printStackTrace(); // Prints the full error to your server console
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
    @PostMapping("/admin/getProfileById")
    public ResponseEntity<Map<String,Object>> getFullProfileFById(@RequestBody Map<String,Object> body){
         String id= (String) body.get("id");
        ProfileResponse result = ProfileService.getFullProfileById(UUID.fromString(id));
        //System.out.println(result);
        Map<String,Object> response = new HashMap<>();

        if(result != null){
            response.put("status","S");
            response.put("data",result);
            response.put("message","Full profile details retrieved successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile details not found or failed to retrieve");
            return ResponseEntity.status(503).body(response);
        }
    }
    @DeleteMapping("/admin/deleteProfile")
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

    @PostMapping("/admin/toggleProfile")
    public ResponseEntity<Map<String,Object>> toggleProfile(@RequestBody Map<String,Object> requestBody){
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
//EDIT PROFILE ENDPONITS (INS UPD)
    @PostMapping("/admin/createProfileBus")
    public ResponseEntity<Map<String,Object>> createProfileBus(@RequestBody Map<String,Object> requestBody){
        UUID profile_id = UUID.fromString(requestBody.get("profile_id").toString());
        UUID bus_id = UUID.fromString(requestBody.get("bus_id").toString());
        String busNumber=requestBody.get("bus_number").toString();
        UUID id = ProfileService.createProfileBus(profile_id,bus_id,busNumber);
        Map<String,Object> response=new HashMap<>();
        if(id != null){
            response.put("status","S");
            response.put("message"," Bus in Profile inserted successfully");
            response.put("profileBusId",id);
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","Bus not inserted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }

    @PostMapping("/admin/createProfileStop")
    public ResponseEntity<Map<String,Object>> createProfileStop(@RequestBody Map<String,Object> requestBody){
        UUID profileBusId = UUID.fromString(requestBody.get("profile_bus_id").toString());
        UUID stopId = UUID.fromString(requestBody.get("stop_id").toString());
        int stopOrder = Integer.parseInt(requestBody.get("stop_order").toString());
        String stopTime = requestBody.get("stop_time").toString();
        UUID profileStopId = ProfileService.createProfileStop(profileBusId, stopId, stopOrder, stopTime);
        Map<String,Object> response = new HashMap<>();
        if(profileStopId != null){
            response.put("status","S");
            response.put("message","Profile stop inserted successfully");
            response.put("profileStopId", profileStopId);
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile stop not inserted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }

    @PostMapping("/admin/createProfileRiderStop")
    public ResponseEntity<Map<String,Object>> createProfileRiderStop(@RequestBody Map<String,Object> requestBody){
        UUID profileId = UUID.fromString(requestBody.get("profile_id").toString());
        UUID riderId = UUID.fromString(requestBody.get("rider_id").toString());
        UUID profileStopId = UUID.fromString(requestBody.get("profile_stop_id").toString());
        UUID profileRiderStopId = ProfileService.createProfileRiderStop(profileId, riderId, profileStopId);
        Map<String,Object> response = new HashMap<>();
        if(profileRiderStopId != null){
            response.put("status","S");
            response.put("message","Profile rider stop inserted successfully");
            response.put("profileRiderStopId", profileRiderStopId);
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile rider stop not inserted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }


    @DeleteMapping("/admin/deleteProfileBus")
    public ResponseEntity<Map<String,Object>> deleteProfileBus(@RequestBody Map<String,Object> requestBody){
        UUID profileBusId = UUID.fromString(requestBody.get("profile_bus_id").toString());
        boolean done = ProfileService.deleteProfileBus(profileBusId);
        Map<String,Object> response = new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","Profile bus, stops and user mappings recursively deleted successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile bus not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }

    @DeleteMapping("/admin/deleteProfileStop")
    public ResponseEntity<Map<String,Object>> deleteProfileStop(@RequestBody Map<String,Object> requestBody){
        UUID profileStopId = UUID.fromString(requestBody.get("profile_stop_id").toString());
        boolean done = ProfileService.deleteProfileStop(profileStopId);
        Map<String,Object> response = new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","Profile stop and user mappings recursively deleted successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile stop not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }

    @DeleteMapping("/admin/deleteProfileRiderStop")
    public ResponseEntity<Map<String,Object>> deleteProfileRiderStop(@RequestBody Map<String,Object> requestBody){
        UUID profileRiderStopId = UUID.fromString(requestBody.get("profile_rider_stop_id").toString());
        boolean done = ProfileService.deleteProfileRiderStop(profileRiderStopId);
        Map<String,Object> response = new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","User (profile for rider stop) mapping deleted successfully");
            return ResponseEntity.ok(response);
        } else {
            response.put("status","E");
            response.put("message","Profile rider stop not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
    @PostMapping("/admin/getrnoVehicleMappingById")
    public ResponseEntity<Map<String,Object>> getvehicleRnoMappingById(@RequestBody Map<String,Object> requestBody){
        UUID VehicleRnoMapId = UUID.fromString(requestBody.get("vehicle_rno_map_id").toString());
        Vehicle_rno_mapping mapping=VehicleRnoService.getById(VehicleRnoMapId);
        Map<String,Object> response=new HashMap<>();
        if(mapping!=null){
            response.put("status","S");
            response.put("result",mapping);
            response.put("message","Mapping retrieved successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","Mappings not retrieved successfully");
            return ResponseEntity.status(503).body(response);
        }

    }

    @GetMapping("/admin/getAllRnoVehicleMapping")
    public ResponseEntity<Map<String,Object>> getrnoVehicleMapping(){
        List<Vehicle_rno_mapping> mappings=VehicleRnoService.getAll();

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
            return ResponseEntity.status(503).body(response);
        }

    }
    @PostMapping("/admin/insertVehicleRnoMapping")
    public ResponseEntity<Map<String,Object>> insertVehicleRnoMapping(@RequestBody Map<String,Object> requestBody){
        Vehicle_rno_mapping mapping=Vehicle_rno_mapping.builder()
                .routeNo((String)requestBody.get("route_no"))
                .vehicleNo((String)requestBody.get("vehicle_no"))
                .build();
        boolean done=VehicleRnoService.create_vehicle_rno_mapping(mapping);
        Map<String,Object> response=new HashMap<>();
        if(done){
            response.put("status","S");
            response.put("message","inserted the mapping successfully");
            return ResponseEntity.ok(response);
        }
        else{
            response.put("status","E");
            response.put("message","not inserted the mapping successfully");
            return ResponseEntity.status(503).body(response);
        }

    }
    @DeleteMapping("/admin/deleteVehicleRnoMapping")
    public ResponseEntity<Map<String,Object>> deleteVehicleRnoMapping(@RequestBody Map<String,Object> requestBody) {
        UUID VehicleRnoMapId = UUID.fromString(requestBody.get("vehicle_rno_map_id").toString());
        boolean done = VehicleRnoService.delete_vehicle_rno_mapping(VehicleRnoMapId);
        Map<String, Object> response = new HashMap<>();
        if (done) {
            response.put("status", "S");
            response.put("message", "mapping deleted successfully");
            return ResponseEntity.ok(response);

        } else {
            response.put("status", "E");
            response.put("message", "mapping not deleted successfully");
            return ResponseEntity.status(503).body(response);
        }
    }
        @PostMapping("/admin/updateVehicleRnoMapping")
        public ResponseEntity<Map<String,Object>> updateVehicleRnoMapping(@RequestBody Map<String,Object> requestBody){
            Vehicle_rno_mapping mapping=Vehicle_rno_mapping.builder()
                    .id(UUID.fromString(requestBody.get("id").toString()))
                    .routeNo((String)requestBody.get("route_no"))
                    .vehicleNo((String)requestBody.get("vehicle_rno"))
                    .build();
            Boolean done =VehicleRnoService.update_vehicle_rno_mappings(mapping);
            Map<String,Object> response=new HashMap<>();
            if(done){
                response.put("status", "S");
                response.put("message", "mapping updated successfully");
                return ResponseEntity.ok(response);
            }
            else{
                response.put("status", "E");
                response.put("message", "mapping not  updated successfully");
                return ResponseEntity.status(503).body(response);
            }

        }

     }






