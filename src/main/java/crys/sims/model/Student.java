package crys.sims.model;

import crys.sims.model.enums.Gender;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Student {
    private String id;
    private String firstName;
    private String lastName;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String department;
    private String program;
    private int yearLevel;
    private int currentSemester;
    private LocalDate enrollmentDate;
    private String email;
    private String phone;
    private boolean active;
    private int currentCredit;
    private List<String> enrolledSubjects;

    public Student() {
        this.enrolledSubjects = new ArrayList<>();
        this.active = true;
    }

    public Student(String id, String firstName, String lastName, Gender gender,
                   LocalDate dateOfBirth, String department, String program,
                   int yearLevel, int currentSemester, LocalDate enrollmentDate,
                   String email, String phone, boolean active,
                   int currentCredit, List<String> enrolledSubjects) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.department = department;
        this.program = program;
        this.yearLevel = yearLevel;
        this.currentSemester = currentSemester;
        this.enrollmentDate = enrollmentDate;
        this.email = email;
        this.phone = phone;
        this.active = active;
        this.currentCredit = currentCredit;
        this.enrolledSubjects = enrolledSubjects != null ? enrolledSubjects : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = program;
    }

    public int getYearLevel() {
        return yearLevel;
    }

    public void setYearLevel(int yearLevel) {
        this.yearLevel = yearLevel;
    }

    public int getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(int currentSemester) {
        this.currentSemester = currentSemester;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public void setEnrollmentDate(LocalDate enrollmentDate) {
        this.enrollmentDate = enrollmentDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getCurrentCredit() {
        return currentCredit;
    }

    public void setCurrentCredit(int currentCredit) {
        this.currentCredit = currentCredit;
    }

    public List<String> getEnrolledSubjects() {
        return enrolledSubjects;
    }

    public void setEnrolledSubjects(List<String> enrolledSubjects) {
        this.enrolledSubjects = enrolledSubjects;
    }

    @Override
    public String toString() {
        return "Student{" +
                "id='" + id + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", gender=" + gender +
                ", department='" + department + '\'' +
                ", program='" + program + '\'' +
                ", yearLevel=" + yearLevel +
                ", currentSemester=" + currentSemester +
                ", email='" + email + '\'' +
                ", active=" + active +
                ", enrolledSubjects=" + enrolledSubjects +
                '}';
    }
}
