package crys.sims.service;

import crys.sims.datastructure.ArrayStack;
import crys.sims.model.Enrollment;

import java.util.ArrayList;
import java.util.List;

/**
 * Undo/redo for course registration. Snapshot-based: the owner pushes the
 * pre-mutation state before every register/drop; undo/redo swap whole lists.
 * Backed by ArrayStack (custom array-based stacks, no java.util Deque).
 * Entries are deep-copied so later mutations cannot corrupt history.
 * Callers must mutate the live list in place (clear + addAll), never reassign
 * it, because other views hold the same reference.
 */
public class UndoRedoService {

    private final ArrayStack<List<Enrollment>> undoStack = new ArrayStack<>();
    private final ArrayStack<List<Enrollment>> redoStack = new ArrayStack<>();

    public void snapshot(List<Enrollment> current) {
        undoStack.push(copy(current));
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public List<Enrollment> undo(List<Enrollment> current) {
        if (!canUndo()) {
            throw new IllegalStateException("Nothing to undo.");
        }
        redoStack.push(copy(current));
        return undoStack.pop();
    }

    public List<Enrollment> redo(List<Enrollment> current) {
        if (!canRedo()) {
            throw new IllegalStateException("Nothing to redo.");
        }
        undoStack.push(copy(current));
        return redoStack.pop();
    }

    private static List<Enrollment> copy(List<Enrollment> src) {
        List<Enrollment> out = new ArrayList<>(src.size());
        for (Enrollment e : src) {
            out.add(new Enrollment(e.getStudentId(), e.getSubjectId(),
                    e.getSemester(), e.getEnrollmentDate()));
        }
        return out;
    }
}
