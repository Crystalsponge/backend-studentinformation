package crys.sims.view;

import crys.sims.controller.StudentController;
import crys.sims.model.Student;
import crys.sims.model.enums.GENDER;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Console UI for Student CRUD + search + sort.
 * I/O only: all business logic and persistence live in StudentController.
 */
public class StudentView {

    private final StudentController controller;
    private final Scanner scanner;

    public StudentView(StudentController controller, Scanner scanner) {
        this.controller = controller;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("STUDENT MANAGEMENT");
            System.out.println("1. List all students");
            System.out.println("2. Add student");
            System.out.println("3. View student by ID");
            System.out.println("4. Update student");
            System.out.println("5. Delete student");
            System.out.println("6. Search students");
            System.out.println("7. Sort by name");
            System.out.println("8. Sort by GPA");
            System.out.println("0. Exit");
            int choice = InputUtils.readMenuChoice(scanner, 0, 8);
            switch (choice) {
                case 1: listAll(); break;
                case 2: addStudent(); break;
                case 3: viewById(); break;
                case 4: updateStudent(); break;
                case 5: deleteStudent(); break;
                case 6: search(); break;
                case 7: sortByName(); break;
                case 8: sortByGpa(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Actions (I/O only) =====

    private void listAll() {
        FormatUtils.printHeader("ALL STUDENTS");
        printStudents(controller.getAllActive());
    }

    private void addStudent() {
        FormatUtils.printHeader("ADD STUDENT");
        try {
            String firstName = InputUtils.readRequiredLine(scanner, "First name: ");
            String lastName = InputUtils.readRequiredLine(scanner, "Last name: ");
            GENDER gender = InputUtils.readGender(scanner, "Gender");
            LocalDate dateOfBirth = InputUtils.readOptionalDate(scanner, "Date of birth");
            String department = InputUtils.readRequiredLine(scanner, "Department ID (e.g. D001): ");
            String program = InputUtils.readRequiredLine(scanner, "Program (e.g. BSIS): ");
            int yearLevel = InputUtils.readInt(scanner, "Year level: ", 1);
            String semester = InputUtils.readRequiredLine(scanner, "Current semester (e.g. 2024-1): ");
            LocalDate enrollmentDate = InputUtils.readOptionalDate(scanner, "Enrollment date");
            if (enrollmentDate == null) {
                enrollmentDate = LocalDate.now();
                System.out.println("  Using today: " + enrollmentDate);
            }
            String email = InputUtils.readRequiredLine(scanner, "Email: ");
            String phone = InputUtils.readLine(scanner, "Phone (optional): ");
            boolean active = InputUtils.readYesNo(scanner, "Active?", true);

            Student s = controller.add(firstName, lastName, gender, dateOfBirth,
                    department, program, yearLevel, semester, enrollmentDate,
                    email, phone, active);
            System.out.println("  Added student " + s.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
    }

    private void updateStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID to update: ");
        Student s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
        System.out.println("  (empty keeps current value)");
        GENDER curGender = s.getGender();
        int curYear = s.getYearLevel();
        LocalDate curDob = s.getDateOfBirth();
        LocalDate curEnrollDate = s.getEnrollmentDate();
        boolean curActive = s.isActive();
        try {
            String rawFirst = InputUtils.readLine(scanner, "First name [" + TextUtils.orEmpty(s.getFirstName()) + "]: ");
            if (!rawFirst.isEmpty()) controller.setFirstName(id, rawFirst);

            String rawLast = InputUtils.readLine(scanner, "Last name [" + TextUtils.orEmpty(s.getLastName()) + "]: ");
            if (!rawLast.isEmpty()) controller.setLastName(id, rawLast);

            GENDER newGender = InputUtils.readOptionalGender(scanner, "Gender", curGender);
            if (newGender != curGender) controller.setGender(id, newGender);

            LocalDate newDob = InputUtils.readOptionalDate(scanner, "Date of birth", curDob);
            if (!sameDate(newDob, curDob)) controller.setDateOfBirth(id, newDob);

            String rawDept = InputUtils.readLine(scanner, "Department ID [" + TextUtils.orEmpty(s.getDepartment()) + "]: ");
            if (!rawDept.isEmpty()) controller.setDepartment(id, rawDept);

            String rawProg = InputUtils.readLine(scanner, "Program [" + TextUtils.orEmpty(s.getProgram()) + "]: ");
            if (!rawProg.isEmpty()) controller.setProgram(id, rawProg);

            int newYear = InputUtils.readOptionalInt(scanner, "Year level", curYear);
            if (newYear != curYear) controller.setYearLevel(id, newYear);

            String rawSem = InputUtils.readLine(scanner, "Current semester [" + TextUtils.orEmpty(s.getCurrentSemester()) + "]: ");
            if (!rawSem.isEmpty()) controller.setCurrentSemester(id, rawSem);

            LocalDate newEnrollDate = InputUtils.readOptionalDate(scanner, "Enrollment date", curEnrollDate);
            if (!sameDate(newEnrollDate, curEnrollDate)) controller.setEnrollmentDate(id, newEnrollDate);

            String rawEmail = InputUtils.readLine(scanner, "Email [" + TextUtils.orEmpty(s.getEmail()) + "]: ");
            if (!rawEmail.isEmpty()) controller.setEmail(id, rawEmail);

            String rawPhone = InputUtils.readLine(scanner, "Phone [" + TextUtils.orEmpty(s.getPhone()) + "]: ");
            if (!rawPhone.isEmpty()) controller.setPhone(id, rawPhone);

            boolean newActive = InputUtils.readYesNo(scanner, "Active?", curActive);
            if (newActive != curActive) controller.setActive(id, newActive);

            System.out.println("  Updated " + id);
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage()
                    + " — earlier fields may already be saved; re-run to fix.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void deleteStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID to delete: ");
        Student s = controller.getById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
        if (!s.isActive()) {
            System.out.println("  Already inactive.");
            return;
        }
        boolean confirm = InputUtils.readYesNo(
                scanner, "Deactivate " + s.getId() + " (" + s.getFullName() + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        try {
            controller.delete(id);
            System.out.println("  Deactivated " + id + " (row kept for records).");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (id/name/department/program/email): ");
        FormatUtils.printHeader("SEARCH RESULTS");
        printStudents(controller.search(q));
    }

    private void sortByName() {
        FormatUtils.printHeader("STUDENTS SORTED BY NAME");
        printStudents(controller.sortByName());
    }

    private void sortByGpa() {
        FormatUtils.printHeader("STUDENTS SORTED BY GPA (DESC)");
        printStudents(controller.sortByGpa());
    }

    // ===== Display helpers =====

    private void printStudents(List<Student> list) {
        String[] headers = {"ID", "Name", "Gender", "Dept", "Program", "Year", "Sem", "Email", "Active", "Cr", "GPA"};
        List<String[]> rows = new ArrayList<>();
        for (Student s : list) {
            double gpa = controller.getGpa(s.getId());
            rows.add(new String[]{
                    TextUtils.orEmpty(s.getId()),
                    s.getFullName(),
                    s.getGender() == null ? "" : s.getGender().name(),
                    TextUtils.orEmpty(s.getDepartment()),
                    TextUtils.orEmpty(s.getProgram()),
                    String.valueOf(s.getYearLevel()),
                    TextUtils.orEmpty(s.getCurrentSemester()),
                    TextUtils.orEmpty(s.getEmail()),
                    String.valueOf(s.isActive()),
                    String.valueOf(controller.getStoredCredits(s.getId())),
                    String.format(Locale.US, "%.2f", gpa)
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printProfile(Student s) {
        FormatUtils.printHeader("STUDENT PROFILE: " + s.getId());
        double gpa = controller.getGpa(s.getId());
        int computedCredits = controller.getEarnedCredits(s.getId());
        System.out.println("Name:             " + s.getFullName());
        System.out.println("Gender:           " + (s.getGender() == null ? "" : s.getGender()));
        System.out.println("Date of birth:    " + (s.getDateOfBirth() == null ? "" : s.getDateOfBirth()));
        System.out.println("Department:       " + TextUtils.orEmpty(s.getDepartment()));
        System.out.println("Program:          " + TextUtils.orEmpty(s.getProgram()));
        System.out.println("Year level:       " + s.getYearLevel());
        System.out.println("Current semester: " + TextUtils.orEmpty(s.getCurrentSemester()));
        System.out.println("Enrollment date:  " + (s.getEnrollmentDate() == null ? "" : s.getEnrollmentDate()));
        System.out.println("Email:            " + TextUtils.orEmpty(s.getEmail()));
        System.out.println("Phone:            " + TextUtils.orEmpty(s.getPhone()));
        System.out.println("Active:           " + s.isActive());
        System.out.println("GPA (computed):   " + String.format(Locale.US, "%.2f", gpa));
        System.out.println("Credits computed: " + computedCredits + " | stored: " + controller.getStoredCredits(s.getId()));
    }

    // ===== Internal helpers =====

    private static boolean sameDate(LocalDate a, LocalDate b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
