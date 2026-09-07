package crys.sims.controller;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.model.enums.GRADE;
import crys.sims.service.FileService;
import crys.sims.service.UndoRedoService;
import crys.sims.service.WaitlistService;
import crys.sims.utils.TextUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic + persistence for course registration. Owns the in-memory
 * enrollment list plus the UndoRedoService and WaitlistService; views call
 * these methods and never touch FileService directly. Every mutation
 * validates, snapshots (for undo), then saves. Undo covers enrollment
 * changes only — a waitlist dequeue on admit is not undone.
 */
public class EnrollmentController {

    public static class SemesterStats {
        public final Map<String, Integer> bySubject;
        public final Map<String, Integer> byDepartment;

        public SemesterStats(Map<String, Integer> bySubject, Map<String, Integer> byDepartment) {
            this.bySubject = bySubject;
            this.byDepartment = byDepartment;
        }
    }

    public static class AvailableSubject {
        public final Subject subject;
        public final int enrolledInSemester;

        public AvailableSubject(Subject subject, int enrolledInSemester) {
            this.subject = subject;
            this.enrolledInSemester = enrolledInSemester;
        }
    }

    public static class ExcludedSubject {
        public final Subject subject;
        public final String reason;

        public ExcludedSubject(Subject subject, String reason) {
            this.subject = subject;
            this.reason = reason;
        }
    }

    public static class SuggestionResult {
        public final List<AvailableSubject> available;
        public final List<ExcludedSubject> excluded;

        public SuggestionResult(List<AvailableSubject> available, List<ExcludedSubject> excluded) {
            this.available = available;
            this.excluded = excluded;
        }
    }

    private final List<Enrollment> enrollments;
    private final Path filePath;
    private final List<Student> students;
    private final List<Subject> subjects;
    private final List<AcademicRecord> records;
    private final WaitlistService waitlist;
    private final UndoRedoService history;

    public EnrollmentController(List<Enrollment> enrollments, Path filePath,
                                List<Student> students, List<Subject> subjects,
                                List<AcademicRecord> records, WaitlistService waitlist) {
        this.enrollments = enrollments;
        this.filePath = filePath;
        this.students = students;
        this.subjects = subjects;
        this.records = records;
        this.waitlist = waitlist;
        this.history = new UndoRedoService();
    }

    // ===== Lookups =====

    public Student findStudent(String id) {
        if (id == null) return null;
        for (Student s : students) {
            if (id.equalsIgnoreCase(s.getId())) return s;
        }
        return null;
    }

    public Subject findSubject(String idOrCode) {
        if (idOrCode == null) return null;
        for (Subject s : subjects) {
            if (idOrCode.equalsIgnoreCase(s.getId())) return s;
        }
        for (Subject s : subjects) {
            if (idOrCode.equalsIgnoreCase(s.getCode())) return s;
        }
        return null;
    }

    public Subject findSubjectById(String id) {
        if (id == null) return null;
        for (Subject s : subjects) {
            if (id.equalsIgnoreCase(s.getId())) return s;
        }
        return null;
    }

    // ===== Reads =====

    public List<Enrollment> getAll() {
        return new ArrayList<>(enrollments);
    }

