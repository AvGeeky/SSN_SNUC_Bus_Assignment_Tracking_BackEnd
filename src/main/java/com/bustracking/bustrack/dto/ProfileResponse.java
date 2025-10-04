package com.bustracking.bustrack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents the final, perfectly nested JSON response structure for a full profile.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {
    private UUID profileId;
    private String profileName;
    private String profileStatus;
    private List<Bus> buses;

    @Data
    @NoArgsConstructor
    public static class Bus {
        private UUID profileBusId;
        private UUID busId;
        private String busNumber;
        private Integer capacity;
        private String brand;
        private List<Stop> stops = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Stop {
        private UUID profileStopId;
        private UUID stopId;
        private String stopName;
        private Double lat;
        private Double lng;
        private Integer stopOrder;
        private String stopTime;
        private List<Rider> riders = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class Rider {
        private UUID profileRiderStopId;
        private UUID riderId;
        private String riderName;
        private String riderEmail;
        private Integer year;
        private String department;
    }
}
