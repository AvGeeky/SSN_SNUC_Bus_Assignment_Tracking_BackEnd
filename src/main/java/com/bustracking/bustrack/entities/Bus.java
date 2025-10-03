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
public class Bus {
      private UUID id;
      private Integer capacity;
      private Instant createdAt;
      private String busNumber;
      private String  brand;
}

