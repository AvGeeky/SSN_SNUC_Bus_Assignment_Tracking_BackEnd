package com.bustracking.bustrack.entities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Profile_rider_stop {
   private UUID id;
   private UUID profileId;
   private UUID riderId;
   private UUID profileStopId;
    private Instant createdAt;

}
