package crys.sims.view;

import crys.sims.controller.FacultyController;
import crys.sims.model.Department;
import crys.sims.model.Faculty;
import crys.sims.model.Subject;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Console UI for Faculty CRUD, Department management, and hierarchy browsing.
 * I/O only: all business logic and persistence live in FacultyController.
 */
public class FacultyView {

    private final FacultyController controller;
    private final Scanner scanner;

    public FacultyView(FacultyController controller, Scanner scanner) {
        this.controller = controller;
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

    // ===== Faculty actions (I/O only) =====

    private void listAll() {
        FormatUtils.printHeader("ALL FACULTIES");
        printFaculties(controller.getAll());
    }

    private void addFaculty() {
        FormatUtils.printHeader("ADD FACULTY");
        try {
            String name = InputUtils.readRequiredLine(scanner, "Name: ");
            Faculty f = controller.add(name);
            System.out.println("  Added faculty " + f.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID: ");
        Faculty f = controller.getById(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printFacultyInfo(f);
    }

    private void updateFaculty() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID to update: ");
        Faculty f = controller.getById(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printFacultyInfo(f);
        try {
            String rawName = InputUtils.readLine(scanner, "Name [" + TextUtils.orEmpty(f.getName()) + "]: ");
            if (rawName.isEmpty()) {
                System.out.println("  Nothing changed.");
                return;
            }
            controller.setName(id, rawName);
            System.out.println("  Updated " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage() + " — nothing changed.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void deleteFaculty() {
        String id = InputUtils.readRequiredLine(scanner, "Faculty ID to delete: ");
        Faculty f = controller.getById(id);
        if (f == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        List<Department> attached = controller.getDepartmentsByFaculty(f.getId());
        if (!attached.isEmpty()) {
            System.out.println("  Cannot delete: faculty still has " + attached.size() + " department(s):");
            for (Department d : attached) {
                System.out.println("    " + d.getId() + " " + TextUtils.orEmpty(d.getName()));
            }
            System.out.println("  Move or delete them first (menu 6).");
            return;
        }
        printFacultyInfo(f);
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + f.getId() + " (" + TextUtils.orEmpty(f.getName()) + ")?", false);
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
        printDepartments(controller.getAllDepartments());
    }

    private void addDepartment() {
        FormatUtils.printHeader("ADD DEPARTMENT");
        try {
            String name = InputUtils.readRequiredLine(scanner, "Name: ");
            String facultyId = readExistingFacultyId();
            Department d = controller.addDepartment(name, facultyId);
            System.out.println("  Added department " + d.getId() + " under " + d.getFacultyId());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void updateDepartment() {
        String id = InputUtils.readRequiredLine(scanner, "Department ID to update: ");
        Department d = controller.getDepartmentById(id);
        if (d == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        try {
            String rawName = InputUtils.readLine(scanner, "Name [" + TextUtils.orEmpty(d.getName()) + "]: ");
            if (!rawName.isEmpty()) controller.setDepartmentName(id, rawName);

            String rawFac = InputUtils.readLine(scanner, "Faculty ID [" + TextUtils.orEmpty(d.getFacultyId()) + "]: ");
            if (!rawFac.isEmpty()) controller.moveDepartment(id, rawFac);

            System.out.println("  Updated " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage()
                    + " — earlier fields may already be saved; re-run to fix.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void deleteDepartment() {
        String id = InputUtils.readRequiredLine(scanner, "Department ID to delete: ");
        Department d = controller.getDepartmentById(id);
        if (d == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        List<Subject> using = controller.getSubjectsOf(d.getId());
        if (!using.isEmpty()) {
            System.out.println("  Cannot delete: " + using.size() + " subject(s) reference this department:");
            for (Subject s : using) {
                System.out.println("    " + s.getId() + " " + TextUtils.orEmpty(s.getCode()) + " " + TextUtils.orEmpty(s.getName()));
            }
            System.out.println("  Reassign or delete them first (subject menu).");
            return;
        }
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + d.getId() + " (" + TextUtils.orEmpty(d.getName()) + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        try {
            controller.deleteDepartment(id);
            System.out.println("  Deleted " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    // ===== Browse + search =====

    private void browseHierarchy() {
        FormatUtils.printHeader("FACULTY / DEPARTMENT HIERARCHY");
        for (Faculty f : controller.getAll()) {
            System.out.println(TextUtils.orEmpty(f.getName()) + " (" + TextUtils.orEmpty(f.getId()) + ")");
            List<Department> depts = controller.getDepartmentsByFaculty(f.getId());
            if (depts.isEmpty()) {
                System.out.println("  (no departments)");
            }
            for (Department d : depts) {
                List<Subject> subs = controller.getSubjectsOf(d.getId());
                System.out.println("  " + TextUtils.orEmpty(d.getId()) + " " + TextUtils.orEmpty(d.getName())
                        + " [" + subs.size() + " subject(s)]");
                for (Subject s : subs) {
                    System.out.println("    " + TextUtils.orEmpty(s.getCode()) + " " + TextUtils.orEmpty(s.getName())
                            + " (" + s.getCredits() + " cr, " + controller.getEnrollmentCount(s.getId()) + " enrolled)");
                }
            }
        }
        List<Department> orphans = controller.getOrphanDepartments();
        if (!orphans.isEmpty()) {
            System.out.println("Departments with unknown faculty:");
            for (Department d : orphans) {
                System.out.println("  " + TextUtils.orEmpty(d.getId()) + " " + TextUtils.orEmpty(d.getName())
                        + " -> faculty '" + TextUtils.orEmpty(d.getFacultyId()) + "' not found");
            }
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (faculty/department id or name): ");
        FormatUtils.printHeader("SEARCH RESULTS");
        System.out.println("-- Faculties --");
        printFaculties(controller.searchFaculties(q));
        System.out.println("-- Departments --");
        printDepartments(controller.searchDepartments(q));
    }

    // ===== Display helpers =====

    private void printFaculties(List<Faculty> list) {
        String[] headers = {"ID", "Name", "Depts"};
        List<String[]> rows = new ArrayList<>();
        for (Faculty f : list) {
            rows.add(new String[]{
                    TextUtils.orEmpty(f.getId()),
                    TextUtils.orEmpty(f.getName()),
                    String.valueOf(controller.getDepartmentsByFaculty(f.getId()).size())
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printDepartments(List<Department> list) {
        String[] headers = {"ID", "Name", "Faculty", "Subjects"};
        List<String[]> rows = new ArrayList<>();
        for (Department d : list) {
            Faculty owner = controller.getById(d.getFacultyId());
            String facShown = owner == null
                    ? "MISSING (" + TextUtils.orEmpty(d.getFacultyId()) + ")"
                    : owner.getId() + " " + TextUtils.orEmpty(owner.getName());
            rows.add(new String[]{
                    TextUtils.orEmpty(d.getId()),
                    TextUtils.orEmpty(d.getName()),
                    facShown,
                    String.valueOf(controller.getSubjectCount(d.getId()))
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printFacultyInfo(Faculty f) {
        FormatUtils.printHeader("FACULTY INFO: " + f.getId());
        System.out.println("Name:        " + TextUtils.orEmpty(f.getName()));
        List<Department> depts = controller.getDepartmentsByFaculty(f.getId());
        System.out.println("Departments (" + depts.size() + "):");
        if (depts.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Department d : depts) {
            System.out.println("  " + TextUtils.orEmpty(d.getId()) + " " + TextUtils.orEmpty(d.getName())
                    + " [" + controller.getSubjectCount(d.getId()) + " subject(s)]");
        }
    }

    // ===== Input helpers =====

    private String readExistingFacultyId() {
        while (true) {
            String fid = InputUtils.readRequiredLine(scanner, "Faculty ID: ");
            if (controller.getById(fid) != null) return fid;
            System.out.println("  Unknown faculty ID: " + fid + " (add it first).");
        }
    }
}
