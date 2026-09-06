package crys.sims.controller;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GENDER;
import crys.sims.service.FileService;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.TextUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic + persistence for students. Owns the in-memory list;
 * views call these methods and never touch FileService directly.
 * Every mutation validates, then saves. Delete is soft (active=false).
 * GPA/earned credits are computed live; the stored earnedCredits field
 * stays a GradeController-synced cache (no setter here on purpose).
 */
public class StudentController {

    private static final Comparator<String> NULL_SAFE_ORDER =
            Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER);

    private final List<Student> students;
    private final Path filePath;
    private final List<AcademicRecord> records;
    private final List<Subject> subjects;

    public StudentController(List<Student> students, Path filePath,
                             List<AcademicRecord> records, List<Subject> subjects) {
        this.students = students;
        this.filePath = filePath;
        this.records = records;
        this.subjects = subjects;
    }

    // ===== Reads (active students only, except getById) =====

    public List<Student> getAllActive() {
        List<Student> out = new ArrayList<>();
        for (Student s : students) {
            if (s.isActive()) out.add(s);
        }
        return out;
    }

    public List<Student> getInactive() {
        List<Student> out = new ArrayList<>();
        for (Student s : students) {
            if (!s.isActive()) out.add(s);
        }
        return out;
    }

    public Student getById(String id) {
        if (id == null) return null;
        for (Student s : students) {
            if (id.equalsIgnoreCase(s.getId())) return s;
        }
        return null;
    }

    public double getGpa(String id) {
        Student s = requireById(id);
        return AcademicUtils.calculateGpa(s.getId(), records, subjects);
    }

    public int getEarnedCredits(String id) {
        Student s = requireById(id);
        return AcademicUtils.calculateEarnedCredits(s.getId(), records, subjects);
    }

    public int getStoredCredits(String id) {
        return requireById(id).getEarnedCredits();
    }

    public List<Student> search(String query) {
        String q = query == null ? "" : query;
        List<Student> hits = new ArrayList<>();
        for (Student s : students) {
            if (!s.isActive()) continue;
            if (TextUtils.containsIgnoreCase(s.getId(), q)
                    || TextUtils.containsIgnoreCase(s.getFullName(), q)
                    || TextUtils.containsIgnoreCase(s.getDepartment(), q)
                    || TextUtils.containsIgnoreCase(s.getProgram(), q)
                    || TextUtils.containsIgnoreCase(s.getEmail(), q)) {
                hits.add(s);
            }
        }
        return hits;
    }

    public List<Student> sortByName() {
        List<Student> sorted = getAllActive();
        sorted.sort(Comparator
                .comparing(Student::getLastName, NULL_SAFE_ORDER)
                .thenComparing(Student::getFirstName, NULL_SAFE_ORDER));
        return sorted;
    }

    public List<Student> sortByGpa() {
        Map<String, Double> gpaById = new HashMap<>();
        for (Student s : students) {
            if (s.isActive()) {
                gpaById.put(s.getId(), AcademicUtils.calculateGpa(s.getId(), records, subjects));
            }
        }
        List<Student> sorted = getAllActive();
        sorted.sort((a, b) -> {
            int cmp = Double.compare(gpaById.get(b.getId()), gpaById.get(a.getId()));
            if (cmp != 0) return cmp;
            cmp = NULL_SAFE_ORDER.compare(a.getLastName(), b.getLastName());
            if (cmp != 0) return cmp;
            return NULL_SAFE_ORDER.compare(a.getFirstName(), b.getFirstName());
        });
        return sorted;
    }

    // ===== Create (ID auto-generated) =====

    public Student add(String firstName, String lastName, GENDER gender,
                       LocalDate dateOfBirth, String department, String program,
                       int yearLevel, String semester, LocalDate enrollmentDate,
                       String email, String phone, boolean active) throws IOException {
        String cleanFirst = ValidationUtils.cleanField(firstName, "firstName");
        String cleanLast = ValidationUtils.cleanField(lastName, "lastName");
        GENDER g = ValidationUtils.requireNonNull(gender, "gender");
        String cleanDept = ValidationUtils.cleanField(department, "department");
        String cleanProg = ValidationUtils.cleanField(program, "program");
        if (yearLevel < 1) {
            throw new IllegalArgumentException("Field 'yearLevel' must be >= 1: " + yearLevel);
        }
        String cleanSem = ValidationUtils.cleanField(semester, "currentSemester");
        String cleanEmail = ValidationUtils.requireValidEmail(email);
        String cleanPhone = ValidationUtils.optionalField(phone, "phone");
        List<String> ids = students.stream().map(Student::getId).collect(Collectors.toList());
        String id = IdGenerator.nextId(ids, "S", 3);
        Student s = new Student(id, cleanFirst, cleanLast, g, dateOfBirth,
                cleanDept, cleanProg, yearLevel, cleanSem, enrollmentDate,
                cleanEmail, cleanPhone, active, 0);
        students.add(s);
        save();
        return s;
    }

    // ===== Field-by-field update (each validates + saves) =====

    public void setFirstName(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setFirstName(ValidationUtils.cleanField(value, "firstName"));
        save();
    }

    public void setLastName(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setLastName(ValidationUtils.cleanField(value, "lastName"));
        save();
    }

    public void setGender(String id, GENDER value) throws IOException {
        Student s = requireById(id);
        s.setGender(ValidationUtils.requireNonNull(value, "gender"));
        save();
    }

    public void setDateOfBirth(String id, LocalDate value) throws IOException {
        Student s = requireById(id);
        s.setDateOfBirth(value);
        save();
    }

    public void setDepartment(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setDepartment(ValidationUtils.cleanField(value, "department"));
        save();
    }

    public void setProgram(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setProgram(ValidationUtils.cleanField(value, "program"));
        save();
    }

    public void setYearLevel(String id, int value) throws IOException {
        if (value < 1) {
            throw new IllegalArgumentException("Field 'yearLevel' must be >= 1: " + value);
        }
        Student s = requireById(id);
        s.setYearLevel(value);
        save();
    }

    public void setCurrentSemester(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setCurrentSemester(ValidationUtils.cleanField(value, "currentSemester"));
        save();
    }

    public void setEnrollmentDate(String id, LocalDate value) throws IOException {
        Student s = requireById(id);
        s.setEnrollmentDate(value);
        save();
    }

    public void setEmail(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setEmail(ValidationUtils.requireValidEmail(value));
        save();
    }

    public void setPhone(String id, String value) throws IOException {
        Student s = requireById(id);
        s.setPhone(ValidationUtils.optionalField(value, "phone"));
        save();
    }

    public void setActive(String id, boolean value) throws IOException {
        Student s = requireById(id);
        s.setActive(value);
        save();
    }

    // ===== Soft delete =====

    public void delete(String id) throws IOException {
        Student s = requireById(id);
        s.setActive(false);
        save();
    }

    // ===== Internals =====

    private Student requireById(String id) {
        Student s = getById(id);
        if (s == null) {
            throw new IllegalArgumentException("Student not found: " + id);
        }
        return s;
    }

    private void save() throws IOException {
        FileService.saveStudents(filePath, students);
    }
}
