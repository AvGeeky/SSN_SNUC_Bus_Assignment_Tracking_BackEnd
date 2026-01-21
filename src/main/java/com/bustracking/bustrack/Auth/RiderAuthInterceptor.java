package com.bustracking.bustrack.Auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class RiderAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        Cookie jwtCookie = WebUtils.getCookie(request, "jwt");
//
//        if (jwtCookie == null) {
//            log.error("Missing JWT cookie for request to {}", request.getRequestURI());
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
//            return false;
//        }
//
//        final String jwt = jwtCookie.getValue();

        final String authHeader = request.getHeader("Authorization");
        String jwt = null;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setStatus(HttpServletResponse.SC_OK);
            return false; // Stop processing and return 200/204 to the browser
        }

        // 2. Validate Header format
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7); // Remove "Bearer " prefix
        }

        if (jwt == null) {
            log.error("Missing or invalid Authorization header for request to {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
            return false;
        }

        try {
            Claims claims = jwtUtil.extractAllClaims(jwt);
            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            if (!"rider".equals(role)) {
                log.warn("Non-rider user '{}' with role '{}' attempted to access rider path {}", email, role, request.getRequestURI());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Requires rider role.");
                return false;
            }

            if (email != null && !jwtUtil.isTokenExpired(jwt)) {
                request.setAttribute("userEmail", email);
                request.setAttribute("userRole", role);
                return true;
            }
        } catch (ExpiredJwtException e) {
            log.error("JWT token has expired: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
            return false;
        } catch (JwtException e) {
            log.error("JWT validation error: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Token");
            return false;
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        return false;
    }
}
