package crys.sims.view;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GENDER;
import crys.sims.service.FileService;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Console UI for Student CRUD + search + sort.
 * Temporary: talks directly to the in-memory list + FileService.
 * A StudentController will take over business logic later; this view keeps only I/O.
 */
public class StudentView {

    private final List<Student> students;
    private final Path filePath;
    private final List<AcademicRecord> records;
    private final List<Subject> subjects;
    private final Scanner scanner;

    public StudentView(List<Student> students, Path filePath,
                       List<AcademicRecord> records, List<Subject> subjects,
                       Scanner scanner) {
        this.students = students;
        this.filePath = filePath;
        this.records = records;
        this.subjects = subjects;
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

    // ===== Actions =====

    private void listAll() {
        FormatUtils.printHeader("ALL STUDENTS");
        printStudents(students);
    }

    private void addStudent() {
        FormatUtils.printHeader("ADD STUDENT");
        try {
            List<String> ids = students.stream().map(Student::getId).collect(Collectors.toList());
            String id = IdGenerator.nextId(ids, "S", 3);
            System.out.println("Assigned ID: " + id);

            String firstName = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "First name: "), "firstName");
            String lastName = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Last name: "), "lastName");
            GENDER gender = InputUtils.readGender(scanner, "Gender");
            LocalDate dateOfBirth = InputUtils.readOptionalDate(scanner, "Date of birth");
            String department = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Department ID (e.g. D001): "), "department");
            String program = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Program (e.g. BSIS): "), "program");
            int yearLevel = InputUtils.readInt(scanner, "Year level: ", 1);
            String semester = ValidationUtils.cleanField(
                    InputUtils.readRequiredLine(scanner, "Current semester (e.g. 2024-1): "), "currentSemester");
            LocalDate enrollmentDate = InputUtils.readOptionalDate(scanner, "Enrollment date");
            if (enrollmentDate == null) {
                enrollmentDate = LocalDate.now();
                System.out.println("  Using today: " + enrollmentDate);
            }
            String email = ValidationUtils.requireValidEmail(
                    InputUtils.readRequiredLine(scanner, "Email: "));
            String phone = ValidationUtils.optionalField(
                    InputUtils.readLine(scanner, "Phone (optional): "), "phone");
            boolean active = InputUtils.readYesNo(scanner, "Active?", true);

            Student s = new Student(id, firstName, lastName, gender, dateOfBirth,
                    department, program, yearLevel, semester, enrollmentDate,
                    email, phone, active, 0);
            students.add(s);
            if (save()) {
                System.out.println("  Added student " + id);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage());
        }
    }

    private void viewById() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
    }

    private void updateStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID to update: ");
        Student s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
        System.out.println("  (empty keeps current value)");
        try {
            // Gather + validate everything first, so a bad value aborts
            // before any field is changed.
            String rawFirst = InputUtils.readLine(scanner, "First name [" + text(s.getFirstName()) + "]: ");
            String newFirst = rawFirst.isEmpty() ? s.getFirstName()
                    : ValidationUtils.cleanField(rawFirst, "firstName");

            String rawLast = InputUtils.readLine(scanner, "Last name [" + text(s.getLastName()) + "]: ");
            String newLast = rawLast.isEmpty() ? s.getLastName()
                    : ValidationUtils.cleanField(rawLast, "lastName");

            GENDER newGender = InputUtils.readOptionalGender(scanner, "Gender", s.getGender());
            LocalDate newDob = InputUtils.readOptionalDate(scanner, "Date of birth", s.getDateOfBirth());

            String rawDept = InputUtils.readLine(scanner, "Department ID [" + text(s.getDepartment()) + "]: ");
            String newDept = rawDept.isEmpty() ? s.getDepartment()
                    : ValidationUtils.cleanField(rawDept, "department");

            String rawProg = InputUtils.readLine(scanner, "Program [" + text(s.getProgram()) + "]: ");
            String newProg = rawProg.isEmpty() ? s.getProgram()
                    : ValidationUtils.cleanField(rawProg, "program");

            int newYear = InputUtils.readOptionalInt(scanner, "Year level", s.getYearLevel());

            String rawSem = InputUtils.readLine(scanner, "Current semester [" + text(s.getCurrentSemester()) + "]: ");
            String newSem = rawSem.isEmpty() ? s.getCurrentSemester()
                    : ValidationUtils.cleanField(rawSem, "currentSemester");

            LocalDate newEnrollDate = InputUtils.readOptionalDate(scanner, "Enrollment date", s.getEnrollmentDate());

            String rawEmail = InputUtils.readLine(scanner, "Email [" + text(s.getEmail()) + "]: ");
            String newEmail = rawEmail.isEmpty() ? s.getEmail()
                    : ValidationUtils.requireValidEmail(rawEmail);

            String rawPhone = InputUtils.readLine(scanner, "Phone [" + text(s.getPhone()) + "]: ");
            String newPhone = rawPhone.isEmpty() ? s.getPhone()
                    : ValidationUtils.optionalField(rawPhone, "phone");

            boolean newActive = InputUtils.readYesNo(scanner, "Active?", s.isActive());

            s.setFirstName(newFirst);
            s.setLastName(newLast);
            s.setGender(newGender);
            s.setDateOfBirth(newDob);
            s.setDepartment(newDept);
            s.setProgram(newProg);
            s.setYearLevel(newYear);
            s.setCurrentSemester(newSem);
            s.setEnrollmentDate(newEnrollDate);
            s.setEmail(newEmail);
            s.setPhone(newPhone);
            s.setActive(newActive);

            if (save()) {
                System.out.println("  Updated " + s.getId());
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Invalid input: " + e.getMessage() + " — nothing changed.");
        }
    }

    private void deleteStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID to delete: ");
        Student s = findById(id);
        if (s == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        printProfile(s);
        boolean confirm = InputUtils.readYesNo(
                scanner, "Delete " + s.getId() + " (" + s.getFullName() + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        students.remove(s);
        if (save()) {
            System.out.println("  Deleted " + id);
            System.out.println("  Note: enrollments/records for this student are kept (cleanup comes with controllers).");
        }
    }

    private void search() {
        String q = InputUtils.readRequiredLine(scanner, "Search (id/name/department/program/email): ")
                .toLowerCase(Locale.ROOT);
        List<Student> hits = new ArrayList<>();
        for (Student s : students) {
            if (contains(s.getId(), q)
                    || contains(s.getFullName(), q)
                    || contains(s.getDepartment(), q)
                    || contains(s.getProgram(), q)
                    || contains(s.getEmail(), q)) {
                hits.add(s);
            }
        }
        FormatUtils.printHeader("SEARCH RESULTS");
        printStudents(hits);
    }

    private void sortByName() {
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort(Comparator
                .comparing(Student::getLastName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(Student::getFirstName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        FormatUtils.printHeader("STUDENTS SORTED BY NAME");
        printStudents(sorted);
    }

    private void sortByGpa() {
        Map<String, Double> gpaById = new HashMap<>();
        for (Student s : students) {
            gpaById.put(s.getId(), AcademicUtils.calculateGpa(s.getId(), records, subjects));
        }
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort((a, b) -> {
            int cmp = Double.compare(gpaById.get(b.getId()), gpaById.get(a.getId()));
            if (cmp != 0) return cmp;
            cmp = compareNullable(a.getLastName(), b.getLastName());
            if (cmp != 0) return cmp;
            return compareNullable(a.getFirstName(), b.getFirstName());
        });
        FormatUtils.printHeader("STUDENTS SORTED BY GPA (DESC)");
        printStudents(sorted);
    }

    // ===== Display helpers =====

    private void printStudents(List<Student> list) {
        String[] headers = {"ID", "Name", "Gender", "Dept", "Program", "Year", "Sem", "Email", "Active", "Cr", "GPA"};
        List<String[]> rows = new ArrayList<>();
        for (Student s : list) {
            double gpa = AcademicUtils.calculateGpa(s.getId(), records, subjects);
            rows.add(new String[]{
                    text(s.getId()),
                    s.getFullName(),
                    s.getGender() == null ? "" : s.getGender().name(),
                    text(s.getDepartment()),
                    text(s.getProgram()),
                    String.valueOf(s.getYearLevel()),
                    text(s.getCurrentSemester()),
                    text(s.getEmail()),
                    String.valueOf(s.isActive()),
                    String.valueOf(s.getEarnedCredits()),
                    String.format(Locale.US, "%.2f", gpa)
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void printProfile(Student s) {
        FormatUtils.printHeader("STUDENT PROFILE: " + s.getId());
        double gpa = AcademicUtils.calculateGpa(s.getId(), records, subjects);
        int computedCredits = AcademicUtils.calculateEarnedCredits(s.getId(), records, subjects);
        System.out.println("Name:             " + s.getFullName());
        System.out.println("Gender:           " + (s.getGender() == null ? "" : s.getGender()));
        System.out.println("Date of birth:    " + (s.getDateOfBirth() == null ? "" : s.getDateOfBirth()));
        System.out.println("Department:       " + text(s.getDepartment()));
        System.out.println("Program:          " + text(s.getProgram()));
        System.out.println("Year level:       " + s.getYearLevel());
        System.out.println("Current semester: " + text(s.getCurrentSemester()));
        System.out.println("Enrollment date:  " + (s.getEnrollmentDate() == null ? "" : s.getEnrollmentDate()));
        System.out.println("Email:            " + text(s.getEmail()));
        System.out.println("Phone:            " + text(s.getPhone()));
        System.out.println("Active:           " + s.isActive());
        System.out.println("GPA (computed):   " + String.format(Locale.US, "%.2f", gpa));
        System.out.println("Credits computed: " + computedCredits + " | stored: " + s.getEarnedCredits());
    }

    // ===== Internal helpers =====

    private Student findById(String id) {
        for (Student s : students) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private boolean save() {
        try {
            FileService.saveStudents(filePath, students);
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

    private static int compareNullable(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareToIgnoreCase(b);
    }
}
