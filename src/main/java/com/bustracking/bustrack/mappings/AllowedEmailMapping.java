package com.bustracking.bustrack.mappings;

import com.bustracking.bustrack.entities.AllowedEmail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface AllowedEmailMapping {
    @Select("SELECT * FROM allowed_emails WHERE email = #{email}")
    Optional<AllowedEmail> findByEmail(String email);
}
