package crys.sims.model;

/**
 * Authoritative owner of the Faculty—Department relation via facultyId.
 * Faculty.departmentIds mirrors this and must be kept in sync by FacultyController.
 */
public class Department {
    private String id;
    private String name;
    private String facultyId;

    public Department() {
    }

    public Department(String id, String name, String facultyId) {
        this.id = id;
        this.name = name;
        this.facultyId = facultyId;
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

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Department that = (Department) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Department{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", facultyId='" + facultyId + '\'' +
                '}';
    }
}
