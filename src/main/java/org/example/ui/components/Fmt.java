package org.example.ui.components;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Text formatting shared by all screens, so dates and marks look the same everywhere. */
public final class Fmt {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

    private Fmt() {
    }

    public static String dateTime(LocalDateTime t) {
        return t == null ? "Not scheduled" : t.format(DATE_TIME);
    }

    public static String time(LocalDateTime t) {
        return t == null ? "—" : t.format(TIME);
    }

    /** "TODAY" or "THU". */
    public static String dayLabel(LocalDateTime t) {
        if (t == null) {
            return "—";
        }
        return t.toLocalDate().equals(LocalDate.now()) ? "TODAY" : t.format(DAY).toUpperCase(Locale.ENGLISH);
    }

    public static String dayOfMonth(LocalDateTime t) {
        return t == null ? "" : String.valueOf(t.getDayOfMonth());
    }

    /** "85%" or "—" when there is no value. */
    public static String percent(BigDecimal p) {
        return p == null ? "—" : p.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 20.00 -> "20", 7.50 -> "7.5". */
    public static String marks(BigDecimal m) {
        if (m == null) {
            return "—";
        }
        BigDecimal s = m.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    public static String plural(int n, String singular) {
        return n + " " + singular + (n == 1 ? "" : "s");
    }

    /** 3725 -> "1:02:05", 94 -> "01:34". */
    public static String clock(long seconds) {
        long s = Math.max(0, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }
}
