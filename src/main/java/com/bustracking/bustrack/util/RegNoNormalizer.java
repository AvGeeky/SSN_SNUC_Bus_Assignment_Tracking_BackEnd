package com.bustracking.bustrack.util;

import java.util.Locale;

/**
 * Single definition of what a bus registration number looks like in Redis and in lookups.
 * "tn 87-h 0937", "TN-87-H-0937" and "TN87H0937" all become "TN87H0937".
 * Every provider parser, and every controller that looks a bus up, must go through this.
 */
public final class RegNoNormalizer {

    private RegNoNormalizer() {
    }

    /** Uppercases and keeps only ASCII letters and digits. Returns null for null input, "" if nothing is left. */
    public static String normalize(String regNo) {
        if (regNo == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(regNo.length());
        for (int i = 0; i < regNo.length(); i++) {
            char c = regNo.charAt(i);
            if (c < 128 && Character.isLetterOrDigit(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
