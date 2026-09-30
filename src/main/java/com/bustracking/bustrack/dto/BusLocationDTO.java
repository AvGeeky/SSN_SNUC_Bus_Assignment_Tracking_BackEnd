package com.bustracking.bustrack.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusLocationDTO {
    private String regNo;       // Unified Registration Number (see RegNoNormalizer)
    private double latitude;
    private double longitude;
    private double speed;
    private String timestamp;   // Always "yyyy-MM-dd HH:mm:ss" in IST (see TimestampNormalizer)
    private long epochMs;       // Same instant as timestamp, absolute. 0 on entries written before this field existed
    private String source;      // To know which API it came from
    private String odometer;
    private String ignition;
}
