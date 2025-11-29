package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.entities.Stop;
import org.apache.ibatis.annotations.*;
import com.bustracking.bustrack.entities.Vehicle_rno_mapping;
import java.util.List;
import java.util.UUID;
@Mapper
public interface VehicleRnoMapping {
    @Select("select * from vehicle_rno_mapping where id =#{id}")
    Vehicle_rno_mapping getById(@Param("id") UUID id);
    @Select("select * from vehicle_rno_mapping")
    List<Vehicle_rno_mapping> getAll();
    @Insert("insert into vehicle_rno_mapping(route_no,vehicle_no)values(#{routeNo},#{vehicleNo})")
    int  insertMappings(Vehicle_rno_mapping mapping);
    @Delete("delete from vehicle_rno_mapping where id=#{id}")
    int deleteMapping(@Param("id")UUID id);
    @Update("update vehicle_rno_mapping set route_no=#{routeNo},vehicle_no=#{vehicleNo} where id=#{id}")
    int updateMapping(Vehicle_rno_mapping mapping);


}
