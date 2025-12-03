package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.dto.BusRouteStopDTO;
import com.bustracking.bustrack.dto.UserStopFinderDTO;
import com.bustracking.bustrack.mappings.RiderMapping;
import com.bustracking.bustrack.entities.Rider;

import com.bustracking.bustrack.mappings.StopFinderMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RiderService {
    private final RiderMapping riderMapper;
    private final StopFinderMapper stopFinderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long USER_STOP_TTL_SECONDS = 180;

    public RiderService(RiderMapping riderMapper, StopFinderMapper stopFinderMapper,
                        StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.riderMapper = riderMapper;
        this.stopFinderMapper = stopFinderMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    public List<BusRouteStopDTO> findFullRouteForRider(UUID riderId) {
        return stopFinderMapper.getBusRouteForRider(riderId);
    }
    public int studentsInUsersBus(UUID riderId) {
        return stopFinderMapper.countRidersForBusRoute(riderId);
    }
    public Rider getById(UUID id){
        return riderMapper.getbyId(id);
    }
    public List<Rider> getAll(){
        return riderMapper.getAll();
    }

    @Transactional
    public Boolean create_rider(List<Rider> riders){
        int rows_affected=0;
        for(Rider rider : riders) {
          rider.setId(UUID.randomUUID());
           rows_affected += riderMapper.insert_rider(rider);
      }
        return rows_affected>0;
    }
    @Transactional
    public Boolean delete_rider(UUID id){
        int rows_affected=riderMapper.delete_rider(id);
        return rows_affected>0;
    }
    @Transactional
    public Boolean update_rider(Rider rider){
        int rows_affected=riderMapper.update_rider(rider);
        return rows_affected>0;
    }


    public Rider getByEmail(String email) {
        return riderMapper.findByEmail(email).orElse(null);
    }

//    public List<UserStopFinderDTO> findUserStop(UUID riderId) {
//        return stopFinderMapper.getUserStopDetails(riderId);
//    }
    public List<UserStopFinderDTO> findUserStop(UUID riderId) {
        String key = "userStopDetails:" + riderId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<UserStopFinderDTO>>() {});
            } catch (Exception ignored) {
            }
        }
        List<UserStopFinderDTO> result = stopFinderMapper.getUserStopDetails(riderId);
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, USER_STOP_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return result;
    }
}
