package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.dto.*;
import com.bustracking.bustrack.entities.*;
import com.bustracking.bustrack.mappings.*;
import jakarta.annotation.PostConstruct;
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
     VehicleRnoMapping vehiclernoMapper;

    @Autowired
    RiderMapping riderMapping;


    /*
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
        ROUTE_TO_BUS_NUMBER_MAP.put("24", "TN11BS7484");
        ROUTE_TO_BUS_NUMBER_MAP.put("16", "TN19BD9907");
        ROUTE_TO_BUS_NUMBER_MAP.put("18", "TN19BD9986");
    }

     */
    private final Map<String, String> ROUTE_TO_BUS_NUMBER_MAP = new HashMap<>();
    @PostConstruct
    private void initMap() {
        List<Vehicle_rno_mapping> list = vehiclernoMapper.getAll();
        for (Vehicle_rno_mapping item : list) {
            ROUTE_TO_BUS_NUMBER_MAP.put(item.getRouteNo(), item.getVehicleNo());
        }
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

        // 1. FAST LOOKUP MAPS (Prevents "TooManyResultsException" crash)
        // We load all riders once. If duplicates exist, putIfAbsent keeps the first one and ignores the rest.
        Map<String, Rider> riderMap = new HashMap<>();
        List<Rider> allRiders = riderMapping.getAll(); // Ensure this method exists in your RiderMapping

        if (allRiders != null) {
            for (Rider r : allRiders) {
                if (r.getDigitalId() == null) continue;
                riderMap.putIfAbsent(r.getDigitalId().toLowerCase().trim(), r);
            }
        }

        Map<String, Stop> stopMap = new HashMap<>();
        List<Stop> allStops = stopMapper.getAll();

        if (allStops != null) {
            for (Stop s : allStops) {
                if (s.getName() == null) continue;
                stopMap.put(s.getName().toLowerCase().trim(), s);
            }
        }

        List<ProfileBusDto> buses = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String routeNumber = sheet.getSheetName().trim();

                // --- Bus Lookup ---
                String licensePlate = ROUTE_TO_BUS_NUMBER_MAP.get(routeNumber);
                if (licensePlate == null) {
                    System.out.println("Warning: Route '" + routeNumber + "' skipped (no map entry).");
                    continue;
                }

                Bus databaseBus = busMapper.findByBusNumber(licensePlate);
                if (databaseBus == null) {
                    System.out.println("Warning: Bus '" + licensePlate + "' not found in DB.");
                    continue;
                }

                ProfileBusDto busDto = new ProfileBusDto();
                busDto.setBusId(databaseBus.getId());
                busDto.setBusNumber(routeNumber);

                List<AssignmentDto> assignmentsForThisBus = new ArrayList<>();
                // Use LinkedHashMap to preserve the insertion order of stops
                Map<String, ProfileStopDto> uniqueStops = new LinkedHashMap<>();
                int stopOrderCounter = 1;

                // -----------------------------------------------------------
                // 🚨 CHECK THIS INDEX!
                // 0=A, 1=B, 2=C, 3=D, 4=E, 5=F(Boarding), 6=G, 7=H
                // -----------------------------------------------------------
                int STOP_TIME_COL_INDEX = 7;
                // -----------------------------------------------------------

                for (int j = 1; j <= sheet.getLastRowNum(); j++) {
                    Row row = sheet.getRow(j);

                    if (row == null || row.getCell(1) == null || row.getCell(5) == null) continue;

                    String rollNo = getCellStringValue(row.getCell(1)).trim();
                    String boardingPointName = getCellStringValue(row.getCell(5)).trim();

                    // Safely read stop time (DataFormatter handles "10:00" vs 0.4166)
                    String stopTimeRaw = "";
                    if (row.getCell(STOP_TIME_COL_INDEX) != null) {
                        stopTimeRaw = getCellStringValue(row.getCell(STOP_TIME_COL_INDEX)).trim();
                    }
                    String stopTime = stopTimeRaw.isEmpty() ? "00:00" : stopTimeRaw;

                    if (rollNo.isEmpty() || boardingPointName.isEmpty()) continue;

                    // --- Rider Lookup (Using Fast Map) ---
                    Rider databaseRider = riderMap.get(rollNo.toLowerCase().trim());
                    if (databaseRider == null) {
                        System.out.println("Warning: Rider '" + rollNo + "' not found in DB.");
                        continue;
                    }

                    // --- Stop Lookup & Creation ---
                    ProfileStopDto stopDto;
                    if (!uniqueStops.containsKey(boardingPointName)) {

                        Stop databaseStop = stopMap.get(boardingPointName.toLowerCase().trim());
                        if (databaseStop == null) {
                            System.out.println("Warning: Stop '" + boardingPointName + "' not found in DB.");
                            continue;
                        }

                        stopDto = new ProfileStopDto();
                        stopDto.setStopId(databaseStop.getId());
                        stopDto.setStopOrder(stopOrderCounter);
                        stopDto.setStopTime(stopTime); // ✅ Saving the time from Excel

                        uniqueStops.put(boardingPointName, stopDto);
                        stopOrderCounter++;
                    } else {
                        stopDto = uniqueStops.get(boardingPointName);
                    }

                    // --- Assignment ---
                    AssignmentDto assignment = new AssignmentDto();
                    assignment.setRiderId(databaseRider.getId());
                    assignment.setProfileStopIndex(stopDto.getStopOrder());

                    assignmentsForThisBus.add(assignment);
                }

                busDto.setStops(new ArrayList<>(uniqueStops.values()));
                busDto.setAssignments(assignmentsForThisBus);
                buses.add(busDto);
            }
        }

        profileRequest.setBuses(buses);

        // Call existing method to save to DB
        this.create_full_profile(profileRequest);
    }
    private String getCellStringValue(Cell cell) {

        if (cell == null) {

            return "";

        }

        DataFormatter formatter = new DataFormatter();

        return formatter.formatCellValue(cell);

    }


}