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
public class Profile_bus {
  private UUID id;
  private UUID profileId;
  private UUID  busId;
  private String busNumber;

}
