package crys.sims.controller;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.service.FileService;
import crys.sims.service.WaitlistService;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.TextUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic + persistence for subjects. Owns the in-memory list;
 * views call these methods and never touch FileService directly.
 * Every mutation validates, then saves. Delete is hard but guarded:
 * subjects referenced by enrollments, grade records, or waiters cannot
 * be deleted (protects transcript integrity).
 */
public class SubjectController {

    private final List<Subject> subjects;
    private final Path filePath;
    private final List<Enrollment> enrollments;
    private final List<AcademicRecord> records;
    private final WaitlistService waitlist;

    public SubjectController(List<Subject> subjects, Path filePath,
                             List<Enrollment> enrollments, List<AcademicRecord> records,
                             WaitlistService waitlist) {
        this.subjects = subjects;
        this.filePath = filePath;
        this.enrollments = enrollments;
        this.records = records;
        this.waitlist = waitlist;
    }

    // ===== Reads =====

    public List<Subject> getAll() {
        return new ArrayList<>(subjects);
    }

    public Subject getById(String id) {
        if (id == null) return null;
        for (Subject s : subjects) {
            if (id.equalsIgnoreCase(s.getId())) return s;
        }
        return null;
    }

    public Subject getByCode(String code) {
        if (code == null) return null;
        for (Subject s : subjects) {
            if (code.equalsIgnoreCase(s.getCode())) return s;
        }
        return null;
    }

    public List<Subject> search(String query) {
        String q = query == null ? "" : query;
        List<Subject> hits = new ArrayList<>();
        for (Subject s : subjects) {
            if (TextUtils.containsIgnoreCase(s.getId(), q)
                    || TextUtils.containsIgnoreCase(s.getCode(), q)
                    || TextUtils.containsIgnoreCase(s.getName(), q)
                    || TextUtils.containsIgnoreCase(s.getDepartment(), q)) {
                hits.add(s);
            }
        }
        return hits;
    }

    public List<Subject> sortByCode() {
        List<Subject> sorted = new ArrayList<>(subjects);
        sorted.sort(Comparator.comparing(Subject::getCode,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return sorted;
    }

    public List<Subject> sortByName() {
        List<Subject> sorted = new ArrayList<>(subjects);
        sorted.sort(Comparator.comparing(Subject::getName,
                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
        return sorted;
    }

    public int getEnrollmentCount(String subjectId, String semester) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())
                    && semester.equals(e.getSemester())) {
                n++;
            }
        }
        return n;
    }

    public int getTotalEnrolled(String subjectId) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())) {
                n++;
            }
        }
        return n;
    }

    // ===== Create (ID auto-generated) =====

    public Subject add(String code, String name, int credits, String department,
                       List<String> prerequisiteIds, String semesterOffered,
                       int maxCapacity) throws IOException {
        String cleanCode = ValidationUtils.cleanField(code, "code");
        checkCodeUnique(cleanCode, null);
        String cleanName = ValidationUtils.cleanField(name, "name");
        if (credits < 0) {
            throw new IllegalArgumentException("Field 'credits' must be >= 0: " + credits);
        }
        String cleanDept = ValidationUtils.cleanField(department, "department");
        String cleanSem = ValidationUtils.cleanField(semesterOffered, "semesterOffered");
        if (maxCapacity < 1) {
            throw new IllegalArgumentException("Field 'maxCapacity' must be >= 1: " + maxCapacity);
        }
        List<String> ids = subjects.stream().map(Subject::getId).collect(Collectors.toList());
        String id = IdGenerator.nextId(ids, "SUBJ", 3);
        List<String> cleanPrereqs = cleanPrerequisites(prerequisiteIds, id);
        Subject s = new Subject(id, cleanCode, cleanName, credits, cleanDept,
                cleanPrereqs, cleanSem, maxCapacity);
        subjects.add(s);
        save();
        return s;
    }

    // ===== Field-by-field update (each validates + saves) =====

    public void setCode(String id, String value) throws IOException {
        Subject s = requireById(id);
        String clean = ValidationUtils.cleanField(value, "code");
        checkCodeUnique(clean, s.getId());
        s.setCode(clean);
        save();
    }

    public void setName(String id, String value) throws IOException {
        Subject s = requireById(id);
        s.setName(ValidationUtils.cleanField(value, "name"));
        save();
    }

    public void setCredits(String id, int value) throws IOException {
        if (value < 0) {
            throw new IllegalArgumentException("Field 'credits' must be >= 0: " + value);
        }
        Subject s = requireById(id);
        s.setCredits(value);
        save();
    }

    public void setDepartment(String id, String value) throws IOException {
        Subject s = requireById(id);
        s.setDepartment(ValidationUtils.cleanField(value, "department"));
        save();
    }

    public void setPrerequisiteIds(String id, List<String> value) throws IOException {
        Subject s = requireById(id);
        s.setPrerequisiteIds(cleanPrerequisites(value, s.getId()));
        save();
    }

    public void setSemesterOffered(String id, String value) throws IOException {
        Subject s = requireById(id);
        s.setSemesterOffered(ValidationUtils.cleanField(value, "semesterOffered"));
        save();
    }

    public void setMaxCapacity(String id, int value) throws IOException {
        if (value < 1) {
            throw new IllegalArgumentException("Field 'maxCapacity' must be >= 1: " + value);
        }
        Subject s = requireById(id);
        s.setMaxCapacity(value);
        save();
    }

    // ===== Guarded hard delete =====

    public void delete(String id) throws IOException {
        Subject s = requireById(id);
        String reason = getDeleteBlockReason(s.getId());
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        subjects.remove(s);
        save();
    }

    /**
     * @return null when the subject can be deleted, otherwise the reason why not.
     */
    public String getDeleteBlockReason(String id) {
        Subject s = requireById(id);
        int enr = 0;
        for (Enrollment e : enrollments) {
            if (s.getId().equals(e.getSubjectId())) enr++;
        }
        int rec = 0;
        for (AcademicRecord r : records) {
            if (s.getId().equals(r.getSubjectId())) rec++;
        }
        int wl = waitlist == null ? 0 : waitlist.sizeTotal(s.getId());
        if (enr > 0 || rec > 0 || wl > 0) {
            return "Cannot delete " + s.getId() + ": "
                    + enr + " enrollment(s), " + rec + " record(s), " + wl
                    + " waiter(s). Remove them first.";
        }
        return null;
    }

    // ===== Internals =====

    private void checkCodeUnique(String code, String selfId) {
        Subject existing = getByCode(code);
        if (existing != null && (selfId == null || !existing.getId().equalsIgnoreCase(selfId))) {
            throw new IllegalArgumentException("Code '" + code + "' is already used by "
                    + existing.getId() + ".");
        }
    }

    private List<String> cleanPrerequisites(List<String> prerequisiteIds, String selfId) {
        List<String> ids = prerequisiteIds == null ? new ArrayList<>() : new ArrayList<>(prerequisiteIds);
        ValidationUtils.checkList(ids, "prerequisiteIds");
        for (String pid : ids) {
            if (pid.equalsIgnoreCase(selfId)) {
                throw new IllegalArgumentException("Subject cannot be its own prerequisite.");
            }
            if (getById(pid) == null) {
                throw new IllegalArgumentException("Unknown prerequisite subject ID: " + pid + ".");
            }
        }
        return ids;
    }

    private Subject requireById(String id) {
        Subject s = getById(id);
        if (s == null) {
            throw new IllegalArgumentException("Subject not found: " + id);
        }
        return s;
    }

    private void save() throws IOException {
        FileService.saveSubjects(filePath, subjects);
    }
}
