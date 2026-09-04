package crys.sims.service;

import crys.sims.datastructure.ArrayQueue;
import crys.sims.model.WaitlistEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO waitlist for full subject offerings, backed by ArrayQueue.
 * The live queue is the source of truth for order; the file mirrors it
 * (saved on every mutation, rebuilt from file order on startup).
 */
public class WaitlistService {

    private final ArrayQueue<WaitlistEntry> queue;
    private final Path filePath;

    public WaitlistService(List<WaitlistEntry> loaded, Path filePath) {
        this.queue = new ArrayQueue<>();
        if (loaded != null) {
            for (WaitlistEntry e : loaded) {
                queue.enqueue(e);
            }
        }
        this.filePath = filePath;
    }

    public void join(WaitlistEntry entry) throws IOException {
        for (int i = 0; i < queue.size(); i++) {
            WaitlistEntry q = queue.get(i);
            if (sameKey(q, entry)) {
                throw new IllegalArgumentException("Already on waitlist for "
                        + entry.getSubjectId() + " (" + entry.getSemester() + ").");
            }
        }
        queue.enqueue(entry);
        save();
    }

    public WaitlistEntry peekHead(String subjectId, String semester) {
        for (int i = 0; i < queue.size(); i++) {
            WaitlistEntry q = queue.get(i);
            if (subjectId.equals(q.getSubjectId()) && semester.equals(q.getSemester())) {
                return q;
            }
        }
        return null;
    }

    public List<WaitlistEntry> listAll() {
        List<WaitlistEntry> out = new ArrayList<>(queue.size());
        for (int i = 0; i < queue.size(); i++) {
            out.add(queue.get(i));
        }
        return out;
    }

    public int size(String subjectId, String semester) {
        int n = 0;
        for (int i = 0; i < queue.size(); i++) {
            WaitlistEntry q = queue.get(i);
            if (subjectId.equals(q.getSubjectId()) && semester.equals(q.getSemester())) {
                n++;
            }
        }
        return n;
    }

    public WaitlistEntry admit(String subjectId, String semester) throws IOException {
        final WaitlistEntry head = peekHead(subjectId, semester);
        if (head == null) return null;
        queue.removeFirstMatch(new ArrayQueue.Matcher<WaitlistEntry>() {
            @Override
            public boolean matches(WaitlistEntry item) {
                return sameKey(item, head);
            }
        });
        save();
        return head;
    }

    public boolean leave(String studentId, String subjectId, String semester) throws IOException {
        final WaitlistEntry key = new WaitlistEntry(studentId, subjectId, semester, null);
        boolean removed = queue.removeFirstMatch(new ArrayQueue.Matcher<WaitlistEntry>() {
            @Override
            public boolean matches(WaitlistEntry item) {
                return sameKey(item, key);
            }
        });
        if (removed) save();
        return removed;
    }

    private static boolean sameKey(WaitlistEntry a, WaitlistEntry b) {
        return a.getStudentId() != null && a.getStudentId().equals(b.getStudentId())
                && a.getSubjectId() != null && a.getSubjectId().equals(b.getSubjectId())
                && a.getSemester() != null && a.getSemester().equals(b.getSemester());
    }

    private void save() throws IOException {
        FileService.saveWaitlist(filePath, listAll());
    }
}
