package crys.sims;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.service.FileService;
import crys.sims.service.WaitlistService;
import crys.sims.view.EnrollmentView;
import crys.sims.view.FacultyView;
import crys.sims.view.GradeView;
import crys.sims.view.MainView;
import crys.sims.view.TranscriptView;
import crys.sims.view.StudentView;
import crys.sims.view.SubjectView;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // File-backed storage paths (data/ folder)
        Path studentsPath = Paths.get("data/students.txt");
        Path subjectsPath = Paths.get("data/subjects.txt");
        Path facultiesPath = Paths.get("data/faculties.txt");
        Path departmentsPath = Paths.get("data/departments.txt");
        Path enrollmentsPath = Paths.get("data/enrollments.txt");
        Path recordsPath = Paths.get("data/academic_records.txt");

        try {
            List<Student> students = FileService.loadStudents(studentsPath);
            List<Subject> subjects = FileService.loadSubjects(subjectsPath);
            List<Faculty> faculties = FileService.loadFaculties(facultiesPath);
            List<Department> departments = FileService.loadDepartments(departmentsPath);
            List<Enrollment> enrollments = FileService.loadEnrollments(enrollmentsPath);
            List<AcademicRecord> records = FileService.loadAcademicRecords(recordsPath);

            System.out.println("SIMS loaded: "
                    + students.size() + " students, "
                    + subjects.size() + " subjects, "
                    + faculties.size() + " faculties, "
                    + departments.size() + " departments, "
                    + enrollments.size() + " enrollments, "
                    + records.size() + " records.");

            // Debug wiring: views talk directly to lists + FileService.
            // TODO: *Controller classes (business logic moves out of the views).
            try (Scanner scanner = new Scanner(System.in)) {
                StudentView studentView = new StudentView(students, studentsPath, records, subjects, scanner);
                SubjectView subjectView = new SubjectView(subjects, subjectsPath, enrollments, scanner);
                FacultyView facultyView = new FacultyView(faculties, facultiesPath,
                        departments, departmentsPath, subjects, enrollments, scanner);
                Path waitlistPath = Paths.get("data/waitlist.txt");
                WaitlistService waitlist = new WaitlistService(
                        FileService.loadWaitlist(waitlistPath), waitlistPath);
                EnrollmentView enrollmentView = new EnrollmentView(enrollments, enrollmentsPath,
                        students, subjects, records, waitlist, scanner);
                GradeView gradeView = new GradeView(records, recordsPath,
                        enrollments, enrollmentsPath, students, studentsPath, subjects, scanner);
                TranscriptView transcriptView = new TranscriptView(students, subjects,
                        records, enrollments, scanner);
                MainView mainView = new MainView(studentView, subjectView, facultyView,
                        enrollmentView, gradeView, transcriptView, scanner);
                mainView.show();
            }
            System.out.println("Bye.");

        } catch (Exception e) {
            System.out.println("Failed to load data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
