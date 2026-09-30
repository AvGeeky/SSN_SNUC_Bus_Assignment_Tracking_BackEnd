package com.bustracking.bustrack.Auth;

import com.bustracking.bustrack.Services.RiderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.Set;

/**
 * findUserStop results are cached for an hour, so any admin write (riders, profiles, buses, stops, assignments...)
 * must clear them or riders would see the old assignment until the entry expires.
 * Read-only admin calls, including the POST-style "getXById" lookups, are left alone.
 */
@Component
@RequiredArgsConstructor
public class UserStopCacheEvictionInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final RiderService riderService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (READ_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return;
        }
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        String path = request.getRequestURI();
        String action = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (action.startsWith("get")) {
            return;
        }
        riderService.evictAllUserStopCache();
    }
}
