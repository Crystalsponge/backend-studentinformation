package crys.sims.controller;

import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Subject;
import crys.sims.service.FileService;
import crys.sims.utils.IdGenerator;
import crys.sims.utils.TextUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic + persistence for faculties and departments.
 * Owns both in-memory lists; views call these methods and never touch
 * FileService directly. Department.facultyId is authoritative — every
 * department mutation rebuilds each Faculty.departmentIds mirror and
 * saves both files. Deletes are hard but guarded.
 */
public class FacultyController {

    private final List<Faculty> faculties;
    private final Path facultiesPath;
    private final List<Department> departments;
    private final Path departmentsPath;
    private final List<Subject> subjects;
    private final List<Enrollment> enrollments;

    public FacultyController(List<Faculty> faculties, Path facultiesPath,
                             List<Department> departments, Path departmentsPath,
                             List<Subject> subjects, List<Enrollment> enrollments) {
        this.faculties = faculties;
        this.facultiesPath = facultiesPath;
        this.departments = departments;
        this.departmentsPath = departmentsPath;
        this.subjects = subjects;
        this.enrollments = enrollments;
    }

    // ===== Faculty reads =====

    public List<Faculty> getAll() {
        return new ArrayList<>(faculties);
    }

    public Faculty getById(String id) {
        if (id == null) return null;
        for (Faculty f : faculties) {
            if (id.equalsIgnoreCase(f.getId())) return f;
        }
        return null;
    }

    public List<Faculty> searchFaculties(String query) {
        String q = query == null ? "" : query;
        List<Faculty> hits = new ArrayList<>();
        for (Faculty f : faculties) {
            if (TextUtils.containsIgnoreCase(f.getId(), q)
                    || TextUtils.containsIgnoreCase(f.getName(), q)) {
                hits.add(f);
            }
        }
        return hits;
    }

    public List<Department> getDepartmentsByFaculty(String facultyId) {
        List<Department> result = new ArrayList<>();
        for (Department d : departments) {
            if (facultyId != null && facultyId.equals(d.getFacultyId())) {
                result.add(d);
            }
        }
        return result;
    }

    // ===== Faculty mutations =====

    public Faculty add(String name) throws IOException {
        String cleanName = ValidationUtils.cleanField(name, "name");
        List<String> ids = faculties.stream().map(Faculty::getId).collect(Collectors.toList());
        String id = IdGenerator.nextId(ids, "F", 3);
        Faculty f = new Faculty(id, cleanName, new ArrayList<>());
        faculties.add(f);
        saveFaculties();
        return f;
    }

    public void setName(String id, String value) throws IOException {
        Faculty f = requireFaculty(id);
        f.setName(ValidationUtils.cleanField(value, "name"));
        saveFaculties();
    }

    public void delete(String id) throws IOException {
        Faculty f = requireFaculty(id);
        List<Department> attached = getDepartmentsByFaculty(f.getId());
        if (!attached.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (Department d : attached) ids.add(d.getId());
            throw new IllegalArgumentException("Cannot delete " + f.getId() + ": "
                    + attached.size() + " department(s) (" + String.join(",", ids)
                    + "). Move or delete them first.");
        }
        faculties.remove(f);
        saveFaculties();
    }

    // ===== Department reads =====

    public List<Department> getAllDepartments() {
        return new ArrayList<>(departments);
    }

    public Department getDepartmentById(String id) {
        if (id == null) return null;
        for (Department d : departments) {
            if (id.equalsIgnoreCase(d.getId())) return d;
        }
        return null;
    }

    public List<Department> searchDepartments(String query) {
        String q = query == null ? "" : query;
        List<Department> hits = new ArrayList<>();
        for (Department d : departments) {
            if (TextUtils.containsIgnoreCase(d.getId(), q)
                    || TextUtils.containsIgnoreCase(d.getName(), q)) {
                hits.add(d);
            }
        }
        return hits;
    }

    public List<Subject> getSubjectsOf(String departmentId) {
        List<Subject> result = new ArrayList<>();
        for (Subject s : subjects) {
            if (departmentId != null && departmentId.equals(s.getDepartment())) {
                result.add(s);
            }
        }
        return result;
    }

    public int getSubjectCount(String departmentId) {
        return getSubjectsOf(departmentId).size();
    }

    public List<Department> getOrphanDepartments() {
        List<Department> result = new ArrayList<>();
        for (Department d : departments) {
            if (getById(d.getFacultyId()) == null) {
                result.add(d);
            }
        }
        return result;
    }

    public int getEnrollmentCount(String subjectId) {
        int n = 0;
        for (Enrollment e : enrollments) {
            if (subjectId != null && subjectId.equals(e.getSubjectId())) {
                n++;
            }
        }
        return n;
    }

    // ===== Department mutations =====

    public Department addDepartment(String name, String facultyId) throws IOException {
        String cleanName = ValidationUtils.cleanField(name, "name");
        Faculty owner = requireFaculty(facultyId);
        List<String> ids = departments.stream().map(Department::getId).collect(Collectors.toList());
        String id = IdGenerator.nextId(ids, "D", 3);
        Department d = new Department(id, cleanName, owner.getId());
        departments.add(d);
        syncFacultyDepartments();
        saveBoth();
        return d;
    }

    public void setDepartmentName(String id, String value) throws IOException {
        Department d = requireDepartment(id);
        d.setName(ValidationUtils.cleanField(value, "name"));
        saveBoth();
    }

    public void moveDepartment(String id, String newFacultyId) throws IOException {
        Department d = requireDepartment(id);
        Faculty owner = requireFaculty(newFacultyId);
        d.setFacultyId(owner.getId());
        syncFacultyDepartments();
        saveBoth();
    }

    public void deleteDepartment(String id) throws IOException {
        Department d = requireDepartment(id);
        List<Subject> using = getSubjectsOf(d.getId());
        if (!using.isEmpty()) {
            List<String> codes = new ArrayList<>();
            for (Subject s : using) codes.add(s.getId());
            throw new IllegalArgumentException("Cannot delete " + d.getId() + ": "
                    + using.size() + " subject(s) (" + String.join(",", codes)
                    + "). Reassign or delete them first.");
        }
        departments.remove(d);
        syncFacultyDepartments();
        saveBoth();
    }

    // ===== Internals =====

    private void syncFacultyDepartments() {
        Map<String, List<String>> byFaculty = new HashMap<>();
        for (Department d : departments) {
            byFaculty.computeIfAbsent(TextUtils.orEmpty(d.getFacultyId()), k -> new ArrayList<>())
                    .add(d.getId());
        }
        for (Faculty f : faculties) {
            List<String> ids = byFaculty.getOrDefault(f.getId(), new ArrayList<>());
            f.setDepartmentIds(new ArrayList<>(ids));
        }
    }

    private Faculty requireFaculty(String id) {
        Faculty f = getById(id);
        if (f == null) {
            throw new IllegalArgumentException("Faculty not found: " + id);
        }
        return f;
    }

    private Department requireDepartment(String id) {
        Department d = getDepartmentById(id);
        if (d == null) {
            throw new IllegalArgumentException("Department not found: " + id);
        }
        return d;
    }

    private void saveFaculties() throws IOException {
        FileService.saveFaculties(facultiesPath, faculties);
    }

    private void saveBoth() throws IOException {
        FileService.saveFaculties(facultiesPath, faculties);
        FileService.saveDepartments(departmentsPath, departments);
    }
}
