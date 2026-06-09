package com.throttle4j.spring.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for parsing human-friendly time-window strings into milliseconds.
 *
 * <p>Accepted suffixes (case-insensitive):</p>
 * <ul>
 *   <li>{@code ms} &mdash; milliseconds (e.g. {@code "500ms"})</li>
 *   <li>{@code s}  &mdash; seconds      (e.g. {@code "30s"})</li>
 *   <li>{@code m}  &mdash; minutes      (e.g. {@code "1m"})</li>
 *   <li>{@code h}  &mdash; hours        (e.g. {@code "1h"})</li>
 *   <li>{@code d}  &mdash; days         (e.g. {@code "1d"})</li>
 * </ul>
 *
 * <p>A bare number (e.g. {@code "1000"}) is interpreted as milliseconds.</p>
 */
public final class WindowParser {

    private static final Pattern PATTERN = Pattern.compile("^\\s*(\\d+)\\s*(ms|s|m|h|d)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private WindowParser() {
        // utility
    }

    /**
     * Parse a window string into milliseconds.
     *
     * @param window window expression (e.g. {@code "1m"})
     * @return duration in milliseconds (always {@code > 0})
     * @throws IllegalArgumentException if the input cannot be parsed or is non-positive
     */
    public static long parseToMillis(String window) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        Matcher m = PATTERN.matcher(window);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid window expression: " + window);
        }
        long value = Long.parseLong(m.group(1));
        String unit = m.group(2);
        long millis;
        if (unit == null || unit.isEmpty()) {
            millis = value;
        } else {
            switch (unit.toLowerCase()) {
                case "ms":
                    millis = value;
                    break;
                case "s":
                    millis = value * 1000L;
                    break;
                case "m":
                    millis = value * 60_000L;
                    break;
                case "h":
                    millis = value * 3_600_000L;
                    break;
                case "d":
                    millis = value * 86_400_000L;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid window unit: " + unit);
            }
        }
        if (millis <= 0L) {
            throw new IllegalArgumentException("window must be > 0: " + window);
        }
        return millis;
    }
}
