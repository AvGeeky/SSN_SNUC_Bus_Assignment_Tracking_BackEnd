package com.bustracking.bustrack.Services.GPSService;

import com.bustracking.bustrack.util.RegNoNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Pattern;

/**
 * Small helpers shared by the GPS provider services.
 */
final class GpsSupport {
    private static final Logger log = LoggerFactory.getLogger(GpsSupport.class);

    static final int CONNECT_TIMEOUT_MS = 5_000;
    static final int READ_TIMEOUT_MS = 10_000;

    // Provider URLs carry api keys / passwords in the query string, and RestClientException messages include the URL.
    private static final Pattern QUERY_STRING = Pattern.compile("\\?[^\\s\"']*");

    private GpsSupport() {
    }

    /** RestTemplate whose calls can never hang forever on a dead or half-open provider connection. */
    static RestTemplate newRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** Reads an int env var; a missing, malformed or too small value falls back to the default instead of crashing startup. */
    static int envInt(String name, int defaultValue, int min) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= min) {
                return value;
            }
            log.warn("Env var {}={} is below the minimum of {}. Using default {}.", name, value, min, defaultValue);
        } catch (NumberFormatException e) {
            log.warn("Env var {}='{}' is not a number. Using default {}.", name, raw, defaultValue);
        }
        return defaultValue;
    }

    /** Exception text that is safe to log: query strings (api keys, passwords) are removed. */
    static String safeMessage(Throwable t) {
        String message = t.getMessage() == null ? "" : ": " + t.getMessage();
        return QUERY_STRING.matcher(t.getClass().getSimpleName() + message).replaceAll("?<redacted>");
    }

    // ---- JSON field access. Anything missing or malformed throws, so the caller can skip just that record. ----

    static String requiredRegNo(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing '" + field + "'");
        }
        String regNo = RegNoNormalizer.normalize(value.asText());
        if (regNo == null || regNo.isEmpty()) {
            throw new IllegalArgumentException("blank '" + field + "'");
        }
        return regNo;
    }

    static double requiredDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing '" + field + "'");
        }
        try {
            if (value.isNumber()) {
                return value.doubleValue();
            }
            if (value.isTextual()) {
                return Double.parseDouble(value.asText().trim());
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException("non-numeric '" + field + "'");
    }

    static long requiredLong(JsonNode node, String field) {
        return (long) requiredDouble(node, field);
    }

    static double optionalDouble(JsonNode node, String field, double defaultValue) {
        try {
            return requiredDouble(node, field);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    /** Rejects impossible coordinates and the (0,0) "no GPS fix" placeholder that trackers report. */
    static void checkFix(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("coordinates out of range (" + lat + ", " + lng + ")");
        }
        if (lat == 0.0 && lng == 0.0) {
            throw new IllegalArgumentException("coordinates are (0,0), no GPS fix");
        }
    }
}
