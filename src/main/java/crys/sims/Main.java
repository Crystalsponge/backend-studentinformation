package crys.sims;

import crys.sims.controller.StudentController;
import crys.sims.model.AcademicRecord;
import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.service.FileService;
import crys.sims.service.WaitlistService;
import crys.sims.utils.AcademicUtils;
import crys.sims.view.EnrollmentView;
import crys.sims.view.FacultyView;
import crys.sims.view.GradeView;
import crys.sims.view.MainView;
import crys.sims.view.TranscriptView;
import crys.sims.view.StudentView;
import crys.sims.view.SubjectView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        Path configPath = Paths.get("data/config.txt");

        try {
            AcademicUtils.setMaxCreditsPerSemester(loadConfig(configPath));
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

            // Debug wiring: views talk directly to lists + FileService.
            // TODO: *Controller classes (business logic moves out of the views).
            Runtime.getRuntime().addShutdownHook(new Thread(() -> saveAll(
                    students, studentsPath, subjects, subjectsPath,
                    faculties, facultiesPath, departments, departmentsPath,
                    enrollments, enrollmentsPath, records, recordsPath,
                    waitlist, waitlistPath)));
            try (Scanner scanner = new Scanner(System.in)) {
                StudentController studentController = new StudentController(students, studentsPath, records, subjects);
                StudentView studentView = new StudentView(studentController, scanner);
                SubjectView subjectView = new SubjectView(subjects, subjectsPath, enrollments, scanner);
                FacultyView facultyView = new FacultyView(faculties, facultiesPath,
                        departments, departmentsPath, subjects, enrollments, scanner);
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

    private static int loadConfig(Path configPath) {
        try {
            if (Files.exists(configPath)) {
                for (String line : Files.readAllLines(configPath, StandardCharsets.UTF_8)) {
                    String t = line == null ? "" : line.trim();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    if (t.startsWith("maxCredits=")) {
                        return parseMaxCredits(t.substring("maxCredits=".length()).trim(), configPath);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("WARN cannot read " + configPath
                    + ", defaulting maxCredits to " + AcademicUtils.getMaxCreditsPerSemester());
        }
        return AcademicUtils.getMaxCreditsPerSemester();
    }

    private static int parseMaxCredits(String raw, Path configPath) {
        int fallback = AcademicUtils.getMaxCreditsPerSemester();
        int v;
        try {
            v = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            v = -1;
        }
        if (v < 0) {
            System.err.println("WARN invalid maxCredits '" + raw + "' in " + configPath
                    + ", defaulting to " + fallback);
            return fallback;
        }
        return v;
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
