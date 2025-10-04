package com.bustracking.bustrack.dto;

import lombok.Data;

import java.util.UUID;
@Data
public class UserStopFinderDTO {
    private UUID riderId;
    private String riderName;
    private String riderEmail;
    private UUID activeProfileId;
    private String profileName;
    private String busNumber;
    private String stopName;
    private int stopOrder;
    private String stopTime;
    private double stopLatitude;
    private double stopLongitude;
}