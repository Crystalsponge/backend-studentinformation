package crys.sims.view;

import crys.sims.model.Enrollment;
import crys.sims.model.Subject;
import crys.sims.service.FileService;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Console UI for Subject CRUD + search + sort + info.
 * Temporary: talks directly to the in-memory list + FileService.
 * A SubjectController will take over business logic later; this view keeps only I/O.
 */
public class SubjectView {

    private final List<Subject> subjects;
    private final Path filePath;
    private final List<Enrollment> enrollments;
    private final Scanner scanner;

    public SubjectView(List<Subject> subjects, Path filePath,
                       List<Enrollment> enrollments, Scanner scanner) {
        this.subjects = subjects;
        this.filePath = filePath;
        this.enrollments = enrollments;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("SUBJECT MANAGEMENT");
            System.out.println("1. List all subjects");
            System.out.println("2. Add subject");
            System.out.println("3. View subject info");
            System.out.println("4. Update subject");
            System.out.println("5. Delete subject");
            System.out.println("6. Search subjects");
            System.out.println("7. Sort by code");
            System.out.println("8. Sort by name");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 8);
            switch (choice) {
                case 1: listAll(); break;
                case 2: addSubject(); break;
                case 3: viewById(); break;
                case 4: updateSubject(); break;
                case 5: deleteSubject(); break;
                case 6: search(); break;
                case 7: sortByCode(); break;
                case 8: sortByName(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Actions =====

    private void listAll() {
        FormatUtils.printHeader("ALL SUBJECTS");
        printSubjects(subjects);
    }

    private void addSubject() {
        FormatUtils.printHeader("ADD SUBJECT");
        try {
            List<String> ids = subjects.stream().map(Subject::getId).collect(Collectors.toList());
            String id = IdGenerator.nextId(ids, "SUBJ", 3);
            System.out.println("Assigned ID: " + id);

            String code = readUniqueCode(null);
            String name = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Name: "), "name");
            int credits = InputUtils.readInt(scanner, "Credits: ", 0);
            String department = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Department ID (e.g. D001): "), "department");
            List<String> prereqs = readPrereqIds(id);
            String semester = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Semester offered (e.g. 1): "), "semesterOffered");
            int capacity = InputUtils.readInt(scanner, "Max capacity: ", 1);

            Subject s = new Subject(id, code, name, credits, department, prereqs, semester, capacity);
            subjects.add(s);
            if (save()) {
                System.out.println("  Added subject " + id);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID: ");
        Subject s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
    }

    private void updateSubject() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID to update: ");
        Subject s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
        System.out.println("  (empty keeps current value)");
        try {
            // Gather + validate everything first, so a bad value aborts
            // before any field is changed.
            String rawCode = InputUtils.readLine(scanner, "Code [" + text(s.getCode()) + "]: ");
            String newCode = rawCode.isEmpty() ? s.getCode() : checkCodeUnique(rawCode, s.getId());

            String rawName = InputUtils.readLine(scanner, "Name [" + text(s.getName()) + "]: ");
            String newName = rawName.isEmpty() ? s.getName()
                    : ValidationUtils.cleanField(rawName, "name");

            int newCredits = InputUtils.readOptionalInt(scanner, "Credits", s.getCredits());

            String rawDept = InputUtils.readLine(scanner, "Department ID [" + text(s.getDepartment()) + "]: ");
            String newDept = rawDept.isEmpty() ? s.getDepartment()
                    : ValidationUtils.cleanField(rawDept, "department");

            List<String> newPrereqs = readOptionalPrereqIds(s.getId(), s.getPrerequisiteIds());

            String rawSem = InputUtils.readLine(scanner, "Semester offered [" + text(s.getSemesterOffered()) + "]: ");
            String newSem = rawSem.isEmpty() ? s.getSemesterOffered()
                    : ValidationUtils.cleanField(rawSem, "semesterOffered");

            int newCapacity = InputUtils.readOptionalInt(scanner, "Max capacity", s.getMaxCapacity());

            s.setCode(newCode);
            s.setName(newName);
            s.setCredits(newCredits);
            s.setDepartment(newDept);
            s.setPrerequisiteIds(newPrereqs);
            s.setSemesterOffered(newSem);
            s.setMaxCapacity(newCapacity);

            if (save()) {
                System.out.println("  Updated " + s.getId());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage() + " — nothing changed.");
        }
    }

    private void deleteSubject() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID to delete: ");
        Subject s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + s.getId() + " (" + text(s.getCode()) + " " + text(s.getName()) + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        subjects.remove(s);
        if (save()) {
            System.out.println("  Deleted " + id);
            System.out.println("  Note: enrollments/records referencing this subject are kept (cleanup comes with controllers).");
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (id/code/name/department): ")
                .toLowerCase(Locale.ROOT);
        List<Subject> hits = new ArrayList<>();
        for (Subject s : subjects) {
            if (contains(s.getId(), q)
                    || contains(s.getCode(), q)
                    || contains(s.getName(), q)
                    || contains(s.getDepartment(), q)) {
                hits.add(s);
            }
        }
        FormatUtils.printHeader("SEARCH RESULTS");
        printSubjects(hits);
    }

    private void sortByCode() {
        List<Subject> sorted = new ArrayList<>(subjects);
        sorted.sort(Comparator.comparing(Subject::getCode, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        FormatUtils.printHeader("SUBJECTS SORTED BY CODE");
        printSubjects(sorted);
    }

    private void sortByName() {
        List<Subject> sorted = new ArrayList<>(subjects);
        sorted.sort(Comparator.comparing(Subject::getName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        FormatUtils.printHeader("SUBJECTS SORTED BY NAME");
        printSubjects(sorted);
    }

    // ===== Display helpers =====

    private void printSubjects(List<Subject> list) {
        String[] headers = {"ID", "Code", "Name", "Cr", "Dept", "Prereqs", "Sem", "Cap", "Enr"};
        List<String[]> rows = new ArrayList<>();
        for (Subject s : list) {
            String prereqs = (s.getPrerequisiteIds() == null || s.getPrerequisiteIds().isEmpty())
                    ? "-" : String.join(",", s.getPrerequisiteIds());
            rows.add(new String[]{
                    text(s.getId()),
                    text(s.getCode()),
                    text(s.getName()),
                    String.valueOf(s.getCredits()),
                    text(s.getDepartment()),
                    prereqs,
                    text(s.getSemesterOffered()),
                    String.valueOf(s.getMaxCapacity()),
                    String.valueOf(enrollmentCount(s.getId()))
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printSubjectInfo(Subject s) {
        FormatUtils.printHeader("SUBJECT INFO: " + s.getId());
        System.out.println("Code:             " + text(s.getCode()));
        System.out.println("Name:             " + text(s.getName()));
        System.out.println("Credits:          " + s.getCredits());
        System.out.println("Department:       " + text(s.getDepartment()));
        System.out.println("Semester offered: " + text(s.getSemesterOffered()));
        System.out.println("Max capacity:     " + s.getMaxCapacity());
        System.out.println("Enrolled:         " + enrollmentCount(s.getId()));
        System.out.println("Prerequisites:");
        if (s.getPrerequisiteIds() == null || s.getPrerequisiteIds().isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String pid : s.getPrerequisiteIds()) {
                Subject pre = findById(pid);
                if (pre == null) {
                    System.out.println("  " + pid + " — MISSING (dangling reference)");
                } else {
                    System.out.println("  " + pid + " — " + text(pre.getCode()) + " " + text(pre.getName()));
                }
            }
        }
    }

    // ===== Input helpers =====

    private String readUniqueCode(String selfId) {
        while (true) {
            String code = InputUtils.readRequiredLine(scanner, "Code (e.g. CS101): ");
            try {
                return checkCodeUnique(code, selfId);
            } catch (IllegalArgumentException e) {
                System.out.println("  " + e.getMessage() + " Try again.");
            }
        }
    }

    private String checkCodeUnique(String code, String selfId) {
        String clean = ValidationUtils.cleanField(code, "code");
        Subject existing = findByCode(clean);
        if (existing != null && (selfId == null || !existing.getId().equalsIgnoreCase(selfId))) {
            throw new IllegalArgumentException("Code '" + clean + "' is already used by " + existing.getId() + ".");
        }
        return clean;
    }

    private List<String> readPrereqIds(String selfId) {
        while (true) {
            String raw = InputUtils.readLine(scanner, "Prerequisite IDs (comma-separated, empty = none): ");
            if (raw.isEmpty()) return new ArrayList<>();
            List<String> parsed = parsePrereqIds(raw, selfId);
            if (parsed != null) return parsed;
        }
    }

    private List<String> readOptionalPrereqIds(String selfId, List<String> current) {
        String shown = (current == null || current.isEmpty()) ? "none" : String.join(",", current);
        while (true) {
            String raw = InputUtils.readLine(scanner, "Prerequisites [" + shown + "]: ");
            if (raw.isEmpty()) {
                return current == null ? new ArrayList<>() : new ArrayList<>(current);
            }
            List<String> parsed = parsePrereqIds(raw, selfId);
            if (parsed != null) return parsed;
        }
    }

    private List<String> parsePrereqIds(String raw, String selfId) {
        List<String> ids = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        try {
            ValidationUtils.checkList(ids, "prerequisiteIds");
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
            return null;
        }
        for (String pid : ids) {
            if (pid.equalsIgnoreCase(selfId)) {
                System.out.println("  Subject cannot be its own prerequisite.");
                return null;
            }
            if (findById(pid) == null) {
                System.out.println("  Unknown subject ID: " + pid + " (add it first).");
                return null;
            }
        }
        return ids;
    }

    // ===== Internal helpers =====

    private Subject findById(String id) {
        for (Subject s : subjects) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private Subject findByCode(String code) {
        for (Subject s : subjects) {
            if (s.getCode() != null && s.getCode().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }

    private int enrollmentCount(String subjectId) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())) {
                n++;
            }
        }
        return n;
    }

    private boolean save() {
        try {
            FileService.saveSubjects(filePath, subjects);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
