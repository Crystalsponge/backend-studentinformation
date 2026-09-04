package crys.sims.utils;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.enums.GENDER;
import crys.sims.model.enums.GRADE;
import crys.sims.model.WaitlistEntry;
import crys.sims.service.FileService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Field-level validation shared by all views (and later controllers).
 * Delimiter rules delegate to FileService so there is one source of truth.
 */
public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must not be blank");
        }
        return value.trim();
    }

    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must not be null");
        }
        return value;
    }

    public static int requireInRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must be between "
                    + min + " and " + max + ": " + value);
        }
        return value;
    }

    public static LocalDate requireValidDate(String value, String fieldName) {
        String v = requireNonBlank(value, fieldName);
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Field '" + fieldName
                    + "' must be a date (yyyy-MM-dd): " + value);
        }
    }

    public static GENDER parseGender(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Gender must be MALE or FEMALE: null");
        }
        String v = value.trim();
        if (v.equalsIgnoreCase("MALE")) return GENDER.MALE;
        if (v.equalsIgnoreCase("FEMALE")) return GENDER.FEMALE;
        throw new IllegalArgumentException("Gender must be MALE or FEMALE: " + value);
    }

    public static GRADE parseGrade(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Grade must not be blank");
        }
        GRADE grade = GRADE.fromString(value);
        if (grade == null) {
            throw new IllegalArgumentException(
                    "Grade must be one of A+, A, A-, B+, B, B-, C+, C, D, F: " + value);
        }
        return grade;
    }

    public static String cleanField(String value, String fieldName) {
        String v = requireNonBlank(value, fieldName);
        FileService.checkField(v, fieldName);
        return v;
    }

    public static String optionalField(String value, String fieldName) {
        if (value == null) return "";
        String v = value.trim();
        FileService.checkField(v, fieldName);
        return v;
    }

    public static void checkList(List<String> list, String fieldName) {
        FileService.checkListField(list, fieldName);
    }

    public static String requireValidEmail(String value) {
        String v = cleanField(value, "email");
        if (!v.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")) {
            throw new IllegalArgumentException("Field 'email' is not a valid email: " + value);
        }
        return v;
    }

    public static String optionalEmail(String value) {
        String v = optionalField(value, "email");
        if (!v.isEmpty()) {
            return requireValidEmail(v);
        }
        return v;
    }

    public static int requirePositiveInt(String value, String fieldName) {
        String v = requireNonBlank(value, fieldName);
        try {
            int n = Integer.parseInt(v);
            if (n <= 0) {
                throw new IllegalArgumentException("Field '" + fieldName + "' must be positive: " + value);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field '" + fieldName + "' must be a number: " + value);
        }
    }

    /**
     * Registration rules: student exists + active, subject exists, no duplicate
     * enrollment, not already completed (retake after F is allowed), all
     * prerequisites completed with passing grades, capacity and per-semester
     * credit limit respected, subject offered in the given semester.
     * Pure function; EnrollmentController will call this later.
     *
     * @throws IllegalArgumentException describing the first violated rule
     */
    public static void validateRegistration(String studentId, String subjectId, String semester,
                                            List<Student> students, List<Subject> subjects,
                                            List<Enrollment> enrollments, List<AcademicRecord> records) {
        Student stu = requireStudent(students, studentId);
        Subject subj = requireSubject(subjects, subjectId);
        String sem = requireSemester(semester);
        requireNotEnrolled(studentId, subjectId, enrollments, subj.getCode());
        requireNotCompleted(studentId, subjectId, records, subj.getCode());
        requirePrereqs(studentId, subj, records);
        int inSemester = 0;
        for (Enrollment e : enrollments) {
            if (subjectId.equals(e.getSubjectId()) && sem.equals(e.getSemester())) {
                inSemester++;
            }
        }
        if (inSemester + 1 > subj.getMaxCapacity()) {
            throw new IllegalArgumentException("Subject is full (" + inSemester + "/" + subj.getMaxCapacity() + ").");
        }
        int current = AcademicUtils.currentEnrolledCredits(studentId, sem, enrollments, subjects);
        if (current + subj.getCredits() > AcademicUtils.MAX_CREDITS_PER_SEMESTER) {
            throw new IllegalArgumentException("Credit limit exceeded ("
                    + current + "+" + subj.getCredits() + "/" + AcademicUtils.MAX_CREDITS_PER_SEMESTER + ").");
        }
        if (!isOfferedIn(subj, sem)) {
            throw new IllegalArgumentException(subj.getCode() + " is offered in semester '"
                    + subj.getSemesterOffered().trim() + "', not '" + sem + "'.");
        }
    }

    /**
     * Waitlist-join rules: same as registration except capacity is skipped
     * (a full subject is the reason to waitlist), plus a duplicate-waitlist check.
     *
     * @throws IllegalArgumentException describing the first violated rule
     */
    public static void validateWaitlistJoin(String studentId, String subjectId, String semester,
                                            List<Student> students, List<Subject> subjects,
                                            List<Enrollment> enrollments, List<AcademicRecord> records,
                                            List<WaitlistEntry> waitlist) {
        Student stu = requireStudent(students, studentId);
        Subject subj = requireSubject(subjects, subjectId);
        String sem = requireSemester(semester);
        requireNotEnrolled(studentId, subjectId, enrollments, subj.getCode());
        requireNotCompleted(studentId, subjectId, records, subj.getCode());
        requirePrereqs(studentId, subj, records);
        for (WaitlistEntry w : waitlist) {
            if (studentId.equals(w.getStudentId()) && subjectId.equals(w.getSubjectId())
                    && sem.equals(w.getSemester())) {
                throw new IllegalArgumentException("Already on waitlist for "
                        + subj.getCode() + " (" + sem + ").");
            }
        }
        if (!isOfferedIn(subj, sem)) {
            throw new IllegalArgumentException(subj.getCode() + " is offered in semester '"
                    + subj.getSemesterOffered().trim() + "', not '" + sem + "'.");
        }
    }

    private static Student requireStudent(List<Student> students, String studentId) {
        for (Student s : students) {
            if (studentId.equals(s.getId())) {
                if (!s.isActive()) {
                    throw new IllegalArgumentException("Student " + studentId + " is not active.");
                }
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown student ID: " + studentId + ".");
    }

    private static Subject requireSubject(List<Subject> subjects, String subjectId) {
        for (Subject s : subjects) {
            if (subjectId.equals(s.getId())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown subject ID: " + subjectId + ".");
    }

    private static String requireSemester(String semester) {
        if (semester == null || semester.trim().isEmpty()) {
            throw new IllegalArgumentException("Semester must not be blank.");
        }
        return semester.trim();
    }

    private static void requireNotEnrolled(String studentId, String subjectId,
                                           List<Enrollment> enrollments, String code) {
        for (Enrollment e : enrollments) {
            if (studentId.equals(e.getStudentId()) && subjectId.equals(e.getSubjectId())) {
                throw new IllegalArgumentException("Already enrolled in "
                        + code + " (" + e.getSemester() + ").");
            }
        }
    }

    private static void requireNotCompleted(String studentId, String subjectId,
                                            List<AcademicRecord> records, String code) {
        for (AcademicRecord r : records) {
            if (studentId.equals(r.getStudentId()) && subjectId.equals(r.getSubjectId())
                    && r.getGrade() != null && r.getGrade() != GRADE.F) {
                throw new IllegalArgumentException("Already completed "
                        + code + " with grade " + r.getGrade() + ".");
            }
        }
    }

    private static void requirePrereqs(String studentId, Subject subj, List<AcademicRecord> records) {
        List<String> missing = new ArrayList<>();
        if (subj.getPrerequisiteIds() != null) {
            for (String pre : subj.getPrerequisiteIds()) {
                boolean done = false;
                for (AcademicRecord r : records) {
                    if (studentId.equals(r.getStudentId()) && pre.equals(r.getSubjectId())
                            && r.getGrade() != null && r.getGrade() != GRADE.F) {
                        done = true;
                        break;
                    }
                }
                if (!done) missing.add(pre);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing prerequisites: " + String.join(",", missing) + ".");
        }
    }

    /**
     * Semester-offering check shared by registration validation and suggestions.
     * Matches when the offering is blank, equals the full semester, or equals
     * its suffix after '-' (e.g. offered "1" matches "2024-1").
     */
    public static boolean isOfferedIn(Subject subj, String semester) {
        if (subj == null || semester == null) return false;
        String offered = subj.getSemesterOffered() == null ? "" : subj.getSemesterOffered().trim();
        if (offered.isEmpty()) return true;
        String sem = semester.trim();
        String suffix = sem.contains("-") ? sem.substring(sem.lastIndexOf('-') + 1) : sem;
        return offered.equalsIgnoreCase(sem) || offered.equalsIgnoreCase(suffix);
    }
}
