package crys.sims.service;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GENDER;
import crys.sims.model.enums.GRADE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class FileService {

    private static final String DELIMITER = ";";
    private static final String SUB_DELIMITER = ",";

    private FileService() {
    }

    // ===== Helpers =====

    private static List<String> readLines(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        if (!Files.exists(path)) {
            Files.createFile(path);
            return new ArrayList<>();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    private static void writeLinesAtomic(Path path, List<String> lines) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String joinSub(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream().map(s -> s == null ? "" : s.trim()).collect(Collectors.joining(SUB_DELIMITER));
    }

    private static List<String> splitSub(String field) {
        if (field == null || field.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(field.split(SUB_DELIMITER, -1))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static String dateToStr(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    private static LocalDate strToDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ===== Student: 14 fields (enrolledSubjects NOT persisted per decision B; controlled via Enrollment) =====
    // id;firstName;lastName;gender;dateOfBirth;department;program;yearLevel;currentSemester;enrollmentDate;email;phone;active;earnedCredits

    public static List<Student> loadStudents(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Student> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 14) {
                System.err.println("WARN skip malformed student line (expected 14 fields): " + raw);
                continue;
            }
            try {
                Student s = new Student();
                s.setId(f[0].trim());
                s.setFirstName(f[1].trim());
                s.setLastName(f[2].trim());
                String g = f[3].trim();
                if (!g.isEmpty()) {
                    try { s.setGender(GENDER.valueOf(g.toUpperCase())); } catch (IllegalArgumentException e) { System.err.println("WARN invalid gender: " + g); }
                }
                s.setDateOfBirth(strToDate(f[4]));
                s.setDepartment(f[5].trim());
                s.setProgram(f[6].trim());
                s.setYearLevel(f[7].trim().isEmpty() ? 0 : Integer.parseInt(f[7].trim()));
                s.setCurrentSemester(f[8].trim());
                s.setEnrollmentDate(strToDate(f[9]));
                s.setEmail(f[10].trim());
                s.setPhone(f[11].trim());
                s.setActive(f[12].trim().isEmpty() ? true : Boolean.parseBoolean(f[12].trim()));
                s.setEarnedCredits(f[13].trim().isEmpty() ? 0 : Integer.parseInt(f[13].trim()));
                result.add(s);
            } catch (Exception e) {
                System.err.println("WARN skip student line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveStudents(Path path, List<Student> students) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Student s : students) {
            String line = String.join(DELIMITER,
                    nn(s.getId()),
                    nn(s.getFirstName()),
                    nn(s.getLastName()),
                    s.getGender() == null ? "" : s.getGender().name(),
                    dateToStr(s.getDateOfBirth()),
                    nn(s.getDepartment()),
                    nn(s.getProgram()),
                    String.valueOf(s.getYearLevel()),
                    nn(s.getCurrentSemester()),
                    dateToStr(s.getEnrollmentDate()),
                    nn(s.getEmail()),
                    nn(s.getPhone()),
                    String.valueOf(s.isActive()),
                    String.valueOf(s.getEarnedCredits())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }

    // ===== Subject: 8 fields =====
    // id;code;name;credits;department;prerequisiteIds(,);semesterOffered;maxCapacity

    public static List<Subject> loadSubjects(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Subject> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 8) {
                System.err.println("WARN skip malformed subject line (expected 8 fields): " + raw);
                continue;
            }
            try {
                Subject sub = new Subject();
                sub.setId(f[0].trim());
                sub.setCode(f[1].trim());
                sub.setName(f[2].trim());
                sub.setCredits(f[3].trim().isEmpty() ? 0 : Integer.parseInt(f[3].trim()));
                sub.setDepartment(f[4].trim());
                sub.setPrerequisiteIds(splitSub(f[5]));
                sub.setSemesterOffered(f[6].trim());
                sub.setMaxCapacity(f[7].trim().isEmpty() ? 0 : Integer.parseInt(f[7].trim()));
                result.add(sub);
            } catch (Exception e) {
                System.err.println("WARN skip subject line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveSubjects(Path path, List<Subject> subjects) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Subject sub : subjects) {
            String line = String.join(DELIMITER,
                    nn(sub.getId()),
                    nn(sub.getCode()),
                    nn(sub.getName()),
                    String.valueOf(sub.getCredits()),
                    nn(sub.getDepartment()),
                    joinSub(sub.getPrerequisiteIds()),
                    nn(sub.getSemesterOffered()),
                    String.valueOf(sub.getMaxCapacity())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }

    // ===== Faculty: 3 fields =====
    // id;name;departmentIds(,)

    public static List<Faculty> loadFaculties(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Faculty> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 3) {
                System.err.println("WARN skip malformed faculty line (expected 3 fields): " + raw);
                continue;
            }
            try {
                Faculty fac = new Faculty();
                fac.setId(f[0].trim());
                fac.setName(f[1].trim());
                fac.setDepartmentIds(splitSub(f[2]));
                result.add(fac);
            } catch (Exception e) {
                System.err.println("WARN skip faculty line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveFaculties(Path path, List<Faculty> faculties) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Faculty fac : faculties) {
            String line = String.join(DELIMITER,
                    nn(fac.getId()),
                    nn(fac.getName()),
                    joinSub(fac.getDepartmentIds())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }

    // ===== Department: 3 fields =====
    // id;name;facultyId

    public static List<Department> loadDepartments(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Department> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 3) {
                System.err.println("WARN skip malformed department line (expected 3 fields): " + raw);
                continue;
            }
            try {
                Department d = new Department();
                d.setId(f[0].trim());
                d.setName(f[1].trim());
                d.setFacultyId(f[2].trim());
                result.add(d);
            } catch (Exception e) {
                System.err.println("WARN skip department line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveDepartments(Path path, List<Department> departments) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Department d : departments) {
            String line = String.join(DELIMITER,
                    nn(d.getId()),
                    nn(d.getName()),
                    nn(d.getFacultyId())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }

    // ===== Enrollment: 4 fields =====
    // studentId;subjectId;semester;enrollmentDate

    public static List<Enrollment> loadEnrollments(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Enrollment> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 4) {
                System.err.println("WARN skip malformed enrollment line (expected 4 fields): " + raw);
                continue;
            }
            try {
                Enrollment e = new Enrollment();
                e.setStudentId(f[0].trim());
                e.setSubjectId(f[1].trim());
                e.setSemester(f[2].trim());
                e.setEnrollmentDate(strToDate(f[3]));
                result.add(e);
            } catch (Exception ex) {
                System.err.println("WARN skip enrollment line parse error: " + raw + " -> " + ex.getMessage());
            }
        }
        return result;
    }

    public static void saveEnrollments(Path path, List<Enrollment> enrollments) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Enrollment e : enrollments) {
            String line = String.join(DELIMITER,
                    nn(e.getStudentId()),
                    nn(e.getSubjectId()),
                    nn(e.getSemester()),
                    dateToStr(e.getEnrollmentDate())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }

    // ===== AcademicRecord: 4 fields =====
    // studentId;subjectId;semester;grade (GRADE constant name e.g. A_PLUS)

    public static List<AcademicRecord> loadAcademicRecords(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<AcademicRecord> result = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 4) {
                System.err.println("WARN skip malformed academic_record line (expected 4 fields): " + raw);
                continue;
            }
            try {
                AcademicRecord r = new AcademicRecord();
                r.setStudentId(f[0].trim());
                r.setSubjectId(f[1].trim());
                r.setSemester(f[2].trim());
                String g = f[3].trim();
                if (!g.isEmpty()) {
                    GRADE grade = GRADE.fromString(g);
                    if (grade == null) {
                        try { grade = GRADE.valueOf(g.toUpperCase()); } catch (IllegalArgumentException ignored) {}
                    }
                    r.setGrade(grade);
                    if (grade == null) {
                        System.err.println("WARN invalid grade: " + g + " in line: " + raw);
                    }
                }
                result.add(r);
            } catch (Exception ex) {
                System.err.println("WARN skip academic_record line parse error: " + raw + " -> " + ex.getMessage());
            }
        }
        return result;
    }

    public static void saveAcademicRecords(Path path, List<AcademicRecord> records) throws IOException {
        List<String> lines = new ArrayList<>();
        for (AcademicRecord r : records) {
            String line = String.join(DELIMITER,
                    nn(r.getStudentId()),
                    nn(r.getSubjectId()),
                    nn(r.getSemester()),
                    r.getGrade() == null ? "" : r.getGrade().name()
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }
}
