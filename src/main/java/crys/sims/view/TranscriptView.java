package crys.sims.view;

import crys.sims.controller.EnrollmentController;
import crys.sims.controller.TranscriptController;
import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Console UI for academic transcripts, graduation progress, and subject suggestions.
 * Read-only: never mutates lists or files. Formats the DTOs returned by
 * TranscriptController; suggestions delegate through it to EnrollmentController.
 */
public class TranscriptView {

    private final TranscriptController controller;
    private final Scanner scanner;

    public TranscriptView(TranscriptController controller, Scanner scanner) {
        this.controller = controller;
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

    // ===== Actions (I/O only) =====

    private void transcript() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student probe = controller.findStudent(id);
        if (probe == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        TranscriptController.Transcript t = controller.getTranscript(probe.getId());
        FormatUtils.printHeader("ACADEMIC TRANSCRIPT");
        System.out.println("Student:    " + t.student.getId() + " " + t.student.getFullName());
        System.out.println("Program:    " + TextUtils.orEmpty(t.student.getProgram()) + " | Department: " + TextUtils.orEmpty(t.student.getDepartment()));
        System.out.println("Year level: " + t.student.getYearLevel() + " | Semester: " + TextUtils.orEmpty(t.student.getCurrentSemester()));
        System.out.println();

        if (t.blocks.isEmpty()) {
            System.out.println("  (no completed subjects)");
        }
        for (TranscriptController.SemesterBlock block : t.blocks) {
            String semLabel = block.semester.isEmpty() ? "(no semester)" : block.semester;
            System.out.println("Semester " + semLabel + ":");
            for (AcademicRecord r : block.records) {
                Subject subj = controller.findSubjectById(r.getSubjectId());
                String code = subj == null ? r.getSubjectId() : TextUtils.orEmpty(subj.getCode());
                String name = subj == null ? "MISSING" : TextUtils.orEmpty(subj.getName());
                String credits = subj == null ? "?" : String.valueOf(subj.getCredits());
                String grade = r.getGrade() == null ? "-" : r.getGrade().name();
                String points = r.getGrade() == null ? "-" : String.format(Locale.US, "%.1f", r.getGrade().getGpaValue());
                System.out.println("  " + code + " " + name + " (" + credits + " cr) — " + grade + " (" + points + ")");
            }
        }
        System.out.println();
        System.out.println("Currently enrolled:");
        if (t.currentEnrollments.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Enrollment e : t.currentEnrollments) {
            Subject subj = controller.findSubjectById(e.getSubjectId());
            String code = subj == null ? e.getSubjectId() : TextUtils.orEmpty(subj.getCode());
            String name = subj == null ? "MISSING" : TextUtils.orEmpty(subj.getName());
            System.out.println("  " + code + " " + name + " (" + TextUtils.orEmpty(e.getSemester()) + ")");
        }
        System.out.println();
        System.out.println("GPA: " + String.format(Locale.US, "%.2f", t.gpa));
        System.out.println("Earned credits: " + t.earnedCredits);
    }

    private void graduationProgress() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student probe = controller.findStudent(id);
        if (probe == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        int requiredCredits = InputUtils.readOptionalInt(scanner, "Required credits", 120, 0);
        String rawRequired = InputUtils.readLine(scanner, "Required subject IDs (comma-separated, empty = none): ");
        List<String> requiredIds = new ArrayList<>();
        if (!rawRequired.isEmpty()) {
            for (String part : rawRequired.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) requiredIds.add(trimmed);
            }
        }

        TranscriptController.GraduationResult g;
        try {
            g = controller.getGraduationProgress(probe.getId(), requiredCredits, requiredIds);
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
            return;
        }
        FormatUtils.printHeader("GRADUATION PROGRESS: " + probe.getId() + " " + probe.getFullName());
        System.out.println("Credits: " + g.earned + " / " + g.required
                + " (remaining " + g.remaining + ", " + g.pct + "%)");

        List<String> missingLabels = new ArrayList<>();
        if (!g.subjects.isEmpty()) {
            System.out.println("Required subjects:");
            for (TranscriptController.RequiredSubject rs : g.subjects) {
                String label = rs.subject == null ? rs.rawId + " (unknown ID)"
                        : rs.subject.getCode() + " " + TextUtils.orEmpty(rs.subject.getName());
                if (rs.done) {
                    System.out.println("  [x] " + label + " — " + rs.bestGrade);
                } else {
                    System.out.println("  [ ] " + label + " — missing");
                    missingLabels.add(label);
                }
            }
        }

        System.out.println();
        System.out.println(g.eligible ? "Eligible for graduation: YES" : "Eligible for graduation: NO");
        if (!g.eligible) {
            if (g.remaining > 0) {
                System.out.println("  - Needs " + g.remaining + " more credit(s).");
            }
            for (String label : missingLabels) {
                System.out.println("  - Missing required subject: " + label + ".");
            }
        }
    }

    private void suggestSubjects() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student probe = controller.findStudent(id);
        if (probe == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        String sem = TextUtils.orEmpty(probe.getCurrentSemester());
        if (sem.isEmpty()) {
            sem = InputUtils.readRequiredLine(scanner, "Target semester (e.g. 2024-1): ");
        } else {
            System.out.println("  Target semester: " + sem + " (student's current)");
        }

        FormatUtils.printHeader("SUGGESTED SUBJECTS FOR " + probe.getId() + " (" + sem + ")");
        EnrollmentController.SuggestionResult result;
        try {
            result = controller.suggestSubjects(probe.getId(), sem);
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
            return;
        }
        String[] headers = {"Code", "Name", "Cr", "Prereqs", "Enr/Cap"};
        List<String[]> available = new ArrayList<>();
        for (EnrollmentController.AvailableSubject a : result.available) {
            Subject subj = a.subject;
            String prereqs = (subj.getPrerequisiteIds() == null || subj.getPrerequisiteIds().isEmpty())
                    ? "-" : String.join(",", subj.getPrerequisiteIds());
            available.add(new String[]{
                    TextUtils.orEmpty(subj.getCode()), TextUtils.orEmpty(subj.getName()),
                    String.valueOf(subj.getCredits()), prereqs,
                    a.enrolledInSemester + "/" + subj.getMaxCapacity()
            });
        }
        System.out.println("-- Available --");
        FormatUtils.printTable(headers, available);
        if (!result.excluded.isEmpty()) {
            List<String[]> excluded = new ArrayList<>();
            for (EnrollmentController.ExcludedSubject x : result.excluded) {
                excluded.add(new String[]{TextUtils.orEmpty(x.subject.getCode()), x.reason});
            }
            System.out.println("-- Excluded --");
            FormatUtils.printTable(new String[]{"Code", "Reason"}, excluded);
        }
    }
}
