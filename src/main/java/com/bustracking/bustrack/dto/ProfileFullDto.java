package com.bustracking.bustrack.dto;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ProfileFullDto {
    private UUID id;
    private String name;
    private String status;
    private List<ProfileBusDto> buses;
}
