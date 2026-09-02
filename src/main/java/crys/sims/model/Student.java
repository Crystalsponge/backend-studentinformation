package crys.sims.model;

import crys.sims.model.enums.GENDER;

import java.time.LocalDate;

public class Student {
    private String id;
    private String firstName;
    private String lastName;
    private GENDER gender;
    private LocalDate dateOfBirth;
    private String department;
    private String program;
    private int yearLevel;
    private String currentSemester;
    private LocalDate enrollmentDate;
    private String email;
    private String phone;
    private boolean active;
    private int earnedCredits;

    public Student() {
        this.active = true;
        this.earnedCredits = 0;
    }

    public Student(String id, String firstName, String lastName, GENDER gender,
                   LocalDate dateOfBirth, String department, String program,
                   int yearLevel, String currentSemester, LocalDate enrollmentDate,
                   String email, String phone, boolean active,
                   int earnedCredits) {
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
        this.earnedCredits = earnedCredits;
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

    public GENDER getGender() {
        return gender;
    }

    public void setGender(GENDER gender) {
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

    public String getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(String currentSemester) {
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

    public int getEarnedCredits() {
        return earnedCredits;
    }

    public void setEarnedCredits(int earnedCredits) {
        this.earnedCredits = earnedCredits;
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
                ", currentSemester='" + currentSemester + '\'' +
                ", email='" + email + '\'' +
                ", active=" + active +
                ", earnedCredits=" + earnedCredits +
                '}';
    }
}
