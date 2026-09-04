package crys.sims.utils;

import java.util.List;

/**
 * Console table formatting shared by all views.
 */
public final class FormatUtils {

    private FormatUtils() {
    }

    public static void printHeader(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    public static void printTable(String[] headers, List<String[]> rows) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = headers[i] == null ? 0 : headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < cols && i < row.length; i++) {
                int len = row[i] == null ? 0 : row[i].length();
                if (len > widths[i]) widths[i] = len;
            }
        }
        StringBuilder fmt = new StringBuilder();
        for (int w : widths) {
            fmt.append("%-").append(w + 2).append("s");
        }
        String format = fmt.toString();
        System.out.println(String.format(format, (Object[]) headers));
        System.out.println(separator(totalWidth(widths)));
        for (String[] row : rows) {
            String[] cells = new String[cols];
            for (int i = 0; i < cols; i++) {
                cells[i] = (i < row.length && row[i] != null) ? row[i] : "";
            }
            System.out.println(String.format(format, (Object[]) cells));
        }
        System.out.println(rows.size() + " row(s).");
    }

    private static int totalWidth(int[] widths) {
        int total = 0;
        for (int w : widths) {
            total += w + 2;
        }
        return total;
    }

    public static String separator(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append('-');
        }
        return sb.toString();
    }
}
