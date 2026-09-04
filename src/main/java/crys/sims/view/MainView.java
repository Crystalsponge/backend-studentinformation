package crys.sims.view;

import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;

import java.util.Scanner;

/**
 * Top-level menu dispatching to feature views.
 */
public class MainView {

    private final StudentView studentView;
    private final SubjectView subjectView;
    private final FacultyView facultyView;
    private final EnrollmentView enrollmentView;
    private final GradeView gradeView;
    private final TranscriptView transcriptView;
    private final Scanner scanner;

    public MainView(StudentView studentView, SubjectView subjectView,
                    FacultyView facultyView, EnrollmentView enrollmentView,
                    GradeView gradeView, TranscriptView transcriptView, Scanner scanner) {
        this.studentView = studentView;
        this.subjectView = subjectView;
        this.facultyView = facultyView;
        this.enrollmentView = enrollmentView;
        this.gradeView = gradeView;
        this.transcriptView = transcriptView;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("SIMS MAIN MENU");
            System.out.println("1. Student management");
            System.out.println("2. Subject management");
            System.out.println("3. Faculty management");
            System.out.println("4. Enrollment management");
            System.out.println("5. Grade management");
            System.out.println("6. Transcript & progress");
            System.out.println("0. Exit");
            int choice = InputUtils.readMenuChoice(scanner, 0, 6);
            switch (choice) {
                case 1: studentView.show(); break;
                case 2: subjectView.show(); break;
                case 3: facultyView.show(); break;
                case 4: enrollmentView.show(); break;
                case 5: gradeView.show(); break;
                case 6: transcriptView.show(); break;
                case 0: return;
                default: break;
            }
        }
    }
}
