package crys.sims.view;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;
import crys.sims.service.FileService;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Console UI for grade management: assign/update/delete grade records,
 * GPA + credit summaries, and earned-credit resync.
 * Temporary: talks directly to the in-memory lists + FileService.
 * A GradeController will take over business logic later; this view keeps only I/O.
 *
 * Assigning a grade creates (or updates) the AcademicRecord, removes the
 * matching enrollment, and resyncs the student's cached earnedCredits.
 */
public class GradeView {

    private final List<AcademicRecord> records;
    private final Path recordsPath;
    private final List<Enrollment> enrollments;
    private final Path enrollmentsPath;
    private final List<Student> students;
    private final Path studentsPath;
    private final List<Subject> subjects;
    private final Scanner scanner;

    public GradeView(List<AcademicRecord> records, Path recordsPath,
                     List<Enrollment> enrollments, Path enrollmentsPath,
                     List<Student> students, Path studentsPath,
                     List<Subject> subjects, Scanner scanner) {
        this.records = records;
        this.recordsPath = recordsPath;
        this.enrollments = enrollments;
        this.enrollmentsPath = enrollmentsPath;
        this.students = students;
        this.studentsPath = studentsPath;
        this.subjects = subjects;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("GRADE MANAGEMENT");
            System.out.println("1. View grades by student");
            System.out.println("2. Assign grade");
            System.out.println("3. Update grade");
            System.out.println("4. Delete grade record");
            System.out.println("5. Recalculate all earned credits");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 5);
            switch (choice) {
                case 1: viewByStudent(); break;
                case 2: assignGrade(); break;
                case 3: updateGrade(); break;
                case 4: deleteRecord(); break;
                case 5: recalculateAll(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Actions =====

    private void viewByStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printGrades(stu);
    }

    private void assignGrade() {
        FormatUtils.printHeader("ASSIGN GRADE");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;

        List<Enrollment> matches = enrollmentsOf(stu.getId(), subj.getId());
        String sem;
        if (matches.size() == 1) {
            sem = matches.get(0).getSemester();
            System.out.println("  Enrolled in " + sem + " — grading that enrollment.");
        } else if (matches.size() > 1) {
            System.out.println("  Enrolled in multiple semesters:");
            for (Enrollment e : matches) {
                System.out.println("    " + e.getSemester());
            }
            sem = InputUtils.readRequiredLine(scanner, "Which semester to grade: ");
        } else {
            System.out.println("  No enrollment found — recording directly.");
            sem = InputUtils.readRequiredLine(scanner, "Semester (e.g. 2024-1): ");
        }

        GRADE grade = readGrade();
        AcademicRecord existing = findRecord(stu.getId(), subj.getId(), sem);
        if (existing != null) {
            System.out.println("  Record already has grade " + existing.getGrade() + " — overwriting.");
            existing.setGrade(grade);
        } else {
            records.add(new AcademicRecord(stu.getId(), subj.getId(), sem, grade));
        }
        int removed = removeEnrollments(stu.getId(), subj.getId(), sem);
        syncCredits(stu);
        if (saveAll()) {
            System.out.println("  Graded " + stu.getId() + " " + subj.getCode() + " (" + sem + ") = " + grade
                    + (removed > 0 ? " [" + removed + " enrollment(s) closed]" : ""));
        }
    }

    private void updateGrade() {
        FormatUtils.printHeader("UPDATE GRADE");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<AcademicRecord> matches = recordsOf(stu.getId(), subj.getId());
        if (matches.isEmpty()) {
            System.out.println("  No grade record for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        AcademicRecord target = matches.get(0);
        if (matches.size() > 1) {
            printGrades(stu);
            String sem = InputUtils.readRequiredLine(scanner, "Which semester: ");
            target = findRecord(stu.getId(), subj.getId(), sem);
            if (target == null) {
                System.out.println("  No record in semester: " + sem);
                return;
            }
        }
        System.out.println("  Current grade: " + target.getGrade());
        GRADE grade = readGrade();
        target.setGrade(grade);
        syncCredits(stu);
        if (saveAll()) {
            System.out.println("  Updated " + stu.getId() + " " + subj.getCode()
                    + " (" + target.getSemester() + ") = " + grade);
        }
    }

    private void deleteRecord() {
        FormatUtils.printHeader("DELETE GRADE RECORD");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<AcademicRecord> matches = recordsOf(stu.getId(), subj.getId());
        if (matches.isEmpty()) {
            System.out.println("  No grade record for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        AcademicRecord target = matches.get(0);
        if (matches.size() > 1) {
            printGrades(stu);
            String sem = InputUtils.readRequiredLine(scanner, "Which semester: ");
            target = findRecord(stu.getId(), subj.getId(), sem);
            if (target == null) {
                System.out.println("  No record in semester: " + sem);
                return;
            }
        }
        boolean confirm = InputUtils.readYesNo(scanner,
                "Delete grade " + target.getGrade() + " for " + stu.getId()
                        + " in " + subj.getCode() + " (" + target.getSemester() + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        final AcademicRecord doomed = target;
        records.remove(doomed);
        syncCredits(stu);
        if (saveAll()) {
            System.out.println("  Deleted grade record.");
        }
    }

    private void recalculateAll() {
        FormatUtils.printHeader("RECALCULATE EARNED CREDITS");
        int changed = 0;
        for (Student stu : students) {
            int computed = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
            if (computed != stu.getEarnedCredits()) {
                System.out.println("  " + stu.getId() + ": " + stu.getEarnedCredits() + " -> " + computed);
                stu.setEarnedCredits(computed);
                changed++;
            }
        }
        if (saveStudents()) {
            System.out.println("  Recalculated " + students.size() + " student(s), " + changed + " changed.");
        }
    }

    // ===== Display helpers =====

    private void printGrades(Student stu) {
        FormatUtils.printHeader("GRADES: " + stu.getId() + " " + stu.getFullName());
        String[] headers = {"Code", "Name", "Credits", "Sem", "Grade", "Points"};
        List<String[]> rows = new ArrayList<>();
        for (AcademicRecord r : records) {
            if (!stu.getId().equals(r.getStudentId())) continue;
            Subject subj = findSubjectById(r.getSubjectId());
            rows.add(new String[]{
                    subj == null ? r.getSubjectId() : text(subj.getCode()),
                    subj == null ? "MISSING" : text(subj.getName()),
                    subj == null ? "?" : String.valueOf(subj.getCredits()),
                    text(r.getSemester()),
                    r.getGrade() == null ? "" : r.getGrade().name(),
                    r.getGrade() == null ? "" : String.format(Locale.US, "%.1f", r.getGrade().getGpaValue())
            });
        }
        FormatUtils.printTable(headers, rows);
        double gpa = AcademicUtils.calculateGpa(stu.getId(), records, subjects);
        int computed = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
        System.out.println("  GPA: " + String.format(Locale.US, "%.2f", gpa));
        System.out.println("  Credits computed: " + computed + " | stored: " + stu.getEarnedCredits());
    }

    // ===== Input helpers =====

    private GRADE readGrade() {
        while (true) {
            String raw = InputUtils.readRequiredLine(scanner, "Grade (A+, A, A-, B+, B, B-, C+, C, D, F): ");
            try {
                return ValidationUtils.parseGrade(raw);
            } catch (IllegalArgumentException e) {
                System.out.println("  Invalid grade. Try again.");
            }
        }
    }

    private Subject readSubject() {
        String input = InputUtils.readRequiredLine(scanner, "Subject ID or code: ");
        Subject subj = findSubjectById(input);
        if (subj == null) subj = findSubjectByCode(input);
        if (subj == null) {
            System.out.println("  Not found: " + input);
        }
        return subj;
    }

    // ===== Internal helpers =====

    private Student findStudent(String id) {
        for (Student s : students) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private Subject findSubjectById(String id) {
        for (Subject s : subjects) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private Subject findSubjectByCode(String code) {
        for (Subject s : subjects) {
            if (s.getCode() != null && s.getCode().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }

    private List<Enrollment> enrollmentsOf(String studentId, String subjectId) {
        List<Enrollment> result = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (studentId.equals(e.getStudentId()) && subjectId.equals(e.getSubjectId())) {
                result.add(e);
            }
        }
        return result;
    }

    private List<AcademicRecord> recordsOf(String studentId, String subjectId) {
        List<AcademicRecord> result = new ArrayList<>();
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())) {
                result.add(r);
            }
        }
        return result;
    }

    private AcademicRecord findRecord(String studentId, String subjectId, String semester) {
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())
                    && semester.equalsIgnoreCase(r.getSemester())) {
                return r;
            }
        }
        return null;
    }

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

    private boolean saveAll() {
        try {
            FileService.saveAcademicRecords(recordsPath, records);
            FileService.saveEnrollments(enrollmentsPath, enrollments);
            FileService.saveStudents(studentsPath, students);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    private boolean saveStudents() {
        try {
            FileService.saveStudents(studentsPath, students);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
