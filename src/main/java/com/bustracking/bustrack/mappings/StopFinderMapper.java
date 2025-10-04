package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.dto.UserStopFinderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface StopFinderMapper {


    @Select("""
        -- This query finds a rider's bus and stop assignment within the currently active profile.
        -- It is designed to answer "For a given rider, what bus and stop should they be at according to the active schedule?"
        SELECT
            r.id AS rider_id,
            r.name AS rider_name,
            r.email AS rider_email,
            p.id AS active_profile_id,
            p.name AS profile_name,
            pb.bus_number,
            s.name AS stop_name,
            ps.stop_order,
            ps.stop_time,
            s.lat as stop_latitude,
            s.lng as stop_longitude
        FROM
            riders r
            -- Join to find the rider's assignment in any profile
        JOIN
            profile_rider_stops prs ON r.id = prs.rider_id
            -- Join to the profiles table to filter for the active one
        JOIN
            profiles p ON prs.profile_id = p.id
            -- Join to profile_stops to find out which stop this assignment corresponds to
        JOIN
            profile_stops ps ON prs.profile_stop_id = ps.id
            -- Join to the master stops table to get the stop's name
        JOIN
            stops s ON ps.stop_id = s.id
            -- Join to profile_buses to find out which bus this stop belongs to
        JOIN
            profile_buses pb ON ps.profile_bus_id = pb.id
        WHERE
            -- Filter so we only look at assignments within the 'active' profile
            p.status = 'active'
            -- And specify which rider you are looking for
            AND r.id = #{profileId};
    """)
    List<UserStopFinderDTO> getUserStopDetails(UUID profileId);
}
