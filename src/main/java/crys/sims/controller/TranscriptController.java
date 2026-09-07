package crys.sims.controller;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.TextUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only business logic for transcripts and graduation progress.
 * No file paths, no mutations — views format the returned DTOs.
 * Subject suggestions delegate to EnrollmentController (shared rules).
 */
public class TranscriptController {

    public static class SemesterBlock {
        public final String semester;
        public final List<AcademicRecord> records;

        public SemesterBlock(String semester, List<AcademicRecord> records) {
            this.semester = semester;
            this.records = records;
        }
    }

    public static class Transcript {
        public final Student student;
        public final List<SemesterBlock> blocks;
        public final List<Enrollment> currentEnrollments;
        public final double gpa;
        public final int earnedCredits;

        public Transcript(Student student, List<SemesterBlock> blocks,
                          List<Enrollment> currentEnrollments, double gpa, int earnedCredits) {
            this.student = student;
            this.blocks = blocks;
            this.currentEnrollments = currentEnrollments;
            this.gpa = gpa;
            this.earnedCredits = earnedCredits;
        }
    }

    public static class RequiredSubject {
        public final Subject subject;
        public final String rawId;
        public final GRADE bestGrade;
        public final boolean done;

        public RequiredSubject(Subject subject, String rawId, GRADE bestGrade, boolean done) {
            this.subject = subject;
            this.rawId = rawId;
            this.bestGrade = bestGrade;
            this.done = done;
        }
    }

    public static class GraduationResult {
        public final int earned;
        public final int required;
        public final int remaining;
        public final int pct;
        public final boolean eligible;
        public final List<RequiredSubject> subjects;

        public GraduationResult(int earned, int required, int remaining, int pct,
                                boolean eligible, List<RequiredSubject> subjects) {
            this.earned = earned;
            this.required = required;
            this.remaining = remaining;
            this.pct = pct;
            this.eligible = eligible;
            this.subjects = subjects;
        }
    }

    private final List<Student> students;
    private final List<Subject> subjects;
    private final List<AcademicRecord> records;
    private final List<Enrollment> enrollments;
    private final EnrollmentController enrollmentController;

    public TranscriptController(List<Student> students, List<Subject> subjects,
                                List<AcademicRecord> records, List<Enrollment> enrollments,
                                EnrollmentController enrollmentController) {
        this.students = students;
        this.subjects = subjects;
        this.records = records;
        this.enrollments = enrollments;
        this.enrollmentController = enrollmentController;
    }

    // ===== Lookups =====

    public Student findStudent(String id) {
        if (id == null) return null;
        for (Student s : students) {
            if (id.equalsIgnoreCase(s.getId())) return s;
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

    public Subject findSubjectByCode(String code) {
        if (code == null) return null;
        for (Subject s : subjects) {
            if (code.equalsIgnoreCase(s.getCode())) return s;
        }
        return null;
    }

    // ===== Transcript =====

    public Transcript getTranscript(String id) {
        Student stu = requireStudent(id);
        Map<String, List<AcademicRecord>> bySemester = new TreeMap<>();
        for (AcademicRecord r : records) {
            if (stu.getId().equals(r.getStudentId())) {
                bySemester.computeIfAbsent(TextUtils.orEmpty(r.getSemester()), k -> new ArrayList<>()).add(r);
            }
        }
        List<SemesterBlock> blocks = new ArrayList<>();
        for (Map.Entry<String, List<AcademicRecord>> entry : bySemester.entrySet()) {
            List<AcademicRecord> block = new ArrayList<>(entry.getValue());
            block.sort(Comparator.comparing(r -> {
                Subject subj = findSubjectById(r.getSubjectId());
                return subj == null ? r.getSubjectId() : TextUtils.orEmpty(subj.getCode());
            }));
            blocks.add(new SemesterBlock(entry.getKey(), block));
        }
        List<Enrollment> current = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId())) current.add(e);
        }
        return new Transcript(stu, blocks, current,
                AcademicUtils.calculateGpa(stu.getId(), records, subjects),
                AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects));
    }

    // ===== Graduation =====

    public GraduationResult getGraduationProgress(String id, int requiredCredits,
                                                  List<String> requiredSubjectIds) {
        Student stu = requireStudent(id);
        if (requiredCredits < 0) {
            throw new IllegalArgumentException("Required credits must be >= 0: " + requiredCredits);
        }
        List<String> required = requiredSubjectIds == null ? new ArrayList<>() : requiredSubjectIds;
        int earned = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
        int remaining = Math.max(0, requiredCredits - earned);
        int pct = requiredCredits <= 0 ? 100 : Math.min(100, earned * 100 / requiredCredits);
        List<RequiredSubject> statuses = new ArrayList<>();
        for (String reqId : required) {
            if (reqId == null) {
                throw new IllegalArgumentException("Required subject IDs must not contain null.");
            }
            Subject subj = findSubjectById(reqId);
            if (subj == null) subj = findSubjectByCode(reqId);
            GRADE best = bestGrade(stu.getId(), subj == null ? reqId : subj.getId());
            boolean done = best != null && best != GRADE.F;
            statuses.add(new RequiredSubject(subj, reqId, best, done));
        }
        boolean eligible = remaining == 0;
        for (RequiredSubject rs : statuses) {
            if (!rs.done) {
                eligible = false;
                break;
            }
        }
        return new GraduationResult(earned, requiredCredits, remaining, pct, eligible, statuses);
    }

    // ===== Suggestions (delegated) =====

    public EnrollmentController.SuggestionResult suggestSubjects(String studentId, String semester) {
        return enrollmentController.suggestSubjects(studentId, semester);
    }

    // ===== Internals =====

    private GRADE bestGrade(String studentId, String subjectId) {
        GRADE best = null;
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())
                    && r.getGrade() != null) {
                if (best == null || r.getGrade().getGpaValue() > best.getGpaValue()) {
                    best = r.getGrade();
                }
            }
        }
        return best;
    }

    private Student requireStudent(String id) {
        Student s = findStudent(id);
        if (s == null) {
            throw new IllegalArgumentException("Student not found: " + id);
        }
        return s;
    }
}
