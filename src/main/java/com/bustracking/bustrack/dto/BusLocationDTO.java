package com.bustracking.bustrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BusLocationDTO {
    private String regNo;       // Unified Registration Number
    private double latitude;
    private double longitude;
    private double speed;
    private String timestamp;
    private String source;
    private String odometer;
    private String ignition;
                            // To know which API it came from
}