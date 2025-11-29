package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.dto.ProfileRiderStopDTO;
import com.bustracking.bustrack.entities.Profile_rider_stop;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Mapper
public interface ProfileRiderStopMapping {

    @Insert("INSERT INTO profile_rider_stops(id, profile_id, rider_id, profile_stop_id,created_at) VALUES (#{id}, #{profileId}, #{riderId}, #{profileStopId},#{createdAt})")
    void insertProfileRiderStop(@Param("id") UUID id, @Param("profileId") UUID profileId, @Param("riderId") UUID riderId, @Param("profileStopId") UUID profileStopId,  @Param("createdAt") Instant createdAt);

//    @Insert("INSERT INTO profile_rider_stops(id, profile_id, rider_id, profile_stop_id, created_at) VALUES (#{id}, #{profileId}, #{riderId}, #{profileStopId}, #{createdAt})")
//    int insertProfileRiderStop(Profile_rider_stop dto);

    @Insert("<script>" +
            "INSERT INTO profile_rider_stops(id, profile_id, rider_id, profile_stop_id, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.profileId}, #{item.riderId}, #{item.profileStopId}, #{item.createdAt})" +
            "</foreach>" +
            "</script>")
    int insertProfileRiderStops(@Param("list") List<ProfileRiderStopDTO> dtos);

    @Select("select * from profile_rider_stops where profile_id=#{profileId}")
    List<Profile_rider_stop> selectProfileRiderStop(@Param("profileId") UUID profileId);

    @Delete("DELETE FROM profile_rider_stops WHERE id=#{id}")
    int deleteProfileRiderStop(@Param("id") UUID profileRiderStopId);



}
