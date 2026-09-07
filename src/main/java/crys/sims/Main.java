package crys.sims;

import crys.sims.controller.EnrollmentController;
import crys.sims.controller.FacultyController;
import crys.sims.controller.GradeController;
import crys.sims.controller.TranscriptController;
import crys.sims.controller.StudentController;
import crys.sims.controller.SubjectController;
import crys.sims.model.AcademicRecord;
import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.service.FileService;
import crys.sims.service.WaitlistService;
import crys.sims.view.EnrollmentView;
import crys.sims.view.FacultyView;
import crys.sims.view.GradeView;
import crys.sims.view.MainView;
import crys.sims.view.TranscriptView;
import crys.sims.view.StudentView;
import crys.sims.view.SubjectView;

import java.io.IOException;
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
        Path waitlistPath = Paths.get("data/waitlist.txt");

        try {
            List<Student> students = FileService.loadStudents(studentsPath);
            List<Subject> subjects = FileService.loadSubjects(subjectsPath);
            List<Faculty> faculties = FileService.loadFaculties(facultiesPath);
            List<Department> departments = FileService.loadDepartments(departmentsPath);
            List<Enrollment> enrollments = FileService.loadEnrollments(enrollmentsPath);
            List<AcademicRecord> records = FileService.loadAcademicRecords(recordsPath);
            WaitlistService waitlist = new WaitlistService(
                    FileService.loadWaitlist(waitlistPath), waitlistPath);

            System.out.println("SIMS loaded: "
                    + students.size() + " students, "
                    + subjects.size() + " subjects, "
                    + faculties.size() + " faculties, "
                    + departments.size() + " departments, "
                    + enrollments.size() + " enrollments, "
                    + records.size() + " records.");

            // MVC wiring: views take controllers; controllers own lists + FileService.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> saveAll(
                    students, studentsPath, subjects, subjectsPath,
                    faculties, facultiesPath, departments, departmentsPath,
                    enrollments, enrollmentsPath, records, recordsPath,
                    waitlist, waitlistPath)));
            try (Scanner scanner = new Scanner(System.in)) {
                StudentController studentController = new StudentController(students, studentsPath, records, subjects);
                StudentView studentView = new StudentView(studentController, scanner);
                SubjectController subjectController = new SubjectController(subjects, subjectsPath,
                        enrollments, records, waitlist);
                SubjectView subjectView = new SubjectView(subjectController, scanner);
                FacultyController facultyController = new FacultyController(faculties, facultiesPath,
                        departments, departmentsPath, subjects, enrollments);
                FacultyView facultyView = new FacultyView(facultyController, scanner);
                EnrollmentController enrollmentController = new EnrollmentController(enrollments, enrollmentsPath,
                        students, subjects, records, waitlist);
                EnrollmentView enrollmentView = new EnrollmentView(enrollmentController, scanner);
                GradeController gradeController = new GradeController(records, recordsPath,
                        enrollments, enrollmentsPath, students, studentsPath, subjects);
                GradeView gradeView = new GradeView(gradeController, scanner);
                TranscriptController transcriptController = new TranscriptController(students, subjects,
                        records, enrollments, enrollmentController);
                TranscriptView transcriptView = new TranscriptView(transcriptController, scanner);
                MainView mainView = new MainView(studentView, subjectView, facultyView,
                        enrollmentView, gradeView, transcriptView, scanner);
                mainView.show();
            }
            saveAll(students, studentsPath, subjects, subjectsPath,
                    faculties, facultiesPath, departments, departmentsPath,
                    enrollments, enrollmentsPath, records, recordsPath,
                    waitlist, waitlistPath);
            System.out.println("Saved on exit.");
            System.out.println("Bye.");

        } catch (IOException | RuntimeException e) {
            System.out.println("Failed to load data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void saveAll(List<Student> students, Path studentsPath,
                                List<Subject> subjects, Path subjectsPath,
                                List<Faculty> faculties, Path facultiesPath,
                                List<Department> departments, Path departmentsPath,
                                List<Enrollment> enrollments, Path enrollmentsPath,
                                List<AcademicRecord> records, Path recordsPath,
                                WaitlistService waitlist, Path waitlistPath) {
        try { FileService.saveStudents(studentsPath, students); }
        catch (IOException e) { System.err.println("Save on exit failed (students): " + e.getMessage()); }
        try { FileService.saveSubjects(subjectsPath, subjects); }
        catch (IOException e) { System.err.println("Save on exit failed (subjects): " + e.getMessage()); }
        try { FileService.saveFaculties(facultiesPath, faculties); }
        catch (IOException e) { System.err.println("Save on exit failed (faculties): " + e.getMessage()); }
        try { FileService.saveDepartments(departmentsPath, departments); }
        catch (IOException e) { System.err.println("Save on exit failed (departments): " + e.getMessage()); }
        try { FileService.saveEnrollments(enrollmentsPath, enrollments); }
        catch (IOException e) { System.err.println("Save on exit failed (enrollments): " + e.getMessage()); }
        try { FileService.saveAcademicRecords(recordsPath, records); }
        catch (IOException e) { System.err.println("Save on exit failed (records): " + e.getMessage()); }
        try { FileService.saveWaitlist(waitlistPath, waitlist.listAll()); }
        catch (IOException e) { System.err.println("Save on exit failed (waitlist): " + e.getMessage()); }
    }
}
