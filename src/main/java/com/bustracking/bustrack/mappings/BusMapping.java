package com.bustracking.bustrack.mappings;
import com.bustracking.bustrack.entities.Bus;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;
@Mapper
public interface BusMapping {
    @Select("select * from buses where id =#{id}")
    Bus getbyId(UUID id);

    @Select("select * from buses")
    List<Bus> getAll();

    @Insert("insert into buses(id,capacity,created_at,bus_number,brand)values(#{id},#{capacity},#{createdAt},#{busNumber},#{brand})")
    int insert_bus(Bus bus);

    @Update("update buses set capacity=#{capacity},bus_number=#{busNumber},brand=#{brand} where id=#{id}")
    int update_bus_capacity(Bus bus);

    @Delete("delete from buses where id=#{id}")
    int delete_bus(UUID id);
}
