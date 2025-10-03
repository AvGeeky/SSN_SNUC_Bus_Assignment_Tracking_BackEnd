package com.bustracking.bustrack.entities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.UUID;
import lombok.Data;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Profile_stop {
   private UUID id;
   private UUID profileBusId;
   private UUID stopId;
   private Integer stopOrder;
   private String stopTime;

}
