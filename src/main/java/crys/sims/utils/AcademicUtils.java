package crys.sims.utils;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Subject;
import crys.sims.model.enums.GRADE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared academic calculations. Used by views now; controllers will
 * delegate here later so the math lives in exactly one place.
 * Passing grade = anything except F (D and above pass).
 */
public final class AcademicUtils {

    /** Max credits per student per semester for registration validation. Adjustable; controllers will own this later. */
    public static final int MAX_CREDITS_PER_SEMESTER = 21;

    private AcademicUtils() {
    }

    public static double calculateGpa(String studentId, List<AcademicRecord> records, List<Subject> subjects) {
        Map<String, Integer> creditsBySubject = creditsBySubject(subjects);
        double points = 0.0;
        int credits = 0;
        for (AcademicRecord r : records) {
            if (!studentId.equals(r.getStudentId())) continue;
            if (r.getGrade() == null) continue;
            Integer c = creditsBySubject.get(r.getSubjectId());
            if (c == null || c <= 0) continue;
            points += r.getGrade().getGpaValue() * c;
            credits += c;
        }
        if (credits == 0) return 0.0;
        return points / credits;
    }

    public static int calculateEarnedCredits(String studentId, List<AcademicRecord> records, List<Subject> subjects) {
        Map<String, Integer> creditsBySubject = creditsBySubject(subjects);
        int total = 0;
        for (AcademicRecord r : records) {
            if (!studentId.equals(r.getStudentId())) continue;
            if (r.getGrade() == null || r.getGrade() == GRADE.F) continue;
            Integer c = creditsBySubject.get(r.getSubjectId());
            if (c != null) total += c;
        }
        return total;
    }

    public static int currentEnrolledCredits(String studentId, String semester,
                                               List<Enrollment> enrollments, List<Subject> subjects) {
        Map<String, Integer> creditsBySubject = creditsBySubject(subjects);
        int total = 0;
        for (Enrollment e : enrollments) {
            if (!studentId.equals(e.getStudentId())) continue;
            if (!semester.equals(e.getSemester())) continue;
            Integer c = creditsBySubject.get(e.getSubjectId());
            if (c != null) total += c;
        }
        return total;
    }

    private static Map<String, Integer> creditsBySubject(List<Subject> subjects) {
        Map<String, Integer> map = new HashMap<>();
        for (Subject s : subjects) {
            map.put(s.getId(), s.getCredits());
        }
        return map;
    }
}
