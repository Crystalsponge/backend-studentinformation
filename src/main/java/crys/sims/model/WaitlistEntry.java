package crys.sims.model;

import java.time.LocalDate;

/**
 * One waitlist request: a student waiting for a seat in a subject offering.
 * File-backed (data/waitlist.txt); file order is arrival order (FIFO).
 */
public class WaitlistEntry {
    private String studentId;
    private String subjectId;
    private String semester;
    private LocalDate requestDate;

    public WaitlistEntry() {
    }

    public WaitlistEntry(String studentId, String subjectId, String semester, LocalDate requestDate) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.semester = semester;
        this.requestDate = requestDate;
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

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WaitlistEntry that = (WaitlistEntry) o;
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
        return "WaitlistEntry{" +
                "studentId='" + studentId + '\'' +
                ", subjectId='" + subjectId + '\'' +
                ", semester='" + semester + '\'' +
                ", requestDate=" + requestDate +
                '}';
    }
}
