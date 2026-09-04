package crys.sims.view;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Console UI for academic transcripts, graduation progress, and subject suggestions.
 * Read-only: never mutates lists or files.
 * A TranscriptController will take over the logic later; this view keeps only I/O.
 */
public class TranscriptView {

    private final List<Student> students;
    private final List<Subject> subjects;
    private final List<AcademicRecord> records;
    private final List<Enrollment> enrollments;
    private final Scanner scanner;

    public TranscriptView(List<Student> students, List<Subject> subjects,
                          List<AcademicRecord> records, List<Enrollment> enrollments,
                          Scanner scanner) {
        this.students = students;
        this.subjects = subjects;
        this.records = records;
        this.enrollments = enrollments;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("TRANSCRIPT & PROGRESS");
            System.out.println("1. Academic transcript");
            System.out.println("2. Graduation progress report");
            System.out.println("3. Suggest available subjects");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 3);
            switch (choice) {
                case 1: transcript(); break;
                case 2: graduationProgress(); break;
                case 3: suggestSubjects(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Actions =====

    private void transcript() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        FormatUtils.printHeader("ACADEMIC TRANSCRIPT");
        System.out.println("Student:    " + stu.getId() + " " + stu.getFullName());
        System.out.println("Program:    " + text(stu.getProgram()) + " | Department: " + text(stu.getDepartment()));
        System.out.println("Year level: " + stu.getYearLevel() + " | Semester: " + text(stu.getCurrentSemester()));
        System.out.println();

        Map<String, List<AcademicRecord>> bySemester = new TreeMap<>();
        for (AcademicRecord r : records) {
            if (stu.getId().equals(r.getStudentId())) {
                bySemester.computeIfAbsent(text(r.getSemester()), k -> new ArrayList<>()).add(r);
            }
        }
        if (bySemester.isEmpty()) {
            System.out.println("  (no completed subjects)");
        }
        for (Map.Entry<String, List<AcademicRecord>> entry : bySemester.entrySet()) {
            String semLabel = entry.getKey().isEmpty() ? "(no semester)" : entry.getKey();
            System.out.println("Semester " + semLabel + ":");
            entry.getValue().sort(Comparator.comparing(r -> subjectCode(r.getSubjectId())));
            for (AcademicRecord r : entry.getValue()) {
                Subject subj = findSubjectById(r.getSubjectId());
                String code = subj == null ? r.getSubjectId() : text(subj.getCode());
                String name = subj == null ? "MISSING" : text(subj.getName());
                String credits = subj == null ? "?" : String.valueOf(subj.getCredits());
                String grade = r.getGrade() == null ? "-" : r.getGrade().name();
                String points = r.getGrade() == null ? "-" : String.format(Locale.US, "%.1f", r.getGrade().getGpaValue());
                System.out.println("  " + code + " " + name + " (" + credits + " cr) — " + grade + " (" + points + ")");
            }
        }
        System.out.println();
        System.out.println("Currently enrolled:");
        boolean anyEnrolled = false;
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId())) {
                anyEnrolled = true;
                Subject subj = findSubjectById(e.getSubjectId());
                String code = subj == null ? e.getSubjectId() : text(subj.getCode());
                String name = subj == null ? "MISSING" : text(subj.getName());
                System.out.println("  " + code + " " + name + " (" + text(e.getSemester()) + ")");
            }
        }
        if (!anyEnrolled) {
            System.out.println("  (none)");
        }
        System.out.println();
        double gpa = AcademicUtils.calculateGpa(stu.getId(), records, subjects);
        int earned = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
        System.out.println("GPA: " + String.format(Locale.US, "%.2f", gpa));
        System.out.println("Earned credits: " + earned);
    }

    private void graduationProgress() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        int requiredCredits = InputUtils.readOptionalInt(scanner, "Required credits", 120);
        String rawRequired = InputUtils.readLine(scanner, "Required subject IDs (comma-separated, empty = none): ");
        List<String> requiredIds = new ArrayList<>();
        if (!rawRequired.isEmpty()) {
            for (String part : rawRequired.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) requiredIds.add(trimmed);
            }
        }

        FormatUtils.printHeader("GRADUATION PROGRESS: " + stu.getId() + " " + stu.getFullName());
        int earned = AcademicUtils.calculateEarnedCredits(stu.getId(), records, subjects);
        int remaining = Math.max(0, requiredCredits - earned);
        int pct = requiredCredits <= 0 ? 100 : Math.min(100, earned * 100 / requiredCredits);
        System.out.println("Credits: " + earned + " / " + requiredCredits
                + " (remaining " + remaining + ", " + pct + "%)");

        List<String> missingSubjects = new ArrayList<>();
        if (!requiredIds.isEmpty()) {
            System.out.println("Required subjects:");
            for (String reqId : requiredIds) {
                Subject subj = findSubjectById(reqId);
                if (subj == null) subj = findSubjectByCode(reqId);
                String label = subj == null ? reqId + " (unknown ID)"
                        : subj.getCode() + " " + text(subj.getName());
                GRADE best = bestGrade(stu.getId(), subj == null ? reqId : subj.getId());
                if (best != null && best != GRADE.F) {
                    System.out.println("  [x] " + label + " — " + best);
                } else {
                    System.out.println("  [ ] " + label + " — missing");
                    missingSubjects.add(label);
                }
            }
        }

        boolean eligible = remaining == 0 && missingSubjects.isEmpty();
        System.out.println();
        System.out.println(eligible ? "Eligible for graduation: YES" : "Eligible for graduation: NO");
        if (!eligible) {
            if (remaining > 0) {
                System.out.println("  - Needs " + remaining + " more credit(s).");
            }
            for (String label : missingSubjects) {
                System.out.println("  - Missing required subject: " + label + ".");
            }
        }
    }

    private void suggestSubjects() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        String sem = text(stu.getCurrentSemester());
        if (sem.isEmpty()) {
            sem = InputUtils.readRequiredLine(scanner, "Target semester (e.g. 2024-1): ");
        } else {
            System.out.println("  Target semester: " + sem + " (student's current)");
        }

        FormatUtils.printHeader("SUGGESTED SUBJECTS FOR " + stu.getId() + " (" + sem + ")");
        String[] headers = {"Code", "Name", "Cr", "Prereqs", "Enr/Cap"};
        List<String[]> available = new ArrayList<>();
        List<String[]> excluded = new ArrayList<>();
        for (Subject subj : subjects) {
            String reason = exclusionReason(stu, subj, sem);
            String prereqs = (subj.getPrerequisiteIds() == null || subj.getPrerequisiteIds().isEmpty())
                    ? "-" : String.join(",", subj.getPrerequisiteIds());
            if (reason == null) {
                available.add(new String[]{
                        text(subj.getCode()), text(subj.getName()),
                        String.valueOf(subj.getCredits()), prereqs,
                        enrollmentCount(subj.getId(), sem) + "/" + subj.getMaxCapacity()
                });
            } else {
                excluded.add(new String[]{text(subj.getCode()), reason});
            }
        }
        available.sort(Comparator.comparing(row -> row[0], String.CASE_INSENSITIVE_ORDER));
        System.out.println("-- Available --");
        FormatUtils.printTable(headers, available);
        if (!excluded.isEmpty()) {
            excluded.sort(Comparator.comparing(row -> row[0], String.CASE_INSENSITIVE_ORDER));
            System.out.println("-- Excluded --");
            FormatUtils.printTable(new String[]{"Code", "Reason"}, excluded);
        }
    }

    // ===== Suggestion rules =====

    private String exclusionReason(Student stu, Subject subj, String semester) {
        for (AcademicRecord r : records) {
            if (stu.getId().equals(r.getStudentId()) && subj.getId().equals(r.getSubjectId())
                    && r.getGrade() != null && r.getGrade() != GRADE.F) {
                return "completed (" + r.getGrade() + ")";
            }
        }
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId()) && subj.getId().equals(e.getSubjectId())) {
                return "already enrolled (" + text(e.getSemester()) + ")";
            }
        }
        String stuDept = text(stu.getDepartment());
        String subjDept = text(subj.getDepartment());
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
        int inSemester = enrollmentCount(subj.getId(), semester);
        if (inSemester + 1 > subj.getMaxCapacity()) {
            return "full (" + inSemester + "/" + subj.getMaxCapacity() + ")";
        }
        if (!ValidationUtils.isOfferedIn(subj, semester)) {
            return "offered in '" + text(subj.getSemesterOffered()) + "'";
        }
        return null;
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

    private String subjectCode(String subjectId) {
        Subject subj = findSubjectById(subjectId);
        return subj == null ? subjectId : text(subj.getCode());
    }

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

    private int enrollmentCount(String subjectId, String semester) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())
                    && semester.equals(e.getSemester())) {
                n++;
            }
        }
        return n;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
