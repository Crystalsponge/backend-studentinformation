package crys.sims.service;

import crys.sims.model.*;
import crys.sims.model.enums.GENDER;
import crys.sims.model.enums.GRADE;
import crys.sims.model.WaitlistEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FileService {

    /** Main field separator for all data files. Also rejected inside user-entered fields. */
    public static final String DELIMITER = ";";
    /** Sub-separator for list fields (e.g. prerequisiteIds). Also rejected inside user-entered fields. */
    public static final String SUB_DELIMITER = ",";

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
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            throw e;
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
            System.err.println("WARN invalid date (expected yyyy-MM-dd): " + s);
            return null;
        }
    }

    /**
     * Rejects delimiter characters in a user-entered field.
     * Views should call this on input so bad data is caught before save.
     * @throws IllegalArgumentException if value contains DELIMITER or SUB_DELIMITER
     */
    public static void checkField(String value, String fieldName) {
        if (value != null && (value.contains(DELIMITER) || value.contains(SUB_DELIMITER))) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' must not contain '" + DELIMITER + "' or '" + SUB_DELIMITER + "': " + value);
        }
    }

    /**
     * Rejects delimiter characters in each element of a list field.
     * @throws IllegalArgumentException if any element contains DELIMITER or SUB_DELIMITER
     */
    public static void checkListField(List<String> list, String fieldName) {
        if (list == null) return;
        for (String item : list) {
            checkField(item, fieldName + "[]");
        }
    }

    private static boolean parseActive(String raw, String line) {
        if (raw == null || raw.trim().isEmpty()) return true;
        String v = raw.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        System.err.println("WARN invalid boolean field 'active' ('" + raw + "'), defaulting to true: " + line);
        return true;
    }

    private static int parseRequiredInt(String raw, String fieldName, String line) {
        if (raw == null || raw.trim().isEmpty()) {
            System.err.println("WARN missing numeric field '" + fieldName + "', defaulting to 0: " + line);
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("WARN invalid numeric field '" + fieldName + "' ('" + raw + "'), defaulting to 0: " + line);
            return 0;
        }
    }

    // ===== Student: 14 fields (enrolledSubjects NOT persisted per decision B; controlled via Enrollment) =====
    // id;firstName;lastName;gender;dateOfBirth;department;program;yearLevel;currentSemester;enrollmentDate;email;phone;active;earnedCredits

    public static List<Student> loadStudents(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<Student> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 14) {
                System.err.println("WARN skip malformed student line (expected 14 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty()) {
                System.err.println("WARN skip student line with empty id: " + raw);
                continue;
            }
            if (!seenIds.add(f[0].trim())) {
                System.err.println("WARN skip duplicate student id (keeping first): " + raw);
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
                s.setYearLevel(parseRequiredInt(f[7], "yearLevel", raw));
                s.setCurrentSemester(f[8].trim());
                s.setEnrollmentDate(strToDate(f[9]));
                s.setEmail(f[10].trim());
                s.setPhone(f[11].trim());
                s.setActive(parseActive(f[12], raw));
                s.setEarnedCredits(parseRequiredInt(f[13], "earnedCredits", raw));
                result.add(s);
            } catch (Exception e) {
                System.err.println("WARN skip student line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveStudents(Path path, List<Student> students) throws IOException {
        for (Student s : students) {
            checkField(s.getId(), "Student.id");
            checkField(s.getFirstName(), "Student.firstName");
            checkField(s.getLastName(), "Student.lastName");
            checkField(s.getDepartment(), "Student.department");
            checkField(s.getProgram(), "Student.program");
            checkField(s.getCurrentSemester(), "Student.currentSemester");
            checkField(s.getEmail(), "Student.email");
            checkField(s.getPhone(), "Student.phone");
        }
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
        Set<String> seenIds = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 8) {
                System.err.println("WARN skip malformed subject line (expected 8 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty()) {
                System.err.println("WARN skip subject line with empty id: " + raw);
                continue;
            }
            if (!seenIds.add(f[0].trim())) {
                System.err.println("WARN skip duplicate subject id (keeping first): " + raw);
                continue;
            }
            try {
                Subject sub = new Subject();
                sub.setId(f[0].trim());
                sub.setCode(f[1].trim());
                sub.setName(f[2].trim());
                sub.setCredits(parseRequiredInt(f[3], "credits", raw));
                sub.setDepartment(f[4].trim());
                sub.setPrerequisiteIds(splitSub(f[5]));
                sub.setSemesterOffered(f[6].trim());
                sub.setMaxCapacity(parseRequiredInt(f[7], "maxCapacity", raw));
                result.add(sub);
            } catch (Exception e) {
                System.err.println("WARN skip subject line parse error: " + raw + " -> " + e.getMessage());
            }
        }
        return result;
    }

    public static void saveSubjects(Path path, List<Subject> subjects) throws IOException {
        for (Subject sub : subjects) {
            checkField(sub.getId(), "Subject.id");
            checkField(sub.getCode(), "Subject.code");
            checkField(sub.getName(), "Subject.name");
            checkField(sub.getDepartment(), "Subject.department");
            checkListField(sub.getPrerequisiteIds(), "Subject.prerequisiteIds");
            checkField(sub.getSemesterOffered(), "Subject.semesterOffered");
        }
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
        Set<String> seenIds = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 3) {
                System.err.println("WARN skip malformed faculty line (expected 3 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty()) {
                System.err.println("WARN skip faculty line with empty id: " + raw);
                continue;
            }
            if (!seenIds.add(f[0].trim())) {
                System.err.println("WARN skip duplicate faculty id (keeping first): " + raw);
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
        for (Faculty fac : faculties) {
            checkField(fac.getId(), "Faculty.id");
            checkField(fac.getName(), "Faculty.name");
            checkListField(fac.getDepartmentIds(), "Faculty.departmentIds");
        }
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
        Set<String> seenIds = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 3) {
                System.err.println("WARN skip malformed department line (expected 3 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty()) {
                System.err.println("WARN skip department line with empty id: " + raw);
                continue;
            }
            if (!seenIds.add(f[0].trim())) {
                System.err.println("WARN skip duplicate department id (keeping first): " + raw);
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
        for (Department d : departments) {
            checkField(d.getId(), "Department.id");
            checkField(d.getName(), "Department.name");
            checkField(d.getFacultyId(), "Department.facultyId");
        }
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
        Set<String> seenKeys = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 4) {
                System.err.println("WARN skip malformed enrollment line (expected 4 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty() || f[1].trim().isEmpty()) {
                System.err.println("WARN skip enrollment line with empty studentId/subjectId: " + raw);
                continue;
            }
            // Retake rule: same student+subject+semester is one enrollment.
            // A retake must be recorded under a distinct semester key.
            if (!seenKeys.add(f[0].trim() + "|" + f[1].trim() + "|" + f[2].trim())) {
                System.err.println("WARN skip duplicate enrollment (keeping first): " + raw);
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
        for (Enrollment e : enrollments) {
            checkField(e.getStudentId(), "Enrollment.studentId");
            checkField(e.getSubjectId(), "Enrollment.subjectId");
            checkField(e.getSemester(), "Enrollment.semester");
        }
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
        Set<String> seenKeys = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 4) {
                System.err.println("WARN skip malformed academic_record line (expected 4 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty() || f[1].trim().isEmpty()) {
                System.err.println("WARN skip academic_record line with empty studentId/subjectId: " + raw);
                continue;
            }
            // Retake rule: same student+subject+semester keeps the first grade.
            // A retake grade must be recorded under a distinct semester key.
            if (!seenKeys.add(f[0].trim() + "|" + f[1].trim() + "|" + f[2].trim())) {
                System.err.println("WARN skip duplicate academic_record (keeping first): " + raw);
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
        for (AcademicRecord r : records) {
            checkField(r.getStudentId(), "AcademicRecord.studentId");
            checkField(r.getSubjectId(), "AcademicRecord.subjectId");
            checkField(r.getSemester(), "AcademicRecord.semester");
        }
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

    // ===== Waitlist: 4 fields =====
    // studentId;subjectId;semester;requestDate

    public static List<WaitlistEntry> loadWaitlist(Path path) throws IOException {
        List<String> lines = readLines(path);
        List<WaitlistEntry> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;
            String[] f = raw.split(DELIMITER, -1);
            if (f.length != 4) {
                System.err.println("WARN skip malformed waitlist line (expected 4 fields): " + raw);
                continue;
            }
            if (f[0].trim().isEmpty() || f[1].trim().isEmpty()) {
                System.err.println("WARN skip waitlist line with empty studentId/subjectId: " + raw);
                continue;
            }
            if (!seenKeys.add(f[0].trim() + "|" + f[1].trim() + "|" + f[2].trim())) {
                System.err.println("WARN skip duplicate waitlist entry (keeping first): " + raw);
                continue;
            }
            try {
                WaitlistEntry w = new WaitlistEntry();
                w.setStudentId(f[0].trim());
                w.setSubjectId(f[1].trim());
                w.setSemester(f[2].trim());
                w.setRequestDate(strToDate(f[3]));
                result.add(w);
            } catch (Exception ex) {
                System.err.println("WARN skip waitlist line parse error: " + raw + " -> " + ex.getMessage());
            }
        }
        return result;
    }

    public static void saveWaitlist(Path path, List<WaitlistEntry> entries) throws IOException {
        for (WaitlistEntry w : entries) {
            checkField(w.getStudentId(), "WaitlistEntry.studentId");
            checkField(w.getSubjectId(), "WaitlistEntry.subjectId");
            checkField(w.getSemester(), "WaitlistEntry.semester");
        }
        List<String> lines = new ArrayList<>();
        for (WaitlistEntry w : entries) {
            String line = String.join(DELIMITER,
                    nn(w.getStudentId()),
                    nn(w.getSubjectId()),
                    nn(w.getSemester()),
                    dateToStr(w.getRequestDate())
            );
            lines.add(line);
        }
        writeLinesAtomic(path, lines);
    }
}
