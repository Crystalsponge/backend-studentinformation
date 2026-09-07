package crys.sims.view;

import crys.sims.controller.GradeController;
import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Console UI for grade management: assign/update/delete grade records,
 * GPA + credit summaries, and earned-credit resync.
 * I/O only: all business logic and persistence live in GradeController.
 */
public class GradeView {

    private final GradeController controller;
    private final Scanner scanner;

    public GradeView(GradeController controller, Scanner scanner) {
        this.controller = controller;
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

    // ===== Actions (I/O only) =====

    private void viewByStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printGrades(stu);
    }

    private void assignGrade() {
        FormatUtils.printHeader("ASSIGN GRADE");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;

        List<Enrollment> matches = controller.getEnrollments(stu.getId(), subj.getId());
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
        try {
            AcademicRecord record = controller.assignGrade(stu.getId(), subj.getId(), sem, grade);
            System.out.println("  Graded " + record.getStudentId() + " " + subj.getCode()
                    + " (" + record.getSemester() + ") = " + record.getGrade());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void updateGrade() {
        FormatUtils.printHeader("UPDATE GRADE");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<AcademicRecord> matches = controller.getRecords(stu.getId(), subj.getId());
        if (matches.isEmpty()) {
            System.out.println("  No grade record for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        String sem = matches.get(0).getSemester();
        if (matches.size() > 1) {
            printGrades(stu);
            sem = InputUtils.readRequiredLine(scanner, "Which semester: ");
        }
        AcademicRecord current = controller.getRecord(stu.getId(), subj.getId(), sem);
        if (current == null) {
            System.out.println("  No record in semester: " + sem);
            return;
        }
        System.out.println("  Current grade: " + current.getGrade());
        GRADE grade = readGrade();
        try {
            AcademicRecord updated = controller.updateGrade(stu.getId(), subj.getId(), sem, grade);
            System.out.println("  Updated " + updated.getStudentId() + " " + subj.getCode()
                    + " (" + updated.getSemester() + ") = " + updated.getGrade());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void deleteRecord() {
        FormatUtils.printHeader("DELETE GRADE RECORD");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<AcademicRecord> matches = controller.getRecords(stu.getId(), subj.getId());
        if (matches.isEmpty()) {
            System.out.println("  No grade record for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        AcademicRecord target = matches.get(0);
        String sem = target.getSemester();
        if (matches.size() > 1) {
            printGrades(stu);
            sem = InputUtils.readRequiredLine(scanner, "Which semester: ");
            target = controller.getRecord(stu.getId(), subj.getId(), sem);
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
        try {
            controller.deleteRecord(stu.getId(), subj.getId(), target.getSemester());
            System.out.println("  Deleted grade record.");
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void recalculateAll() {
        FormatUtils.printHeader("RECALCULATE EARNED CREDITS");
        try {
            GradeController.RecalculateResult result = controller.recalculateAll();
            for (GradeController.CreditChange change : result.changes) {
                System.out.println("  " + change.studentId + ": " + change.oldCredits + " -> " + change.newCredits);
            }
            System.out.println("  Recalculated " + result.total + " student(s), " + result.changes.size() + " changed.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    // ===== Display helpers =====

    private void printGrades(Student stu) {
        FormatUtils.printHeader("GRADES: " + stu.getId() + " " + stu.getFullName());
        String[] headers = {"Code", "Name", "Credits", "Sem", "Grade", "Points"};
        List<String[]> rows = new ArrayList<>();
        for (AcademicRecord r : controller.getGrades(stu.getId())) {
            Subject subj = controller.findSubjectById(r.getSubjectId());
            rows.add(new String[]{
                    subj == null ? r.getSubjectId() : TextUtils.orEmpty(subj.getCode()),
                    subj == null ? "MISSING" : TextUtils.orEmpty(subj.getName()),
                    subj == null ? "?" : String.valueOf(subj.getCredits()),
                    TextUtils.orEmpty(r.getSemester()),
                    r.getGrade() == null ? "" : r.getGrade().name(),
                    r.getGrade() == null ? "" : String.format(Locale.US, "%.1f", r.getGrade().getGpaValue())
            });
        }
        FormatUtils.printTable(headers, rows);
        System.out.println("  GPA: " + String.format(Locale.US, "%.2f", controller.getGpa(stu.getId())));
        System.out.println("  Credits computed: " + controller.getEarnedCredits(stu.getId())
                + " | stored: " + controller.getStoredCredits(stu.getId()));
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
        Subject subj = controller.findSubject(input);
        if (subj == null) {
            System.out.println("  Not found: " + input);
        }
        return subj;
    }
}
