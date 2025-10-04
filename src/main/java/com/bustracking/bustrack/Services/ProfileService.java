package com.bustracking.bustrack.Services;
import com.bustracking.bustrack.dto.AssignmentDto;
import com.bustracking.bustrack.dto.ProfileBusDto;
import com.bustracking.bustrack.dto.ProfileRequest;
import com.bustracking.bustrack.dto.ProfileStopDto;
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
}
