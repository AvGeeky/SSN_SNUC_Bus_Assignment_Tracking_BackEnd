package com.bustracking.bustrack.dto;

import lombok.Data;

@Data
public class BusRouteStopDTO {
    String busNumber;
    int stopOrder;
    String stopName;
    String stopTime;
    double stopLatitude;
    double stopLongitude;
}