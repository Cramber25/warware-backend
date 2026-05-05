package pl.cramber.assetstore.util;

import java.time.Duration;

public class TimeUtils {

    private TimeUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Duration parseDuration(String input) {
        if (input == null || input.isEmpty()) return null;
        try {
            char unit = input.charAt(input.length() - 1);
            long amount = Long.parseLong(input.substring(0, input.length() - 1));
            return switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                case 'w' -> Duration.ofDays(amount * 7);
                case 'M' -> Duration.ofDays(amount * 30);
                default -> Duration.ofMinutes(Long.parseLong(input));
            };
        } catch (Exception e) {
            return null;
        }
    }
}