    public List<Enrollment> getByStudent(String studentId) {
        List<Enrollment> out = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (studentId.equals(e.getStudentId())) out.add(e);
        }
        return out;
    }

    public List<Enrollment> getRoster(String subjectId) {
        List<Enrollment> out = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (subjectId.equals(e.getSubjectId())) out.add(e);
        }
        return out;
    }

    public List<Enrollment> getEnrollments(String studentId, String subjectId) {
        List<Enrollment> out = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (studentId.equals(e.getStudentId()) && subjectId.equals(e.getSubjectId())) {
                out.add(e);
            }
        }
        return out;
    }

    public int getEnrollmentCount(String subjectId, String semester) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())
                    && semester.equals(e.getSemester())) {
                n++;
            }
        }
        return n;
    }

    public SemesterStats getSemesterStats(String semester) {
        String sem = semester == null ? "" : semester.trim();
        Map<String, Integer> bySubject = new HashMap<>();
        for (Enrollment e : enrollments) {
            if (sem.isEmpty() || sem.equalsIgnoreCase(e.getSemester())) {
                bySubject.put(e.getSubjectId(), bySubject.getOrDefault(e.getSubjectId(), 0) + 1);
            }
        }
        Map<String, Integer> byDept = new HashMap<>();
        for (Map.Entry<String, Integer> entry : bySubject.entrySet()) {
            Subject subj = findSubjectById(entry.getKey());
            String dept = (subj == null || subj.getDepartment() == null) ? "(unknown)" : subj.getDepartment();
            byDept.put(dept, byDept.getOrDefault(dept, 0) + entry.getValue());
        }
        return new SemesterStats(bySubject, byDept);
    }

    // ===== Mutations =====

    public Enrollment register(String studentId, String subjectId, String semester) throws IOException {
        ValidationUtils.validateRegistration(studentId, subjectId, semester,
                students, subjects, enrollments, records);
        history.snapshot(enrollments);
        Enrollment e = new Enrollment(studentId, subjectId, semester.trim(), LocalDate.now());
        enrollments.add(e);
        save();
        return e;
    }

    public Enrollment drop(String studentId, String subjectId, String semester) throws IOException {
        List<Enrollment> matches = getEnrollments(studentId, subjectId);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No enrollment found for " + studentId + " in " + subjectId + ".");
        }
        Enrollment target = matches.get(0);
        if (semester == null || semester.trim().isEmpty()) {
            if (matches.size() > 1) {
                List<String> sems = new ArrayList<>();
                for (Enrollment e : matches) sems.add(e.getSemester());
                throw new IllegalArgumentException("Enrolled in multiple semesters ("
                        + String.join(",", sems) + ") — pick one.");
            }
        } else {
            target = null;
            for (Enrollment e : matches) {
                if (semester.equalsIgnoreCase(e.getSemester())) {
                    target = e;
                    break;
                }
            }
            if (target == null) {
                throw new IllegalArgumentException("No enrollment in semester: " + semester);
            }
        }
        history.snapshot(enrollments);
        enrollments.remove(target);
        save();
        return target;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() throws IOException {
        if (!history.canUndo()) {
            throw new IllegalStateException("Nothing to undo.");
        }
        List<Enrollment> prev = history.undo(enrollments);
        enrollments.clear();
        enrollments.addAll(prev);
        save();
    }

    public void redo() throws IOException {
        if (!history.canRedo()) {
            throw new IllegalStateException("Nothing to redo.");
        }
        List<Enrollment> next = history.redo(enrollments);
        enrollments.clear();
        enrollments.addAll(next);
        save();
    }

    // ===== Waitlist =====

    public WaitlistEntry joinWaitlist(String studentId, String subjectId, String semester) throws IOException {
        ValidationUtils.validateWaitlistJoin(studentId, subjectId, semester,
                students, subjects, enrollments, records, waitlist.listAll());
        WaitlistEntry entry = new WaitlistEntry(studentId, subjectId, semester.trim(), LocalDate.now());
        waitlist.join(entry);
        return entry;
    }

    public Enrollment admitHead(String subjectId, String semester) throws IOException {
        String sem = semester == null ? "" : semester.trim();
        WaitlistEntry head = waitlist.peekHead(subjectId, sem);
        if (head == null) {
            throw new IllegalArgumentException("No one waiting for " + subjectId + " (" + sem + ").");
        }
        try {
            ValidationUtils.validateRegistration(head.getStudentId(), head.getSubjectId(), head.getSemester(),
                    students, subjects, enrollments, records);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot admit " + head.getStudentId() + ": "
                    + e.getMessage() + " — kept on waitlist.");
        }
        history.snapshot(enrollments);
        Enrollment e = new Enrollment(head.getStudentId(), head.getSubjectId(),
                head.getSemester(), LocalDate.now());
        enrollments.add(e);
        save();
        waitlist.admit(subjectId, sem);
        return e;
    }

    public void leaveWaitlist(String studentId, String subjectId, String semester) throws IOException {
        String sem = semester == null ? "" : semester.trim();
        boolean removed = waitlist.leave(studentId, subjectId, sem);
        if (!removed) {
            throw new IllegalArgumentException("No matching waitlist entry.");
        }
    }

    public List<WaitlistEntry> getWaitlist() {
        return waitlist.listAll();
    }

    public WaitlistEntry peekWaitlistHead(String subjectId, String semester) {
        return waitlist.peekHead(subjectId, semester);
    }

    public int getWaitlistSize(String subjectId, String semester) {
        return waitlist.size(subjectId, semester);
    }

    // ===== Suggestions =====

    public SuggestionResult suggestSubjects(String studentId, String semester) {
        Student stu = findStudent(studentId);
        if (stu == null) {
            throw new IllegalArgumentException("Student not found: " + studentId);
        }
        if (!stu.isActive()) {
            throw new IllegalArgumentException("Student " + studentId + " is not active.");
        }
        if (semester == null || semester.trim().isEmpty()) {
            throw new IllegalArgumentException("Semester must not be blank.");
        }
        String sem = semester.trim();
        List<AvailableSubject> available = new ArrayList<>();
        List<ExcludedSubject> excluded = new ArrayList<>();
        for (Subject subj : subjects) {
            String reason = exclusionReason(stu, subj, sem);
            if (reason == null) {
                available.add(new AvailableSubject(subj, getEnrollmentCount(subj.getId(), sem)));
            } else {
                excluded.add(new ExcludedSubject(subj, reason));
            }
        }
        available.sort(Comparator.comparing(
                a -> a.subject.getCode() == null ? "" : a.subject.getCode(), String.CASE_INSENSITIVE_ORDER));
        excluded.sort(Comparator.comparing(
                e -> e.subject.getCode() == null ? "" : e.subject.getCode(), String.CASE_INSENSITIVE_ORDER));
        return new SuggestionResult(available, excluded);
    }

    private String exclusionReason(Student stu, Subject subj, String semester) {
        for (AcademicRecord r : records) {
            if (stu.getId().equals(r.getStudentId()) && subj.getId().equals(r.getSubjectId())
                    && r.getGrade() != null && r.getGrade() != GRADE.F) {
                return "completed (" + r.getGrade() + ")";
            }
        }
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId()) && subj.getId().equals(e.getSubjectId())) {
                return "already enrolled (" + TextUtils.orEmpty(e.getSemester()) + ")";
            }
        }
        String stuDept = TextUtils.orEmpty(stu.getDepartment());
        String subjDept = TextUtils.orEmpty(subj.getDepartment());
        if (!stuDept.isEmpty() && !subjDept.isEmpty() && !stuDept.equals(subjDept)) {
            return "different department (" + subjDept + ")";
        }
        List<String> missing = new ArrayList<>();
        if (subj.getPrerequisiteIds() != null) {
            for (String pre : subj.getPrerequisiteIds()) {
                boolean done = false;
                for (AcademicRecord r : records) {
                    if (stu.getId().equals(r.getStudentId()) && pre.equals(r.getSubjectId())
                            && r.getGrade() != null && r.getGrade() != GRADE.F) {
                        done = true;
                        break;
                    }
                }
                if (!done) missing.add(pre);
            }
        }
        if (!missing.isEmpty()) {
            return "missing prerequisites: " + String.join(",", missing);
        }
        int inSemester = getEnrollmentCount(subj.getId(), semester);
        if (inSemester + 1 > subj.getMaxCapacity()) {
            return "full (" + inSemester + "/" + subj.getMaxCapacity() + ")";
        }
        if (!ValidationUtils.isOfferedIn(subj, semester)) {
            return "offered in '" + TextUtils.orEmpty(subj.getSemesterOffered()) + "'";
        }
        return null;
    }

    // ===== Internals =====

    private void save() throws IOException {
        FileService.saveEnrollments(filePath, enrollments);
    }
}
