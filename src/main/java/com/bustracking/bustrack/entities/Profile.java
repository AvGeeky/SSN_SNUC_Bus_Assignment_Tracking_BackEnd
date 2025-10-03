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
public class Profile {
   private UUID id;
   private String name;
   private String status;
   private Instant createdAt;
}
