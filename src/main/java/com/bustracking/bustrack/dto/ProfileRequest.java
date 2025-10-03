package com.bustracking.bustrack.dto;
import lombok.Data;
import java.util.List;
import java.util.UUID;
@Data
public class ProfileRequest {       // make the class public
    private ProfileDto profile;     // make fields private (good practice)
    private List<ProfileBusDto> buses;
}
