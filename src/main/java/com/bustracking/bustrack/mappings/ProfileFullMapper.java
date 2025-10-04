package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.dto.ProfileFullFlatRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ProfileFullMapper {


    @Select("""
        SELECT
            p.id AS profile_id,
            p.name AS profile_name,
            p.status AS profile_status,
            pb.id AS profile_bus_id,
            pb.bus_id AS bus_id,
            pb.bus_number,
            b.capacity,
            b.brand,
            ps.id AS profile_stop_id,
            ps.stop_id AS stop_id,
            s.name AS stop_name,
            s.lat,
            s.lng,
            ps.stop_order,
            ps.stop_time,
            prs.id AS profile_rider_stop_id,
            r.id AS rider_id,
            r.name AS rider_name,
            r.email AS rider_email,
            r.year,
            r.department
        FROM profiles p
        LEFT JOIN profile_buses pb ON pb.profile_id = p.id
        LEFT JOIN buses b ON b.id = pb.bus_id
        LEFT JOIN profile_stops ps ON ps.profile_bus_id = pb.id
        LEFT JOIN stops s ON s.id = ps.stop_id
        LEFT JOIN profile_rider_stops prs ON prs.profile_stop_id = ps.id AND prs.profile_id = p.id
        LEFT JOIN riders r ON r.id = prs.rider_id
        WHERE p.id = #{profileId}
        ORDER BY pb.bus_number, ps.stop_order, r.name
    """)
    List<ProfileFullFlatRow> getFullProfileFlat(UUID profileId);
}
