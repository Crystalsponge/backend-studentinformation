package crys.sims.utils;

import java.util.List;

/**
 * Sequential ID generation (S001, SUBJ002, F003, ...).
 * Derives the next ID from existing IDs so restarts stay collision-free.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String nextId(List<String> existingIds, String prefix, int width) {
        int max = 0;
        for (String id : existingIds) {
            if (id != null && id.startsWith(prefix)) {
                try {
                    int n = Integer.parseInt(id.substring(prefix.length()));
                    if (n > max) max = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + String.format("%0" + width + "d", max + 1);
    }
}
