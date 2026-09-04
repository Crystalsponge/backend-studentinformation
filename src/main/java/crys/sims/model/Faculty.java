package crys.sims.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Faculty 1—N Departments.
 * Authoritative link is Department.facultyId; this departmentIds list is a
 * cached mirror that FacultyController must rebuild/sync on every
 * Department add/update/delete to avoid drift.
 */
public class Faculty {
    private String id;
    private String name;
    private List<String> departmentIds;

    public Faculty() {
        this.departmentIds = new ArrayList<>();
    }

    public Faculty(String id, String name, List<String> departmentIds) {
        this.id = id;
        this.name = name;
        this.departmentIds = departmentIds != null ? departmentIds : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getDepartmentIds() {
        return departmentIds;
    }

    public void setDepartmentIds(List<String> departmentIds) {
        this.departmentIds = departmentIds != null ? departmentIds : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Faculty faculty = (Faculty) o;
        return id != null && id.equals(faculty.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Faculty{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", departmentIds=" + departmentIds +
                '}';
    }
}
