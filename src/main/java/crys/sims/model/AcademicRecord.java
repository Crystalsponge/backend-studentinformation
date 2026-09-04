package crys.sims.model;

import crys.sims.model.enums.GRADE;

public class AcademicRecord {
    private String studentId;
    private String subjectId;
    private String semester;
    private GRADE grade;

    public AcademicRecord() {
    }

    public AcademicRecord(String studentId, String subjectId, String semester, GRADE grade) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.semester = semester;
        this.grade = grade;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public GRADE getGrade() {
        return grade;
    }

    public void setGrade(GRADE grade) {
        this.grade = grade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AcademicRecord that = (AcademicRecord) o;
        return java.util.Objects.equals(studentId, that.studentId)
                && java.util.Objects.equals(subjectId, that.subjectId)
                && java.util.Objects.equals(semester, that.semester);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(studentId, subjectId, semester);
    }

    @Override
    public String toString() {
        return "AcademicRecord{" +
                "studentId='" + studentId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", semester='" + semester + '\'' +
                ", grade=" + grade +
                '}';
    }
}
