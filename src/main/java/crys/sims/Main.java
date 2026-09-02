package crys.sims;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Department;
import crys.sims.model.Enrollment;
import crys.sims.model.Faculty;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.service.FileService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    static void main() {
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

            IO.println("SIMS loaded: "
                    + students.size() + " students, "
                    + subjects.size() + " subjects, "
                    + faculties.size() + " faculties, "
                    + departments.size() + " departments, "
                    + enrollments.size() + " enrollments, "
                    + records.size() + " records.");

            // TODO: instantiate controllers with loaded lists + FileService paths
            // TODO: launch MainView (console CLI menu loop)
            IO.println("Controllers/Views not yet implemented — data layer ready.");

        } catch (Exception e) {
            IO.println("Failed to load data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
