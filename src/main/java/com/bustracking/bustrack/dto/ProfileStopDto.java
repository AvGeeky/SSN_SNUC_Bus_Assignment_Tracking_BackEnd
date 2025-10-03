package com.bustracking.bustrack.dto;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class ProfileStopDto {
    UUID stopId;
    Integer stopOrder;
    String stopTime;
}