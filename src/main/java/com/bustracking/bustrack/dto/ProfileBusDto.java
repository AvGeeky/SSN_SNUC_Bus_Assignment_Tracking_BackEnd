package com.bustracking.bustrack.dto;
import lombok.Data;
import java.util.List;
import java.util.UUID;
@Data
public class ProfileBusDto {
    UUID busId;
    String busNumber;
    Integer capacity;
    String routeName;
    List<ProfileStopDto> stops;
    List<AssignmentDto> assignments;
}