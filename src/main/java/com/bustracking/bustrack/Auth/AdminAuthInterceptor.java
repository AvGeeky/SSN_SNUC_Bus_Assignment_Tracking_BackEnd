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
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Cookie jwtCookie = WebUtils.getCookie(request, "jwt");

        if (jwtCookie == null) {
            log.error("Missing JWT cookie for request to {}", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication token");
            return false;
        }

        final String jwt = jwtCookie.getValue();

        try {
            Claims claims = jwtUtil.extractAllClaims(jwt);
            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            if (!"admin".equals(role)) {
                log.warn("Non-admin user '{}' with role '{}' attempted to access admin path {}", email, role, request.getRequestURI());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Requires admin role.");
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
