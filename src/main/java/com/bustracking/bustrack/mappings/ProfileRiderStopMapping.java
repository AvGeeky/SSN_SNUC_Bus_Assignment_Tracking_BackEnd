package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.entities.Profile_rider_stop;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Mapper
public interface ProfileRiderStopMapping {
    @Insert("INSERT INTO profile_rider_stops(id, profile_id, rider_id, profile_stop_id,created_at) VALUES (#{id}, #{profileId}, #{riderId}, #{profileStopId},#{createdAt})")
    int insertProfileRiderStop(@Param("id") UUID id, @Param("profileId") UUID profileId, @Param("riderId") UUID riderId, @Param("profileStopId") UUID profileStopId,  @Param("createdAt") Instant createdAt);

    @Select("select * from profile_rider_stops where profile_id=#{profileId}")
    List<Profile_rider_stop> selectProfileRiderStop(@Param("profileId") UUID profileId);

}
