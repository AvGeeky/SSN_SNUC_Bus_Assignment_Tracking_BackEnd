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
public class Vehicle_rno_mapping {
    private UUID id;
    private String routeNo;
    private String vehicleNo;

}

