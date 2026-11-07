package dev.profit.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Parses "$12.12B" / "1,500,000" / "760m" and formats back to "$12.12B". */
public final class Money {
    private Money() {}

    public static long parse(String raw) {
        String s = raw.replace("$", "").replace(",", "").replace(" ", "").trim();
        if (s.isEmpty()) throw new NumberFormatException("empty amount");
        long mult = switch (Character.toUpperCase(s.charAt(s.length() - 1))) {
            case 'K' -> 1_000L;
            case 'M' -> 1_000_000L;
            case 'B' -> 1_000_000_000L;
            case 'T' -> 1_000_000_000_000L;
            default -> 1L;
        };
        if (mult != 1L) s = s.substring(0, s.length() - 1);
        return new BigDecimal(s).multiply(BigDecimal.valueOf(mult))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String format(long v) {
        long a = Math.abs(v);
        String body;
        if (a >= 1_000_000_000_000L) body = scaled(a, 1_000_000_000_000L) + "T";
        else if (a >= 1_000_000_000L) body = scaled(a, 1_000_000_000L) + "B";
        else if (a >= 1_000_000L) body = scaled(a, 1_000_000L) + "M";
        else if (a >= 1_000L) body = scaled(a, 1_000L) + "K";
        else body = Long.toString(a);
        return (v < 0 ? "-$" : "$") + body;
    }

    private static String scaled(long a, long unit) {
        return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(unit), 2, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString();
    }
}
