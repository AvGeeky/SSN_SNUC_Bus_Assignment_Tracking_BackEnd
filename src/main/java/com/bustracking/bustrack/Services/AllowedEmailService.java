package com.bustracking.bustrack.Services;

import com.bustracking.bustrack.entities.AllowedEmail;
import com.bustracking.bustrack.mappings.AllowedEmailMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AllowedEmailService {
    private final AllowedEmailMapping allowedEmailMapping;

    public AllowedEmail getByEmail(String email) {
        return allowedEmailMapping.findByEmail(email).orElse(null);
    }
}
