package com.bustracking.bustrack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents the flat data structure returned by the complex SQL join.
 * Each row contains denormalized information about a profile, bus, stop, and rider.
 * Fields are in camelCase to correctly map from snake_case database columns
 * when 'map-underscore-to-camel-case' is enabled.
 */
@Data
public class ProfileFullFlatRow {

    // Profile Fields
    private UUID profileId;         // Maps from 'profile_id'
    private String profileName;     // Maps from 'profile_name'
    private String profileStatus;   // Maps from 'profile_status'

    // Bus Fields (Profile_Buses & Buses tables)
    private UUID profileBusId;      // Maps from 'profile_bus_id'
    private UUID busId;             // Maps from 'bus_id'
    private String busNumber;       // Maps from 'bus_number'
    private Integer capacity;       // Maps from 'capacity'
    private String brand;           // Maps from 'brand'

    // Stop Fields (Profile_Stops & Stops tables)
    private UUID profileStopId;     // Maps from 'profile_stop_id'
    private UUID stopId;            // Maps from 'stop_id'
    private String stopName;        // Maps from 'stop_name'
    private Double lat;             // Maps from 'lat'
    private Double lng;             // Maps from 'lng'
    private Integer stopOrder;      // Maps from 'stop_order'
    private String stopTime;        // Maps from 'stop_time'

    // Rider Fields
    private UUID profileRiderStopId; // Maps from 'profile_rider_stop_id'
    private UUID riderId;           // Maps from 'rider_id'
    private String riderName;       // Maps from 'rider_name'
    private String riderEmail;      // Maps from 'rider_email'
    private Integer year;           // Maps from 'year'
    private String department;      // Maps from 'department'
}
