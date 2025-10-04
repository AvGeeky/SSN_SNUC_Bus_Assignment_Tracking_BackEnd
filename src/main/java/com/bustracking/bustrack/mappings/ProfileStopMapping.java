package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.entities.Profile_stop;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.UUID;
@Mapper
public interface ProfileStopMapping {
    @Insert("INSERT INTO profile_stops(id, profile_bus_id, stop_id, stop_order, stop_time) VALUES (#{id}, #{profileBusId}, #{stopId}, #{stopOrder}, #{stopTime})")
    int insertProfileStop(@Param("id") UUID id, @Param("profileBusId") UUID profileBusId, @Param("stopId") UUID stopId, @Param("stopOrder") int stopOrder, @Param("stopTime") String stopTime);

    @Select("select * from profile_stops where id=#{id}")
    Profile_stop selectProfileStop(@Param("id") UUID id);
}
