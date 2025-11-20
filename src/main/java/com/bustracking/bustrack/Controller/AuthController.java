package com.bustracking.bustrack.Controller;

import com.bustracking.bustrack.Auth.JwtUtil;
import com.bustracking.bustrack.Firebase.FirebaseAuthService;

import com.bustracking.bustrack.Services.AllowedEmailService;
import com.bustracking.bustrack.Services.RiderService;

import com.bustracking.bustrack.entities.AllowedEmail;
import com.bustracking.bustrack.entities.Rider;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final FirebaseAuthService firebaseAuthService;
    private final RiderService riderService;

    private final AllowedEmailService allowedEmailService;
    private final JwtUtil jwtUtil;

    @PostMapping("/auth/firebase")
    public ResponseEntity<Map<String, Object>> authenticateWithFirebase(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String idToken = request.get("idToken");

        if (idToken == null || idToken.isBlank()) {
            response.put("status", "E");
            response.put("message", "ID token not provided in request body.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            FirebaseToken decodedToken = firebaseAuthService.verifyIdToken(idToken);
            String email = decodedToken.getEmail();

            Map<String, Object> claims = new HashMap<>();
            Object userDetails;
            String role;

            // 1. Check if the user is an admin in allowed_emails

            AllowedEmail admin = allowedEmailService.getByEmail(email);
            if (admin != null && "admin".equalsIgnoreCase(admin.getRole())) {
                String adminEmail = admin.getEmail();
                role = "admin";
                claims.put("role", role);
                claims.put("email", adminEmail);
                userDetails = admin;
            } else if (admin != null) {
                // User exists in allowed_emails but is not an admin
                response.put("status", "E");
                response.put("message", "User is not an admin.");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            } else {
                // 2. If not an admin, check if the user is a rider
                Rider rider = riderService.getByEmail(email);
                if (rider != null) {
                    role = "rider";
                    claims.put("role", role);
                    claims.put("riderId", rider.getId().toString());
                    claims.put("email", rider.getEmail());
                    userDetails = rider;
                } else {
                    // 3. If neither, the user is not authorized
                    response.put("status", "E");
                    response.put("message", "User is not registered as an admin or a rider.");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                }
            }

            // 4. Generate JWT
            String jwt = jwtUtil.generateToken(email, claims);
//            ResponseCookie cookie = ResponseCookie.from("jwt", jwt)
//                    .httpOnly(true)
//                    .secure(true) // Set to false if not using HTTPS in development
//                    .path("/")
//                    .maxAge(24 * 60 * 60) // 24 hours
//                    .build();


            // 5. Build success response
            response.put("status", "S");
            response.put("message", "Login successful");
            response.put("email", email);
            response.put("name", decodedToken.getName());
            response.put("picture", decodedToken.getPicture());
            response.put("userDetails", userDetails);
            response.put("role", role);
            response.put("token", jwt);

            return ResponseEntity.ok()
                    .body(response);

        } catch (Exception e) {
            response.put("status", "E");
            response.put("message", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
}
