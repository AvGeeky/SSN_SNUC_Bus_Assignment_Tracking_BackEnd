package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.dto.BusRouteStopDTO;
import com.bustracking.bustrack.dto.UserStopFinderDTO;
import com.bustracking.bustrack.mappings.RiderMapping;
import com.bustracking.bustrack.entities.Rider;

import com.bustracking.bustrack.mappings.StopFinderMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RiderService {
    private final RiderMapping riderMapper;
    private final StopFinderMapper stopFinderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(RiderService.class);

    private static final String USER_STOP_KEY_PREFIX = "userStopDetails:";
    // Cached for an hour. Admin changes evict the cache (UserStopCacheEvictionInterceptor), so the long TTL is safe.
    private static final long USER_STOP_TTL_SECONDS = 3600;
    // Random extra time per entry so riders who logged in together do not all expire, and hit Postgres, together
    private static final long USER_STOP_TTL_JITTER_SECONDS = 600;

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

    public List<UserStopFinderDTO> findUserStop(UUID riderId, boolean forceCacheRefresh) {
        String key = USER_STOP_KEY_PREFIX + riderId;
        if (!forceCacheRefresh) {
            try {
                String cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, new TypeReference<List<UserStopFinderDTO>>() {});
                }
            } catch (Exception e) {
                // Redis down or entry unreadable: fall through to the database rather than failing the request
                log.warn("User stop cache read failed for {}: {}", riderId, e.getMessage());
            }
        }

        List<UserStopFinderDTO> result = stopFinderMapper.getUserStopDetails(riderId);
        try {
            long ttl = USER_STOP_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, USER_STOP_TTL_JITTER_SECONDS + 1);
            // set() overwrites, so a forced refresh needs no separate delete (and never leaves a gap with no entry)
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), ttl, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("User stop cache write failed for {}: {}", riderId, e.getMessage());
        }
        return result;
    }

    /** Drops every cached findUserStop result. Called after admin changes, since entries now live for an hour. */
    public void evictAllUserStopCache() {
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                List<byte[]> batch = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(USER_STOP_KEY_PREFIX + "*").count(500).build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= 500) {
                            connection.keyCommands().del(batch.toArray(new byte[0][]));
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    connection.keyCommands().del(batch.toArray(new byte[0][]));
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("Could not evict user stop cache: {}", e.getMessage());
        }
    }

}
