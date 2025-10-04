package com.bustracking.bustrack.Services;
import com.bustracking.bustrack.dto.*;
import com.bustracking.bustrack.entities.*;
import com.bustracking.bustrack.mappings.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

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
    @Autowired StopMapping stopMapper;   // existing master mappers
    @Autowired BusMapping busMapper;
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
            List<UUID> profileStopIds = new ArrayList<>();
            for(ProfileStopDto stopdto:busdto.getStops()){
                UUID profileStopId = UUID.randomUUID();
                profileStopMapper.insertProfileStop(profileStopId,profileBusId,stopdto.getStopId(),stopdto.getStopOrder(),stopdto.getStopTime());
                profileStopIds.add(profileStopId);
            }
            for (AssignmentDto a : busdto.getAssignments()) {
                UUID profileRiderStopId = UUID.randomUUID();
                UUID profileStopId = profileStopIds.get(a.getProfileStopIndex()-1); // validate index
                profileRiderStopMapper.insertProfileRiderStop(profileRiderStopId, profileId, a.getRiderId(), profileStopId,Instant.now());
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

}
