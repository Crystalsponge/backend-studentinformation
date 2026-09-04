package crys.sims.view;

import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Subject;
import crys.sims.service.FileService;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Console UI for Faculty CRUD, Department management, and hierarchy browsing.
 * Temporary: talks directly to the in-memory lists + FileService.
 * A FacultyController will take over business logic later; this view keeps only I/O.
 *
 * Sync contract: Department.facultyId is authoritative. Every department
 * mutation rebuilds each Faculty.departmentIds via syncFacultyDepartments()
 * and saves both files.
 */
public class FacultyView {

    private final List<Faculty> faculties;
    private final Path facultiesPath;
    private final List<Department> departments;
    private final Path departmentsPath;
    private final List<Subject> subjects;
    private final List<Enrollment> enrollments;
    private final Scanner scanner;

    public FacultyView(List<Faculty> faculties, Path facultiesPath,
                       List<Department> departments, Path departmentsPath,
                       List<Subject> subjects, List<Enrollment> enrollments,
                       Scanner scanner) {
        this.faculties = faculties;
        this.facultiesPath = facultiesPath;
        this.departments = departments;
        this.departmentsPath = departmentsPath;
        this.subjects = subjects;
        this.enrollments = enrollments;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("FACULTY MANAGEMENT");
            System.out.println("1. List all faculties");
            System.out.println("2. Add faculty");
            System.out.println("3. View faculty info");
            System.out.println("4. Update faculty");
            System.out.println("5. Delete faculty");
            System.out.println("6. Manage departments");
            System.out.println("7. Browse hierarchy");
            System.out.println("8. Search");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 8);
            switch (choice) {
                case 1: listAll(); break;
                case 2: addFaculty(); break;
                case 3: viewById(); break;
                case 4: updateFaculty(); break;
                case 5: deleteFaculty(); break;
                case 6: manageDepartments(); break;
                case 7: browseHierarchy(); break;
                case 8: search(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Faculty actions =====

    private void listAll() {
        FormatUtils.printHeader("ALL FACULTIES");
        printFaculties(faculties);
    }

    private void addFaculty() {
        FormatUtils.printHeader("ADD FACULTY");
        try {
            List<String> ids = faculties.stream().map(Faculty::getId).collect(Collectors.toList());
            String id = IdGenerator.nextId(ids, "F", 3);
            System.out.println("Assigned ID: " + id);
            String name = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Name: "), "name");
            faculties.add(new Faculty(id, name, new ArrayList<>()));
            if (saveFaculties()) {
                System.out.println("  Added faculty " + id);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID: ");
        Faculty f = findFaculty(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printFacultyInfo(f);
    }

    private void updateFaculty() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID to update: ");
        Faculty f = findFaculty(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printFacultyInfo(f);
        try {
            String rawName = InputUtils.readLine(scanner, "Name [" + text(f.getName()) + "]: ");
            String newName = rawName.isEmpty() ? f.getName()
                    : ValidationUtils.cleanField(rawName, "name");
            f.setName(newName);
            if (saveFaculties()) {
                System.out.println("  Updated " + f.getId());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage() + " — nothing changed.");
        }
    }

    private void deleteFaculty() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID to delete: ");
        Faculty f = findFaculty(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        List<Department> attached = departmentsOf(f.getId());
        if (!attached.isEmpty()) {
            System.out.println("  Cannot delete: faculty still has " + attached.size() + " department(s):");
            for (Department d : attached) {
                System.out.println("    " + d.getId() + " " + text(d.getName()));
            }
            System.out.println("  Move or delete them first (menu 6).");
            return;
        }
        printFacultyInfo(f);
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + f.getId() + " (" + text(f.getName()) + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        faculties.remove(f);
        if (saveFaculties()) {
            System.out.println("  Deleted " + id);
        }
    }

    // ===== Department submenu =====

    private void manageDepartments() {
        while (true) {
            FormatUtils.printHeader("DEPARTMENT MANAGEMENT");
            System.out.println("1. List all departments");
            System.out.println("2. Add department");
            System.out.println("3. Update department");
            System.out.println("4. Delete department");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 4);
            switch (choice) {
                case 1: listDepartments(); break;
                case 2: addDepartment(); break;
                case 3: updateDepartment(); break;
                case 4: deleteDepartment(); break;
                case 0: return;
                default: break;
            }
        }
    }

    private void listDepartments() {
        FormatUtils.printHeader("ALL DEPARTMENTS");
        printDepartments(departments);
    }

    private void addDepartment() {
        FormatUtils.printHeader("ADD DEPARTMENT");
        try {
            List<String> ids = departments.stream().map(Department::getId).collect(Collectors.toList());
            String id = IdGenerator.nextId(ids, "D", 3);
            System.out.println("Assigned ID: " + id);
            String name = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Name: "), "name");
            Faculty owner = readExistingFaculty();
            departments.add(new Department(id, name, owner.getId()));
            syncFacultyDepartments();
            if (saveBoth()) {
                System.out.println("  Added department " + id + " under " + owner.getId());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        }
    }

    private void updateDepartment() {
        String id = InputUtils.readRequiredLine(scanner, "Department ID to update: ");
        Department d = findDepartment(id);
        if (d == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        try {
            String rawName = InputUtils.readLine(scanner, "Name [" + text(d.getName()) + "]: ");
            String newName = rawName.isEmpty() ? d.getName()
                    : ValidationUtils.cleanField(rawName, "name");

            String rawFac = InputUtils.readLine(scanner, "Faculty ID [" + text(d.getFacultyId()) + "]: ");
            String newFacId = d.getFacultyId();
            if (!rawFac.isEmpty()) {
                Faculty owner = findFaculty(rawFac);
                if (owner == null) {
                    System.out.println("  Unknown faculty ID: " + rawFac.trim() + " — nothing changed.");
                    return;
                }
                newFacId = owner.getId();
            }

            d.setName(newName);
            d.setFacultyId(newFacId);
            syncFacultyDepartments();
            if (saveBoth()) {
                System.out.println("  Updated " + d.getId());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage() + " — nothing changed.");
        }
    }

    private void deleteDepartment() {
        String id = InputUtils.readRequiredLine(scanner, "Department ID to delete: ");
        Department d = findDepartment(id);
        if (d == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        List<Subject> using = subjectsOf(d.getId());
        if (!using.isEmpty()) {
            System.out.println("  Cannot delete: " + using.size() + " subject(s) reference this department:");
            for (Subject s : using) {
                System.out.println("    " + s.getId() + " " + text(s.getCode()) + " " + text(s.getName()));
            }
            System.out.println("  Reassign or delete them first (subject menu).");
            return;
        }
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + d.getId() + " (" + text(d.getName()) + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        departments.remove(d);
        syncFacultyDepartments();
        if (saveBoth()) {
            System.out.println("  Deleted " + id);
        }
    }

    // ===== Browse + search =====

    private void browseHierarchy() {
        FormatUtils.printHeader("FACULTY / DEPARTMENT HIERARCHY");
        for (Faculty f : faculties) {
            System.out.println(text(f.getName()) + " (" + text(f.getId()) + ")");
            List<Department> depts = departmentsOf(f.getId());
            if (depts.isEmpty()) {
                System.out.println("  (no departments)");
            }
            for (Department d : depts) {
                List<Subject> subs = subjectsOf(d.getId());
                System.out.println("  " + text(d.getId()) + " " + text(d.getName())
                        + " [" + subs.size() + " subject(s)]");
                for (Subject s : subs) {
                    System.out.println("    " + text(s.getCode()) + " " + text(s.getName())
                            + " (" + s.getCredits() + " cr, " + enrollmentCount(s.getId()) + " enrolled)");
                }
            }
        }
        List<Department> orphans = orphanDepartments();
        if (!orphans.isEmpty()) {
            System.out.println("Departments with unknown faculty:");
            for (Department d : orphans) {
                System.out.println("  " + text(d.getId()) + " " + text(d.getName())
                        + " -> faculty '" + text(d.getFacultyId()) + "' not found");
            }
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (faculty/department id or name): ")
                .toLowerCase(Locale.ROOT);
        List<Faculty> facHits = new ArrayList<>();
        for (Faculty f : faculties) {
            if (contains(f.getId(), q) || contains(f.getName(), q)) facHits.add(f);
        }
        List<Department> deptHits = new ArrayList<>();
        for (Department d : departments) {
            if (contains(d.getId(), q) || contains(d.getName(), q)) deptHits.add(d);
        }
        FormatUtils.printHeader("SEARCH RESULTS");
        System.out.println("-- Faculties --");
        printFaculties(facHits);
        System.out.println("-- Departments --");
        printDepartments(deptHits);
    }

    // ===== Display helpers =====

    private void printFaculties(List<Faculty> list) {
        String[] headers = {"ID", "Name", "Depts"};
        List<String[]> rows = new ArrayList<>();
        for (Faculty f : list) {
            rows.add(new String[]{
                    text(f.getId()),
                    text(f.getName()),
                    String.valueOf(departmentsOf(f.getId()).size())
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printDepartments(List<Department> list) {
        String[] headers = {"ID", "Name", "Faculty", "Subjects"};
        List<String[]> rows = new ArrayList<>();
        for (Department d : list) {
            Faculty owner = d.getFacultyId() == null ? null : findFaculty(d.getFacultyId());
            String facShown = owner == null
                    ? "MISSING (" + text(d.getFacultyId()) + ")"
                    : owner.getId() + " " + text(owner.getName());
            rows.add(new String[]{
                    text(d.getId()),
                    text(d.getName()),
                    facShown,
                    String.valueOf(subjectsOf(d.getId()).size())
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printFacultyInfo(Faculty f) {
        FormatUtils.printHeader("FACULTY INFO: " + f.getId());
        System.out.println("Name:        " + text(f.getName()));
        List<Department> depts = departmentsOf(f.getId());
        System.out.println("Departments (" + depts.size() + "):");
        if (depts.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Department d : depts) {
            System.out.println("  " + text(d.getId()) + " " + text(d.getName())
                    + " [" + subjectsOf(d.getId()).size() + " subject(s)]");
        }
    }

    // ===== Input helpers =====

    private Faculty readExistingFaculty() {
        while (true) {
            String fid = InputUtils.readRequiredLine(scanner, "Faculty ID: ");
            Faculty owner = findFaculty(fid);
            if (owner != null) return owner;
            System.out.println("  Unknown faculty ID: " + fid + " (add it first).");
        }
    }

    // ===== Sync + save =====

    private void syncFacultyDepartments() {
        Map<String, List<String>> byFaculty = new HashMap<>();
        for (Department d : departments) {
            byFaculty.computeIfAbsent(text(d.getFacultyId()), k -> new ArrayList<>()).add(d.getId());
        }
        for (Faculty f : faculties) {
            List<String> ids = byFaculty.getOrDefault(f.getId(), new ArrayList<>());
            f.setDepartmentIds(new ArrayList<>(ids));
        }
    }

    private boolean saveFaculties() {
        try {
            FileService.saveFaculties(facultiesPath, faculties);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    private boolean saveBoth() {
        try {
            FileService.saveFaculties(facultiesPath, faculties);
            FileService.saveDepartments(departmentsPath, departments);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    // ===== Internal helpers =====

    private Faculty findFaculty(String id) {
        for (Faculty f : faculties) {
            if (f.getId() != null && f.getId().equalsIgnoreCase(id.trim())) {
                return f;
            }
        }
        return null;
    }

    private Department findDepartment(String id) {
        for (Department d : departments) {
            if (d.getId() != null && d.getId().equalsIgnoreCase(id.trim())) {
                return d;
            }
        }
        return null;
    }

    private List<Department> departmentsOf(String facultyId) {
        List<Department> result = new ArrayList<>();
        for (Department d : departments) {
            if (facultyId != null && facultyId.equals(d.getFacultyId())) {
                result.add(d);
            }
        }
        return result;
    }

    private List<Department> orphanDepartments() {
        List<Department> result = new ArrayList<>();
        for (Department d : departments) {
            if (findFaculty(text(d.getFacultyId())) == null) {
                result.add(d);
            }
        }
        return result;
    }

    private List<Subject> subjectsOf(String departmentId) {
        List<Subject> result = new ArrayList<>();
        for (Subject s : subjects) {
            if (departmentId != null && departmentId.equals(s.getDepartment())) {
                result.add(s);
            }
        }
        return result;
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

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
