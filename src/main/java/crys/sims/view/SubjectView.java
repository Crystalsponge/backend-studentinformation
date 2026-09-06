package crys.sims.view;

import crys.sims.controller.SubjectController;
import crys.sims.model.Subject;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Console UI for Subject CRUD + search + sort + info.
 * I/O only: all business logic and persistence live in SubjectController.
 */
public class SubjectView {

    private final SubjectController controller;
    private final Scanner scanner;

    public SubjectView(SubjectController controller, Scanner scanner) {
        this.controller = controller;
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

    // ===== Actions (I/O only) =====

    private void listAll() {
        FormatUtils.printHeader("ALL SUBJECTS");
        printSubjects(controller.getAll());
    }

    private void addSubject() {
        FormatUtils.printHeader("ADD SUBJECT");
        try {
            String code = readUniqueCode(null);
            String name = InputUtils.readRequiredLine(scanner, "Name: ");
            int credits = InputUtils.readInt(scanner, "Credits: ", 0);
            String department = InputUtils.readRequiredLine(scanner, "Department ID (e.g. D001): ");
            List<String> prereqs = readPrereqIds();
            String semester = InputUtils.readRequiredLine(scanner, "Semester offered (e.g. 1): ");
            int capacity = InputUtils.readInt(scanner, "Max capacity: ", 1);

            Subject s = controller.add(code, name, credits, department, prereqs, semester, capacity);
            System.out.println("  Added subject " + s.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID: ");
        Subject s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
    }

    private void updateSubject() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID to update: ");
        Subject s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
        System.out.println("  (empty keeps current value)");
        int curCredits = s.getCredits();
        int curCapacity = s.getMaxCapacity();
        try {
            String rawCode = InputUtils.readLine(scanner, "Code [" + TextUtils.orEmpty(s.getCode()) + "]: ");
            if (!rawCode.isEmpty()) controller.setCode(id, rawCode);

            String rawName = InputUtils.readLine(scanner, "Name [" + TextUtils.orEmpty(s.getName()) + "]: ");
            if (!rawName.isEmpty()) controller.setName(id, rawName);

            int newCredits = InputUtils.readOptionalInt(scanner, "Credits", curCredits);
            if (newCredits != curCredits) controller.setCredits(id, newCredits);

            String rawDept = InputUtils.readLine(scanner, "Department ID [" + TextUtils.orEmpty(s.getDepartment()) + "]: ");
            if (!rawDept.isEmpty()) controller.setDepartment(id, rawDept);

            List<String> newPrereqs = readOptionalPrereqIds(s.getId(), s.getPrerequisiteIds());
            if (newPrereqs != null) controller.setPrerequisiteIds(id, newPrereqs);

            String rawSem = InputUtils.readLine(scanner, "Semester offered [" + TextUtils.orEmpty(s.getSemesterOffered()) + "]: ");
            if (!rawSem.isEmpty()) controller.setSemesterOffered(id, rawSem);

            int newCapacity = InputUtils.readOptionalInt(scanner, "Max capacity", curCapacity);
            if (newCapacity != curCapacity) controller.setMaxCapacity(id, newCapacity);

            System.out.println("  Updated " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage()
                    + " — earlier fields may already be saved; re-run to fix.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void deleteSubject() {
        String id = InputUtils.readRequiredLine(scanner, "Subject ID to delete: ");
        Subject s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printSubjectInfo(s);
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + s.getId() + " (" + TextUtils.orEmpty(s.getCode()) + " " + TextUtils.orEmpty(s.getName()) + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        try {
            controller.delete(id);
            System.out.println("  Deleted " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (id/code/name/department): ");
        FormatUtils.printHeader("SEARCH RESULTS");
        printSubjects(controller.search(q));
    }

    private void sortByCode() {
        FormatUtils.printHeader("SUBJECTS SORTED BY CODE");
        printSubjects(controller.sortByCode());
    }

    private void sortByName() {
        FormatUtils.printHeader("SUBJECTS SORTED BY NAME");
        printSubjects(controller.sortByName());
    }

    // ===== Display helpers =====

    private void printSubjects(List<Subject> list) {
        String[] headers = {"ID", "Code", "Name", "Cr", "Dept", "Prereqs", "Sem", "Cap", "Enr"};
        List<String[]> rows = new ArrayList<>();
        for (Subject s : list) {
            String prereqs = (s.getPrerequisiteIds() == null || s.getPrerequisiteIds().isEmpty())
                    ? "-" : String.join(",", s.getPrerequisiteIds());
            rows.add(new String[]{
                    TextUtils.orEmpty(s.getId()),
                    TextUtils.orEmpty(s.getCode()),
                    TextUtils.orEmpty(s.getName()),
                    String.valueOf(s.getCredits()),
                    TextUtils.orEmpty(s.getDepartment()),
                    prereqs,
                    TextUtils.orEmpty(s.getSemesterOffered()),
                    String.valueOf(s.getMaxCapacity()),
                    String.valueOf(controller.getTotalEnrolled(s.getId()))
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printSubjectInfo(Subject s) {
        FormatUtils.printHeader("SUBJECT INFO: " + s.getId());
        System.out.println("Code:             " + TextUtils.orEmpty(s.getCode()));
        System.out.println("Name:             " + TextUtils.orEmpty(s.getName()));
        System.out.println("Credits:          " + s.getCredits());
        System.out.println("Department:       " + TextUtils.orEmpty(s.getDepartment()));
        System.out.println("Semester offered: " + TextUtils.orEmpty(s.getSemesterOffered()));
        System.out.println("Max capacity:     " + s.getMaxCapacity());
        System.out.println("Enrolled:         " + controller.getTotalEnrolled(s.getId()));
        System.out.println("Prerequisites:");
        if (s.getPrerequisiteIds() == null || s.getPrerequisiteIds().isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (String pid : s.getPrerequisiteIds()) {
                Subject pre = controller.getById(pid);
                if (pre == null) {
                    System.out.println("  " + pid + " — MISSING (dangling reference)");
                } else {
                    System.out.println("  " + pid + " — " + TextUtils.orEmpty(pre.getCode()) + " " + TextUtils.orEmpty(pre.getName()));
                }
            }
        }
    }

    // ===== Input helpers (existence checks only; controller enforces) =====

    private String readUniqueCode(String selfId) {
        while (true) {
            String code = InputUtils.readRequiredLine(scanner, "Code (e.g. CS101): ").trim();
            if (code.isEmpty()) {
                System.out.println("  Value must not be blank. Try again.");
                continue;
            }
            Subject existing = controller.getByCode(code);
            if (existing != null && (selfId == null || !existing.getId().equalsIgnoreCase(selfId))) {
                System.out.println("  Code '" + code + "' is already used by " + existing.getId() + ". Try again.");
                continue;
            }
            return code;
        }
    }

    private List<String> readPrereqIds() {
        while (true) {
            String raw = InputUtils.readLine(scanner, "Prerequisite IDs (comma-separated, empty = none): ");
            if (raw.isEmpty()) return new ArrayList<>();
            List<String> parsed = parsePrereqIds(raw);
            if (parsed != null) return parsed;
        }
    }

    private List<String> readOptionalPrereqIds(String selfId, List<String> current) {
        String shown = (current == null || current.isEmpty()) ? "none" : String.join(",", current);
        while (true) {
            String raw = InputUtils.readLine(scanner, "Prerequisites [" + shown + "]: ");
            if (raw.isEmpty()) return null;
            List<String> parsed = parsePrereqIds(raw);
            if (parsed == null) continue;
            for (String pid : parsed) {
                if (pid.equalsIgnoreCase(selfId)) {
                    System.out.println("  Subject cannot be its own prerequisite.");
                    parsed = null;
                    break;
                }
            }
            if (parsed != null) return parsed;
        }
    }

    private List<String> parsePrereqIds(String raw) {
        List<String> ids = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        for (String pid : ids) {
            if (controller.getById(pid) == null) {
                System.out.println("  Unknown subject ID: " + pid + " (add it first).");
                return null;
            }
        }
        return ids;
    }

}
