package crys.sims.model;

import java.util.ArrayList;
import java.util.List;

public class Subject {
    private String id;
    private String code;
    private String name;
    private int credits;
    private String department;
    private List<String> prerequisiteIds;
    private String semesterOffered;
    private int maxCapacity;

    public Subject() {
        this.prerequisiteIds = new ArrayList<>();
    }

    public Subject(String id, String code, String name, int credits, String department,
                   List<String> prerequisiteIds, String semesterOffered, int maxCapacity) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.department = department;
        this.prerequisiteIds = prerequisiteIds != null ? prerequisiteIds : new ArrayList<>();
        this.semesterOffered = semesterOffered;
        this.maxCapacity = maxCapacity;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public List<String> getPrerequisiteIds() {
        return prerequisiteIds;
    }

    public void setPrerequisiteIds(List<String> prerequisiteIds) {
        this.prerequisiteIds = prerequisiteIds;
    }

    public String getSemesterOffered() {
        return semesterOffered;
    }

    public void setSemesterOffered(String semesterOffered) {
        this.semesterOffered = semesterOffered;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    @Override
    public String toString() {
        return "Subject{" +
                "id='" + id + '\'' +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", credits=" + credits +
                ", department='" + department + '\'' +
                ", prerequisiteIds=" + prerequisiteIds +
                ", semesterOffered='" + semesterOffered + '\'' +
                ", maxCapacity=" + maxCapacity +
                '}';
    }
}
