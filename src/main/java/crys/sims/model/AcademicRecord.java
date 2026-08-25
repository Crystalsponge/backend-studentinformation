package crys.sims.model;

import crys.sims.model.enums.Grade;

public class AcademicRecord {
    private String studentId;
    private String subjectId;
    private String semester;
    private Grade grade;

    public AcademicRecord() {
    }

    public AcademicRecord(String studentId, String subjectId, String semester, Grade grade) {
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

    public Grade getGrade() {
        return grade;
    }

    public void setGrade(Grade grade) {
        this.grade = grade;
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
