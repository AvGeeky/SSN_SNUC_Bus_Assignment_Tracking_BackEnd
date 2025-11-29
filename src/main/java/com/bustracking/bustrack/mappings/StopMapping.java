package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.entities.Profile;
import org.apache.ibatis.annotations.*;
import com.bustracking.bustrack.entities.Stop;
import java.util.List;
import java.util.UUID;
@Mapper
public interface StopMapping {
    @Select("select * from stops where id =#{id}")
    Stop getById(@Param("id") UUID id);

    @Select("select * from stops")
    List<Stop> getAll();

    @Insert("insert into stops(id,name,lat,lng)values(#{id},#{name},#{lat},#{lng})")
    int insertStop(Stop stop);

    @Delete("delete from stops where id=#{id}")
    int  delete_stop(@Param("id") UUID id);

    @Select("SELECT * FROM stops WHERE LOWER(TRIM(name)) = LOWER(TRIM(#{name}))")
    Stop findByName(@Param("name") String name);

    @Select("SELECT * FROM stops")
    List<Stop> findAllStops();

}
