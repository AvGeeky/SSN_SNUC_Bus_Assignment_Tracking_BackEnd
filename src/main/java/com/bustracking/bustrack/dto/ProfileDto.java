package com.bustracking.bustrack.dto;
import lombok.Data;
import java.util.List;
import java.util.UUID;
@Data
public class ProfileDto {
    UUID id;
    public String name;
    String status;
}