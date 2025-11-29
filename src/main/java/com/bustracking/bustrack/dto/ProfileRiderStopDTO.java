package com.bustracking.bustrack.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ProfileRiderStopDTO {
    private UUID id;
    private UUID profileId;
    private UUID riderId;
    private UUID profileStopId;
    private Instant createdAt;


}
