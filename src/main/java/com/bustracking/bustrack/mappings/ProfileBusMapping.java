package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.entities.Profile_bus;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.UUID;
@Mapper
public interface ProfileBusMapping {
    @Insert("INSERT INTO profile_buses(id, profile_id, bus_id, bus_number) VALUES (#{id}, #{profileId}, #{busId}, #{busNumber})")
    int insertProfileBus(@Param("id") UUID id, @Param("profileId") UUID profileId, @Param("busId") UUID busId, @Param("busNumber") String busNumber);
    @Select("select * from profile_buses where profile_id=#{profileId}")
    List<Profile_bus> selectProfileBus(@Param("profileId") UUID profileId);
}
