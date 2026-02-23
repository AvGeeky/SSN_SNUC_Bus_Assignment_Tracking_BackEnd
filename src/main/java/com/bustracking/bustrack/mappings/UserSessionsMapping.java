package com.bustracking.bustrack.mappings;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.bustracking.bustrack.entities.User_sessions;
@Mapper
public interface UserSessionsMapping {
    @Select("select * from user_sessions where username=#{username}")
    User_sessions getByusername(@Param("username")String username);

    @Select("SELECT username, login_type, ttl_days,created_at,expires_at FROM user_sessions")
    List<User_sessions> getAllData();

    @Insert("insert into user_sessions(username,password,login_type)values(#{username},#{password},#{login_type})")
    int insertUser_sessions(User_sessions session);

    @Update("update user_sesions set username=#{username} password=#{password} login_type=#{login_type} where id=#{id}")
    int updateUser_sesions(@Param("id")UUID id,@Param("username")String username,@Param("password")String password,@Param("login_type")String login_type);

    @Delete("delete from user_sessions where id=#{id}")
    int deleteUser_sessions(@Param("id")UUID id);

    @Delete("""
        DELETE FROM user_sessions
        WHERE expires_at IS NOT NULL
        AND expires_at <= NOW()
    """)
    int deleteExpiredSessions();
}
