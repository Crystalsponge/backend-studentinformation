package crys.sims.utils;

import crys.sims.model.enums.GENDER;

import java.time.LocalDate;
import java.util.Scanner;

/**
 * Scanner prompt helpers shared by all views.
 * Read methods loop until valid input; "Optional" variants accept
 * empty input and return the current value (for update flows).
 */
public final class InputUtils {

    private InputUtils() {
    }

    public static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        String line = scanner.nextLine();
        return line == null ? "" : line.trim();
    }

    public static String readRequiredLine(Scanner scanner, String prompt) {
        while (true) {
            String v = readLine(scanner, prompt);
            if (!v.isEmpty()) return v;
            System.out.println("  Value must not be blank. Try again.");
        }
    }

    public static int readMenuChoice(Scanner scanner, int min, int max) {
        while (true) {
            String v = readLine(scanner, "Choice [" + min + "-" + max + "]: ");
            try {
                int n = Integer.parseInt(v);
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("  Enter a number between " + min + " and " + max + ".");
        }
    }

    public static int readInt(Scanner scanner, String prompt, int min) {
        while (true) {
            String v = readLine(scanner, prompt);
            try {
                int n = Integer.parseInt(v);
                if (n >= min) return n;
                System.out.println("  Must be >= " + min + ". Try again.");
            } catch (NumberFormatException e) {
                System.out.println("  Not a number. Try again.");
            }
        }
    }

    public static int readOptionalInt(Scanner scanner, String prompt, int current) {
        while (true) {
            String v = readLine(scanner, prompt + " [" + current + "]: ");
            if (v.isEmpty()) return current;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                System.out.println("  Not a number. Try again (empty keeps current).");
            }
        }
    }

    public static LocalDate readOptionalDate(Scanner scanner, String prompt) {
        while (true) {
            String v = readLine(scanner, prompt + " (yyyy-MM-dd, empty = none): ");
            if (v.isEmpty()) return null;
            try {
                return ValidationUtils.requireValidDate(v, "date");
            } catch (IllegalArgumentException e) {
                System.out.println("  Invalid date. Use yyyy-MM-dd.");
            }
        }
    }

    public static LocalDate readOptionalDate(Scanner scanner, String prompt, LocalDate current) {
        while (true) {
            String v = readLine(scanner, prompt + " [" + (current == null ? "none" : current) + "]: ");
            if (v.isEmpty()) return current;
            try {
                return ValidationUtils.requireValidDate(v, "date");
            } catch (IllegalArgumentException e) {
                System.out.println("  Invalid date. Use yyyy-MM-dd (empty keeps current).");
            }
        }
    }

    public static GENDER readGender(Scanner scanner, String prompt) {
        while (true) {
            String v = readLine(scanner, prompt + " (MALE/FEMALE): ");
            try {
                return ValidationUtils.parseGender(v);
            } catch (IllegalArgumentException e) {
                System.out.println("  Enter MALE or FEMALE.");
            }
        }
    }

    public static GENDER readOptionalGender(Scanner scanner, String prompt, GENDER current) {
        while (true) {
            String v = readLine(scanner, prompt + " [" + (current == null ? "none" : current) + "]: ");
            if (v.isEmpty()) return current;
            try {
                return ValidationUtils.parseGender(v);
            } catch (IllegalArgumentException e) {
                System.out.println("  Enter MALE, FEMALE, or empty to keep current.");
            }
        }
    }

    public static boolean readYesNo(Scanner scanner, String prompt, boolean defaultValue) {
        String hint = defaultValue ? "Y/n" : "y/N";
        while (true) {
            String v = readLine(scanner, prompt + " (" + hint + "): ");
            if (v.isEmpty()) return defaultValue;
            if (v.equalsIgnoreCase("y") || v.equalsIgnoreCase("yes")) return true;
            if (v.equalsIgnoreCase("n") || v.equalsIgnoreCase("no")) return false;
            System.out.println("  Enter Y or N.");
        }
    }
}
