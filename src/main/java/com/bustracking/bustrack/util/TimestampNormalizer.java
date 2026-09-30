package com.bustracking.bustrack.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Providers send timestamps as "yyyy-MM-dd HH:mm:ss" or "dd-MM-yyyy HH:mm:ss" (all in IST).
 * Everything stored in Redis uses one format ("yyyy-MM-dd HH:mm:ss", IST) plus an absolute epochMs.
 */
public final class TimestampNormalizer {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(IST);
    private static final Pattern DIGITS = Pattern.compile("\\d{9,}");
    private static final Pattern HAS_OFFSET = Pattern.compile(".*(Z|[+-]\\d{2}:?\\d{2})$");

    private static final List<DateTimeFormatter> LOCAL_INPUTS = List.of(
            local("yyyy-MM-dd HH:mm:ss"),
            local("dd-MM-yyyy HH:mm:ss"),
            local("yyyy-MM-dd'T'HH:mm:ss"),
            local("dd/MM/yyyy HH:mm:ss"),
            local("yyyy/MM/dd HH:mm:ss")
    );

    /** A normalised point in time: display text (IST) and the absolute epoch millis. */
    public record Stamp(String text, long epochMs) {
    }

    private TimestampNormalizer() {
    }

    public static Stamp of(long epochMs) {
        return new Stamp(OUTPUT.format(Instant.ofEpochMilli(epochMs)), epochMs);
    }

    public static Stamp now() {
        return of(System.currentTimeMillis());
    }

    /** Parses any supported provider format. Local (zone-less) formats are read as IST. */
    public static Optional<Stamp> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }

        if (DIGITS.matcher(s).matches()) {
            long v = Long.parseLong(s);
            // 9-11 digits: epoch seconds, 12+ digits: epoch millis
            return Optional.of(of(s.length() <= 11 ? v * 1000L : v));
        }

        if (s.indexOf('T') > 0 && HAS_OFFSET.matcher(s).matches()) {
            try {
                return Optional.of(of(OffsetDateTime.parse(s).toInstant().toEpochMilli()));
            } catch (DateTimeParseException ignored) {
                // fall through to the local formats
            }
        }

        for (DateTimeFormatter f : LOCAL_INPUTS) {
            try {
                long epoch = LocalDateTime.parse(s, f).atZone(IST).toInstant().toEpochMilli();
                return Optional.of(of(epoch));
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return Optional.empty();
    }

    private static DateTimeFormatter local(String pattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
                .toFormatter(Locale.ROOT);
    }
}
