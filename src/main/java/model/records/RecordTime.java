package model.records;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import model.exceptions.ValidationException;

/** Keeps time parsing and formatting consistent across commands and saved files. */
public final class RecordTime {
    // Keep strict parsing compatible with the supported year 0000.
    private static final String TIME_PATTERN = "uuuu-MM-dd HH:mm";
    private static final Pattern TIME_SHAPE = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern(TIME_PATTERN, Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private RecordTime() {
    }

    /** Check the input shape first, then validate the calendar date and time. */
    public static LocalDateTime parse(String text) throws ValidationException {
        if (text == null || !TIME_SHAPE.matcher(text).matches()) {
            throw new ValidationException("Time must use the format yyyy-MM-dd HH:mm.");
        }
        try {
            return LocalDateTime.parse(text, FORMATTER);
        } catch (DateTimeParseException error) {
            throw new ValidationException("Invalid date or time: " + text, error);
        }
    }

    public static String format(LocalDateTime time) {
        return FORMATTER.format(time);
    }

    /** Check that a time can be saved and read back at the same precision. */
    public static LocalDateTime requireMinute(LocalDateTime time) throws ValidationException {
        if (time == null || time.getSecond() != 0 || time.getNano() != 0
                || time.getYear() < 0 || time.getYear() > 9999) {
            throw new ValidationException("Time must have a four-digit year and minute precision.");
        }
        return time;
    }
}
