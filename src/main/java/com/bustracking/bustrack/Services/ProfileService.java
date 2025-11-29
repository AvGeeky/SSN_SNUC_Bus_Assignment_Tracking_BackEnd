package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.dto.*;
import com.bustracking.bustrack.entities.*;
import com.bustracking.bustrack.mappings.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class ProfileService {
    @Autowired
    ProfileMapping profileMapper;
    @Autowired
    ProfileFullMapper profileFullMapper;
    @Autowired
    ProfileBusMapping profileBusMapper;
    @Autowired
    ProfileStopMapping profileStopMapper;
    @Autowired
    ProfileRiderStopMapping profileRiderStopMapper;
    @Autowired
    StopMapping stopMapper;
    @Autowired
    BusMapping busMapper;


    @Autowired
    RiderMapping riderMapping;



    private static final Map<String, String> ROUTE_TO_BUS_NUMBER_MAP = new HashMap<>();

    static {
        ROUTE_TO_BUS_NUMBER_MAP.put("1", "TN19BD8142");
        ROUTE_TO_BUS_NUMBER_MAP.put("2", "TN11BT2470");
        ROUTE_TO_BUS_NUMBER_MAP.put("3", "TN19BD8112");
        ROUTE_TO_BUS_NUMBER_MAP.put("4", "TN19BD9972");
        ROUTE_TO_BUS_NUMBER_MAP.put("4A", "TN11BT2473");
        ROUTE_TO_BUS_NUMBER_MAP.put("5", "TN19BD8111");
        ROUTE_TO_BUS_NUMBER_MAP.put("6", "TN11BS7445");
        ROUTE_TO_BUS_NUMBER_MAP.put("7", "TN11BT2401");
        ROUTE_TO_BUS_NUMBER_MAP.put("8", "TN11BS7430");
        ROUTE_TO_BUS_NUMBER_MAP.put("9", "TN11BS7470");
        ROUTE_TO_BUS_NUMBER_MAP.put("9A", "TN19BD8125");
        ROUTE_TO_BUS_NUMBER_MAP.put("9B", "TN19BD9905");
        ROUTE_TO_BUS_NUMBER_MAP.put("10", "TN11BS7468");
        ROUTE_TO_BUS_NUMBER_MAP.put("11", "TN11BS7458");
        ROUTE_TO_BUS_NUMBER_MAP.put("12", "TN19BD8104");
        ROUTE_TO_BUS_NUMBER_MAP.put("13", "TN11BS7464");
        ROUTE_TO_BUS_NUMBER_MAP.put("14", "TN19BD8106");
        ROUTE_TO_BUS_NUMBER_MAP.put("15", "TN11BS7484");
        ROUTE_TO_BUS_NUMBER_MAP.put("16", "TN19BD9907");
        ROUTE_TO_BUS_NUMBER_MAP.put("18", "TN19BD9986");
    }



    public Map<String,Object> getById(UUID id){
        Map<String,Object> result=new HashMap<>();
        Profile profile= profileMapper.getbyId(id);
        result.put("profile",profile);
        List<Profile_bus> profile_buses=profileBusMapper.selectProfileBus(id);
        result.put("profile_bus",profile_buses);
        List<Profile_rider_stop>  profile_rider_stops=profileRiderStopMapper.selectProfileRiderStop(id);
        result.put("profile_rider_assignments",profile_rider_stops);
        List<UUID> profile_stop_ids=new ArrayList<>();
        for(Profile_rider_stop stop:profile_rider_stops){
            profile_stop_ids.add(stop.getProfileStopId());

        }
        List<Profile_stop> profile_stops=new ArrayList<>();
        for(UUID  profile_stop_id:profile_stop_ids){
            Profile_stop record=profileStopMapper.selectProfileStop(profile_stop_id);
            profile_stops.add(record);
        }
        result.put("profile_stops",profile_stops);
        return result;
    }

    /**
     * Fetches a profile by its ID and transforms the flat database result
     * into a nested object structure.
     *
     * @param profileId The UUID of the profile to fetch.
     * @return A nested ProfileResponse object, or null if not found.
     */
    public ProfileResponse getFullProfileById(UUID profileId) {
        List<ProfileFullFlatRow> flatRows = profileFullMapper.getFullProfileFlat(profileId);
        //System.out.println(flatRows);

        if (flatRows == null || flatRows.isEmpty()) {
            return null; // Or throw a custom NotFoundException
        }

        ProfileResponse response = new ProfileResponse();
        ProfileFullFlatRow firstRow = flatRows.get(0);

        // Corrected Profile Getters (using camelCase)
        response.setProfileId(firstRow.getProfileId());
        response.setProfileName(firstRow.getProfileName());
        response.setProfileStatus(firstRow.getProfileStatus());

        // Use LinkedHashMap to preserve the insertion order from the SQL query
        Map<UUID, ProfileResponse.Bus> busMap = new LinkedHashMap<>();
        Map<UUID, ProfileResponse.Stop> stopMap = new LinkedHashMap<>();

        for (ProfileFullFlatRow row : flatRows) {
            // A profile might exist with no buses assigned.
            if (row.getProfileBusId() == null) continue; // Corrected: getProfileBusId()

            // Step 1: Process Bus
            // computeIfAbsent ensures we create each Bus object only once.
            ProfileResponse.Bus bus = busMap.computeIfAbsent(row.getProfileBusId(), k -> { // Corrected: getProfileBusId()
                ProfileResponse.Bus newBus = new ProfileResponse.Bus();
                newBus.setProfileBusId(row.getProfileBusId()); // Corrected: getProfileBusId()
                newBus.setBusId(row.getBusId());             // Corrected: getBusId()
                newBus.setBusNumber(row.getBusNumber());     // Corrected: getBusNumber()
                newBus.setCapacity(row.getCapacity());
                newBus.setBrand(row.getBrand());
                return newBus;
            });

            // A bus might exist with no stops assigned.
            if (row.getProfileStopId() == null) continue; // Corrected: getProfileStopId()

            // Step 2: Process Stop
            // Similarly, create each Stop object only once.
            ProfileResponse.Stop stop = stopMap.computeIfAbsent(row.getProfileStopId(), k -> { // Corrected: getProfileStopId()
                ProfileResponse.Stop newStop = new ProfileResponse.Stop();
                newStop.setProfileStopId(row.getProfileStopId()); // Corrected: getProfileStopId()
                newStop.setStopId(row.getStopId());               // Corrected: getStopId()
                newStop.setStopName(row.getStopName());           // Corrected: getStopName()
                newStop.setLat(row.getLat());
                newStop.setLng(row.getLng());
                newStop.setStopOrder(row.getStopOrder());         // Corrected: getStopOrder()
                newStop.setStopTime(row.getStopTime());           // Corrected: getStopTime()

                // When a new stop is created, add it to its parent bus.
                // Note: This assumes bus.getStops() is initialized (which it is in the DTO you provided).
                bus.getStops().add(newStop);
                return newStop;
            });

            // A stop might exist with no riders assigned.
            if (row.getRiderId() == null) continue; // Corrected: getRiderId()

            // Step 3: Process Rider
            // Riders are always added to the current stop from the row.
            ProfileResponse.Rider rider = new ProfileResponse.Rider();
            rider.setProfileRiderStopId(row.getProfileRiderStopId()); // Set the new ID
            rider.setRiderId(row.getRiderId());       // Corrected: getRiderId()
            rider.setRiderName(row.getRiderName());   // Corrected: getRiderName()
            rider.setRiderEmail(row.getRiderEmail()); // Corrected: getRiderEmail()
            rider.setYear(row.getYear());
            rider.setDepartment(row.getDepartment());
            stop.getRiders().add(rider);
        }

        response.setBuses(new ArrayList<>(busMap.values()));
        return response;
    }



    public List<Profile> getAll(){
        return profileMapper.getAll();
    }

    @Transactional
    public void create_full_profile(ProfileRequest req){
        List<ProfileRiderStopDTO> profileRiderStopDTOs=new ArrayList<>();

        UUID profileId = UUID.randomUUID();
        Profile profile=Profile.builder()
                .id(profileId)
                .name((String)req.getProfile().getName())
                .status((String)req.getProfile().getStatus())
                .createdAt(Instant.now())
                .build();
        profileMapper.insert_Profile(profile);

        for(ProfileBusDto busdto: req.getBuses()){
            UUID profileBusId = UUID.randomUUID();
            profileBusMapper.insertProfileBus(profileBusId, profileId,busdto.getBusId(), busdto.getBusNumber());

            // This map is crucial for linking assignments
            Map<Integer, UUID> profileStopIdsByOrder = new HashMap<>();

            for(ProfileStopDto stopdto:busdto.getStops()){
                UUID profileStopId = UUID.randomUUID();
                profileStopMapper.insertProfileStop(profileStopId,profileBusId,stopdto.getStopId(),stopdto.getStopOrder(),stopdto.getStopTime());
                // Store the generated UUID against its order
                profileStopIdsByOrder.put(stopdto.getStopOrder(), profileStopId);
            }

            for (AssignmentDto a : busdto.getAssignments()) {
                UUID profileRiderStopId = UUID.randomUUID();

                // Get the correct profileStopId using the index from the assignment
                UUID profileStopId = profileStopIdsByOrder.get(a.getProfileStopIndex());

                if (profileStopId == null) {
                    // This error is important for debugging bad Excel data
                    throw new RuntimeException("Invalid profileStopIndex: " + a.getProfileStopIndex() + " for bus " + busdto.getBusNumber());
                }
                profileRiderStopDTOs.add(new ProfileRiderStopDTO(profileRiderStopId, profileId, a.getRiderId(), profileStopId, Instant.now()));
                //profileRiderStopMapper.insertProfileRiderStop(profileRiderStopId, profileId, a.getRiderId(), profileStopId,Instant.now());
            }

        }
        // Batch insert all assignments at once
        if (!profileRiderStopDTOs.isEmpty()) {
            int test = profileRiderStopMapper.insertProfileRiderStops(profileRiderStopDTOs);

            if (test > 0) {
                log.info("Profile created successfully... Inserted " + test + " rider assignments.");
            }
        }
    }

    @Transactional
    public Boolean delete_profile(UUID id){
        int rows_affected=profileMapper.delete_Profile(id);
        return rows_affected>0;
    }

    @Transactional
    public Boolean update_Profile_Status(Profile profile){
        if(profile.getStatus().equals("active")){
            int no_actives=profileMapper.countAllActiveProfiles();

            if(no_actives==0){
                int rows_affected=profileMapper.update_Profile_Status(profile);
                return rows_affected>0;
            }
            return false;
        }
        else{
            int rows_affected=profileMapper.update_Profile_Status(profile);
            return rows_affected>0;
        }
    }


    @Transactional
    public UUID createProfileBus(UUID profileId, UUID busId, String busNumber) {
        UUID profileBusId = UUID.randomUUID();
        profileBusMapper.insertProfileBus(profileBusId, profileId, busId, busNumber);
        return profileBusId;
    }

    @Transactional
    public UUID createProfileStop(UUID profileBusId, UUID stopId, int stopOrder, String stopTime) {
        UUID profileStopId = UUID.randomUUID();
        profileStopMapper.insertProfileStop(profileStopId, profileBusId, stopId, stopOrder, stopTime);
        return profileStopId;
    }

    @Transactional
    public UUID createProfileRiderStop(UUID profileId, UUID riderId, UUID profileStopId) {
        UUID profileRiderStopId = UUID.randomUUID();
        profileRiderStopMapper.insertProfileRiderStop(profileRiderStopId, profileId, riderId, profileStopId, Instant.now());
        return profileRiderStopId;
    }

    @Transactional
    public boolean deleteProfileBus(UUID profileBusId) {
        return profileBusMapper.deleteProfileBus(profileBusId) > 0;
    }

    @Transactional
    public boolean deleteProfileStop(UUID profileStopId) {
        return profileStopMapper.deleteProfileStop(profileStopId) > 0;
    }

    @Transactional
    public boolean deleteProfileRiderStop(UUID profileRiderStopId) {
        return profileRiderStopMapper.deleteProfileRiderStop(profileRiderStopId) > 0;
    }


    @Transactional(rollbackFor = Exception.class)
    public void create_full_profile_from_excel(MultipartFile file, String profileName, String profileStatus) throws Exception {

        ProfileRequest profileRequest = new ProfileRequest();

        ProfileDto profileDto = new ProfileDto();
        profileDto.setName(profileName);
        profileDto.setStatus(profileStatus);
        profileRequest.setProfile(profileDto);

        // Pre-fetch all riders into a map for quick lookup by digitalId (RollNo)
        Map<String, Rider> riderMap = new HashMap<>();
        for (Rider r : riderMapping.findAllRiders()) {
            if (r.getDigitalId() == null) continue;
            riderMap.put(r.getDigitalId().toLowerCase().trim(), r);
        }

        Map<String, Stop> stopMap = new HashMap<>();
        for (Stop r : stopMapper.findAllStops()) {
            if (r.getName() == null) continue;
            stopMap.put(r.getName().toLowerCase().trim(), r);
        }

        List<ProfileBusDto> buses = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            // 2. Iterate over each SHEET (each sheet is one BUS)
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // The Route Number (e.g., "9A", "43") is the sheet name.
                String routeNumber = sheet.getSheetName().trim();

                // -----------------------------------------------------------------
                // NEW LOGIC: Use the map to find the bus license plate
                // -----------------------------------------------------------------
                String licensePlate = ROUTE_TO_BUS_NUMBER_MAP.get(routeNumber);

                if (licensePlate == null) {
                    log.warn("Warning: Route '{}' is not in the hardcoded map. Skipping sheet.", routeNumber);
                    continue; // Skip this sheet
                }

                // Now find the bus in the database using the license plate
                // Uses your 'busMapper' variable
                Bus databaseBus = busMapper.findByBusNumber(licensePlate);

                if (databaseBus == null) {
                    log.warn("Warning: Bus with license plate '{}' (for route {}) not found in DB. Skipping sheet.", licensePlate, routeNumber);
                    continue; // Skip this sheet
                }
                // -----------------------------------------------------------------


                // We found the bus! Now we can build the DTO.
                ProfileBusDto busDto = new ProfileBusDto();
                busDto.setBusId(databaseBus.getId()); // <-- This is the Bus UUID
                busDto.setBusNumber(routeNumber);     // <-- This is the Route No ("9A")

                List<AssignmentDto> assignmentsForThisBus = new ArrayList<>();
                // Use LinkedHashMap to preserve the insertion order of stops
                Map<String, ProfileStopDto> uniqueStops = new LinkedHashMap<>();
                int stopOrderCounter = 1;

                // 3. Iterate over each ROW (each row is a RIDER ASSIGNMENT)
                // Assuming row 0 is header, data starts at row 1
                for (int j = 1; j <= sheet.getLastRowNum(); j++) {
                    Row row = sheet.getRow(j);
                    // Col 1 = RollNo, Col 5 = Boarding Point (based on your '9A.csv' sample)
                    if (row == null || row.getCell(1) == null || row.getCell(5) == null) {
                        continue;
                    }

                    String rollNo = getCellStringValue(row.getCell(1)).trim();
                    String boardingPointName = getCellStringValue(row.getCell(5)).trim();

                    if (rollNo.isEmpty() || boardingPointName.isEmpty()) {
                        continue;
                    }

                    String normalizedRoll = rollNo.toLowerCase().trim();
                    Rider databaseRider = riderMap.get(normalizedRoll);
                    if (databaseRider == null) {
                        log.warn("Warning: Rider with RollNo(digital_id) '{}' not found. Skipping.", rollNo);
                        continue;
                    }

                    // --- Process the Stop (using name) ---
                    ProfileStopDto stopDto;
                    if (!uniqueStops.containsKey(boardingPointName)) {
                        // Uses your 'stopMapper' variable

                        Stop databaseStop = stopMap.get(boardingPointName.toLowerCase().trim());
                        if (databaseStop == null) {
                            log.warn("Warning: Stop with name '{}' not found. Skipping.", boardingPointName);
                            continue;
                        }

                        stopDto = new ProfileStopDto();
                        stopDto.setStopId(databaseStop.getId());
                        stopDto.setStopOrder(stopOrderCounter);
                        stopDto.setStopTime("00:00"); // Setting default time, as it's not in Excel

                        uniqueStops.put(boardingPointName, stopDto);
                        stopOrderCounter++;
                    } else {
                        stopDto = uniqueStops.get(boardingPointName);
                    }

                    // 4. Create the Assignment DTO
                    AssignmentDto assignment = new AssignmentDto();
                    assignment.setRiderId(databaseRider.getId());
                    assignment.setProfileStopIndex(stopDto.getStopOrder()); // Link to the stop's order

                    assignmentsForThisBus.add(assignment);
                }

                busDto.setStops(new ArrayList<>(uniqueStops.values()));
                busDto.setAssignments(assignmentsForThisBus);
                buses.add(busDto);
            }
        }

        profileRequest.setBuses(buses);

        // 5. Finally, call your existing transactional method
        this.create_full_profile(profileRequest);
    }

    /**
     * Helper method to safely get string values from any cell type
     * (e.g., handles numeric RollNo as a String).
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

}