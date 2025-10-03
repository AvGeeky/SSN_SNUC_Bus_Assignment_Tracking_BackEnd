package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.entities.Rider;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;
@Mapper
public interface RiderMapping {
  @Select("select * from riders where id =#{id}")
    Rider  getbyId(UUID id);
  @Select("select * from riders")
    List<Rider>  getAll();
  @Insert("insert into riders(id,name,year,department,home_stop_id,created_at,college,email,digital_id)values(#{id},#{name},#{year},#{department},#{homeStopId},#{createdAt},#{college},#{email},#{digitalId})")
  int insert_rider(Rider rider);
  @Update("update riders set name=#{name},year=#{year},department=#{department},home_stop_id=#{homeStopId},created_at=#{createdAt},college=#{college},email=#{email},digital_id=#{digitalId} where id=#{id}")
  int update_rider(Rider rider);
  @Delete("delete from riders where id=#{id}")
  int delete_rider(UUID id);
}
