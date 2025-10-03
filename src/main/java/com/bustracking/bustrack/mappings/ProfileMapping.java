package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.entities.Profile;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;
@Mapper
public interface ProfileMapping {
  @Select("select * from profiles where id=#{id}")
  Profile getbyId(UUID id);
  @Select("select * from profiles")
  List<Profile>  getAll();
  @Insert("insert into profiles(id,name,status,created_at)values(#{id},#{name},#{status},#{createdAt})")
  int insert_Profile(Profile profile);
  @Update("update profiles set status=#{status} where id=#{id}")
  int update_Profile_Status(Profile profile);
  @Delete("delete from profiles where id=#{id}")
  int delete_Profile(UUID id);

}
