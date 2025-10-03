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
public class Rider {
    private UUID id;
    private String name;
    private Integer year;
    private String department;
    private String college;
    private String email;
    private UUID homeStopId;
    private Instant createdAt;
    private String digitalId;
}
