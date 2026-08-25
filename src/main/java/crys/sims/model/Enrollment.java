package crys.sims.model;

import java.time.LocalDate;

public class Enrollment {
    private String studentId;
    private String subjectId;
    private String semester;
    private LocalDate enrollmentDate;

    public Enrollment() {
    }

    public Enrollment(String studentId, String subjectId, String semester, LocalDate enrollmentDate) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.semester = semester;
        this.enrollmentDate = enrollmentDate;
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

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(LocalDate enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    @Override
    public String toString() {
        return "Enrollment{" +
                "studentId='" + studentId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", semester='" + semester + '\'' +
                ", enrollmentDate=" + enrollmentDate +
                '}';
    }
}
