package com.sablednah.standards.core;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Human durations: {@code 30m}, {@code 2h30m}, {@code 7d}, {@code 90}.
 *
 * <p>A bare number means seconds, which is the one everybody assumes and nobody documents.
 * {@code perm}, {@code permanent} and {@code forever} all mean "no expiry", because a moderator
 * reaching for a permanent mute should not have to remember which word this particular mod chose.
 * Units may repeat and appear in any order — {@code 1h30m} and {@code 30m1h} are the same 90
 * minutes, since refusing the second one teaches nobody anything.</p>
 */
public final class Duration {

    /** Returned for a permanent duration. */
    public static final long PERMANENT = -1L;

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([a-z]+)");

    /**
     * @return seconds, {@link #PERMANENT}, or empty if it is not a duration at all
     */
    public static Optional<Long> parse(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return Optional.empty();
        if (text.equals("perm") || text.equals("permanent") || text.equals("forever")) {
            return Optional.of(PERMANENT);
        }
        // A bare number is seconds.
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        Matcher matcher = PART.matcher(text);
        long total = 0;
        int consumed = 0;
        while (matcher.find()) {
            long value;
            try {
                value = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            Long unit = seconds(matcher.group(2));
            if (unit == null) return Optional.empty();
            total += value * unit;
            consumed += matcher.group().length();
        }
        // Every character must have been part of a recognised unit. Without this, "5 bananas"
        // parses as five seconds and the moderator never finds out they typed nonsense.
        return consumed == text.replace(" ", "").length() && total > 0
                ? Optional.of(total)
                : Optional.empty();
    }

    private static Long seconds(String unit) {
        return switch (unit) {
            case "s", "sec", "secs", "second", "seconds" -> 1L;
            case "m", "min", "mins", "minute", "minutes" -> 60L;
            case "h", "hr", "hrs", "hour", "hours" -> 3600L;
            case "d", "day", "days" -> 86_400L;
            case "w", "wk", "week", "weeks" -> 604_800L;
            case "mo", "month", "months" -> 2_592_000L;      // 30 days, stated rather than exact
            case "y", "yr", "year", "years" -> 31_536_000L;  // 365 days
            default -> null;
        };
    }

    /** Back to something readable: {@code 90061} becomes {@code 1d 1h 1m 1s}. */
    public static String describe(long seconds) {
        if (seconds == PERMANENT) return "permanent";
        if (seconds <= 0) return "0s";
        StringBuilder sb = new StringBuilder();
        long left = seconds;
        for (Object[] unit : new Object[][] {
                {86_400L, "d"}, {3600L, "h"}, {60L, "m"}, {1L, "s"}}) {
            long size = (Long) unit[0];
            long count = left / size;
            if (count > 0) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(count).append(unit[1]);
                left -= count * size;
            }
        }
        return sb.toString();
    }

    private Duration() {}
}
