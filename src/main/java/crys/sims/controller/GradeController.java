package crys.sims.controller;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;
import crys.sims.service.FileService;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic + persistence for grades. Owns the academic-record list;
 * views call these methods and never touch FileService directly.
 * Assigning a grade creates (or overwrites) the record, closes the matching
 * enrollment, and resyncs the student's cached earnedCredits. Grading does
 * not require an enrollment (transfer/override cases record directly).
 */
public class GradeController {

    public static class CreditChange {
        public final String studentId;
        public final int oldCredits;
        public final int newCredits;

        public CreditChange(String studentId, int oldCredits, int newCredits) {
            this.studentId = studentId;
            this.oldCredits = oldCredits;
            this.newCredits = newCredits;
        }
    }

    public static class RecalculateResult {
        public final int total;
        public final List<CreditChange> changes;

        public RecalculateResult(int total, List<CreditChange> changes) {
            this.total = total;
            this.changes = changes;
        }
    }

    private final List<AcademicRecord> records;
    private final Path recordsPath;
    private final List<Enrollment> enrollments;
    private final Path enrollmentsPath;
    private final List<Student> students;
    private final Path studentsPath;
    private final List<Subject> subjects;

    public GradeController(List<AcademicRecord> records, Path recordsPath,
                           List<Enrollment> enrollments, Path enrollmentsPath,
                           List<Student> students, Path studentsPath,
                           List<Subject> subjects) {
        this.records = records;
        this.recordsPath = recordsPath;
        this.enrollments = enrollments;
        this.enrollmentsPath = enrollmentsPath;
        this.students = students;
        this.studentsPath = studentsPath;
        this.subjects = subjects;
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

    public List<AcademicRecord> getGrades(String studentId) {
        List<AcademicRecord> out = new ArrayList<>();
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId())) out.add(r);
        }
        return out;
    }

    public List<AcademicRecord> getRecords(String studentId, String subjectId) {
        List<AcademicRecord> out = new ArrayList<>();
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())) {
                out.add(r);
            }
        }
        return out;
    }

    public AcademicRecord getRecord(String studentId, String subjectId, String semester) {
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())
                    && semester.equalsIgnoreCase(r.getSemester())) {
                return r;
            }
        }
        return null;
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

    public double getGpa(String id) {
        Student s = requireStudent(id);
        return AcademicUtils.calculateGpa(s.getId(), records, subjects);
    }

    public int getEarnedCredits(String id) {
        Student s = requireStudent(id);
        return AcademicUtils.calculateEarnedCredits(s.getId(), records, subjects);
    }

    public int getStoredCredits(String id) {
        return requireStudent(id).getEarnedCredits();
    }

    // ===== Mutations =====

    public AcademicRecord assignGrade(String studentId, String subjectId, String semester,
                                      GRADE grade) throws IOException {
        Student stu = requireStudent(studentId);
        Subject subj = requireSubject(subjectId);
        String sem = ValidationUtils.requireNonBlank(semester, "semester");
        GRADE g = ValidationUtils.requireNonNull(grade, "grade");
        AcademicRecord existing = getRecord(stu.getId(), subj.getId(), sem);
        AcademicRecord record;
        if (existing != null) {
            existing.setGrade(g);
            record = existing;
        } else {
            record = new AcademicRecord(stu.getId(), subj.getId(), sem, g);
            records.add(record);
        }
        removeEnrollments(stu.getId(), subj.getId(), sem);
        syncCredits(stu);
        saveAll();
        return record;
    }

    public AcademicRecord updateGrade(String studentId, String subjectId, String semester,
                                      GRADE grade) throws IOException {
        Student stu = requireStudent(studentId);
        Subject subj = requireSubject(subjectId);
        String sem = ValidationUtils.requireNonBlank(semester, "semester");
        GRADE g = ValidationUtils.requireNonNull(grade, "grade");
        AcademicRecord record = getRecord(stu.getId(), subj.getId(), sem);
        if (record == null) {
            throw new IllegalArgumentException("No grade record for " + stu.getId()
                    + " in " + subj.getCode() + " (" + sem + ").");
        }
        record.setGrade(g);
        syncCredits(stu);
        saveRecords();
        saveStudents();
        return record;
    }

    public void deleteRecord(String studentId, String subjectId, String semester) throws IOException {
        Student stu = requireStudent(studentId);
        Subject subj = requireSubject(subjectId);
        String sem = ValidationUtils.requireNonBlank(semester, "semester");
        AcademicRecord record = getRecord(stu.getId(), subj.getId(), sem);
        if (record == null) {
            throw new IllegalArgumentException("No grade record for " + stu.getId()
                    + " in " + subj.getCode() + " (" + sem + ").");
        }
        records.remove(record);
        syncCredits(stu);
        saveRecords();
        saveStudents();
    }

    public RecalculateResult recalculateAll() throws IOException {
        List<CreditChange> changes = new ArrayList<>();
        for (Student stu : students) {
            int computed = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
            if (computed != stu.getEarnedCredits()) {
                changes.add(new CreditChange(stu.getId(), stu.getEarnedCredits(), computed));
                stu.setEarnedCredits(computed);
            }
        }
        saveStudents();
        return new RecalculateResult(students.size(), changes);
    }

    // ===== Internals =====

    private int removeEnrollments(String studentId, String subjectId, String semester) {
        List<Enrollment> doomed = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (studentId.equals(e.getStudentId()) && subjectId.equals(e.getSubjectId())
                    && semester.equalsIgnoreCase(e.getSemester())) {
                doomed.add(e);
            }
        }
        enrollments.removeAll(doomed);
        return doomed.size();
    }

    private void syncCredits(Student stu) {
        stu.setEarnedCredits(AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects));
    }

    private Student requireStudent(String id) {
        Student s = findStudent(id);
        if (s == null) {
            throw new IllegalArgumentException("Student not found: " + id);
        }
        return s;
    }

    private Subject requireSubject(String id) {
        Subject s = findSubjectById(id);
        if (s == null) {
            throw new IllegalArgumentException("Subject not found: " + id);
        }
        return s;
    }

    private void saveAll() throws IOException {
        saveRecords();
        FileService.saveEnrollments(enrollmentsPath, enrollments);
        saveStudents();
    }

    private void saveRecords() throws IOException {
        FileService.saveAcademicRecords(recordsPath, records);
    }

    private void saveStudents() throws IOException {
        FileService.saveStudents(studentsPath, students);
    }
}
