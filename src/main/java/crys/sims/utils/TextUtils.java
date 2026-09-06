package crys.sims.utils;

import java.util.Locale;

/**
 * Shared string helpers. Single home for the null-safe display default
 * and case-insensitive matching previously duplicated across views
 * and StudentController.
 */
public final class TextUtils {

    private TextUtils() {
    }

    public static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    public static boolean containsIgnoreCase(String value, String query) {
        if (value == null || query == null) return false;
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
