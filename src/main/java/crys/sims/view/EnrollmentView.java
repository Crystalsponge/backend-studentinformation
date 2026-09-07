package crys.sims.view;

import crys.sims.controller.EnrollmentController;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Console UI for course registration: register/drop, rosters, semester
 * statistics, waitlist, and undo/redo.
 * I/O only: all business logic and persistence live in EnrollmentController.
 */
public class EnrollmentView {

    private final EnrollmentController controller;
    private final Scanner scanner;

    public EnrollmentView(EnrollmentController controller, Scanner scanner) {
        this.controller = controller;
        this.scanner = scanner;
    }

    public void show() {
        while (true) {
            FormatUtils.printHeader("ENROLLMENT MANAGEMENT");
            System.out.println("1. View enrollments by student");
            System.out.println("2. View roster by subject");
            System.out.println("3. Register student");
            System.out.println("4. Drop enrollment");
            System.out.println("5. View all enrollments");
            System.out.println("6. Semester statistics");
            System.out.println("7. Undo" + (controller.canUndo() ? "" : " (empty)"));
            System.out.println("8. Redo" + (controller.canRedo() ? "" : " (empty)"));
            System.out.println("9. Waitlist");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 9);
            switch (choice) {
                case 1: viewByStudent(); break;
                case 2: viewRoster(); break;
                case 3: register(); break;
                case 4: drop(); break;
                case 5: viewAll(); break;
                case 6: semesterStats(); break;
                case 7: undo(); break;
                case 8: redo(); break;
                case 9: waitlistMenu(); break;
                case 0: return;
                default: break;
            }
        }
    }

    // ===== Actions (I/O only) =====

    private void viewByStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        FormatUtils.printHeader("ENROLLMENTS: " + stu.getId() + " " + stu.getFullName());
        List<Enrollment> mine = controller.getByStudent(stu.getId());
        printEnrollments(mine, true);
        Map<String, Integer> creditsPerSem = new HashMap<>();
        for (Enrollment e : mine) {
            Subject subj = controller.findSubject(e.getSubjectId());
            int c = subj == null ? 0 : subj.getCredits();
            creditsPerSem.put(e.getSemester(), creditsPerSem.getOrDefault(e.getSemester(), 0) + c);
        }
        for (Map.Entry<String, Integer> entry : creditsPerSem.entrySet()) {
            System.out.println("  Semester " + entry.getKey() + ": " + entry.getValue()
                    + " credit(s) (limit " + AcademicUtils.MAX_CREDITS_PER_SEMESTER + ")");
        }
    }

    private void viewRoster() {
        Subject subj = readSubject();
        if (subj == null) return;
        FormatUtils.printHeader("ROSTER: " + subj.getCode() + " " + TextUtils.orEmpty(subj.getName()));
        List<Enrollment> roster = controller.getRoster(subj.getId());
        String[] headers = {"Student", "Name", "Program", "Sem", "Date"};
        List<String[]> rows = new ArrayList<>();
        for (Enrollment e : roster) {
            Student stu = controller.findStudent(e.getStudentId());
            rows.add(new String[]{
                    e.getStudentId(),
                    stu == null ? "MISSING" : stu.getFullName(),
                    stu == null ? "" : TextUtils.orEmpty(stu.getProgram()),
                    TextUtils.orEmpty(e.getSemester()),
                    e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
            });
        }
        FormatUtils.printTable(headers, rows);
        System.out.println("  Enrolled: " + roster.size() + " / " + subj.getMaxCapacity());
    }

    private void register() {
        FormatUtils.printHeader("REGISTER STUDENT");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        String rawSem = InputUtils.readLine(scanner, "Semester [" + TextUtils.orEmpty(stu.getCurrentSemester()) + "]: ");
        String sem = rawSem.isEmpty() ? TextUtils.orEmpty(stu.getCurrentSemester()) : rawSem;
        try {
            Enrollment e = controller.register(stu.getId(), subj.getId(), sem);
            System.out.println("  Registered " + e.getStudentId() + " -> " + subj.getCode() + " (" + e.getSemester() + ")");
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot register: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().startsWith("Subject is full")) {
                offerWaitlistJoin(stu, subj, sem.trim());
            }
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void drop() {
        FormatUtils.printHeader("DROP ENROLLMENT");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<Enrollment> matches = controller.getEnrollments(stu.getId(), subj.getId());
        if (matches.isEmpty()) {
            System.out.println("  No enrollment found for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        String sem = null;
        if (matches.size() > 1) {
            printEnrollments(matches, false);
            sem = InputUtils.readRequiredLine(scanner, "Multiple semesters — which semester to drop: ");
        }
        Enrollment target = matches.get(0);
        if (sem != null) {
            target = null;
            for (Enrollment e : matches) {
                if (sem.equalsIgnoreCase(e.getSemester())) {
                    target = e;
                    break;
                }
            }
            if (target == null) {
                System.out.println("  No enrollment in semester: " + sem);
                return;
            }
        }
        boolean confirm = InputUtils.readYesNo(scanner,
                "Drop " + stu.getId() + " from " + subj.getCode() + " (" + target.getSemester() + ")?", false);
        if (!confirm) {
            System.out.println("  Cancelled.");
            return;
        }
        try {
            Enrollment dropped = controller.drop(stu.getId(), subj.getId(), target.getSemester());
            System.out.println("  Dropped " + dropped.getStudentId() + " from " + subj.getCode());
            int waiting = controller.getWaitlistSize(subj.getId(), dropped.getSemester());
            if (waiting > 0) {
                WaitlistEntry head = controller.peekWaitlistHead(subj.getId(), dropped.getSemester());
                System.out.println("  " + waiting + " student(s) on waitlist (head: "
                        + (head == null ? "?" : head.getStudentId()) + ") — admit via Waitlist menu (9).");
            }
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot drop: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void viewAll() {
        FormatUtils.printHeader("ALL ENROLLMENTS");
        printEnrollments(controller.getAll(), true);
    }

    private void semesterStats() {
        FormatUtils.printHeader("SEMESTER STATISTICS");
        String sem = InputUtils.readLine(scanner, "Semester (empty = all): ").trim();
        EnrollmentController.SemesterStats stats = controller.getSemesterStats(sem);
        String[] subHeaders = {"Subject", "Name", "Enrolled"};
        List<String[]> subRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stats.bySubject.entrySet()) {
            Subject subj = controller.findSubject(entry.getKey());
            String code = subj == null ? entry.getKey() : subj.getCode();
            String name = subj == null ? "MISSING" : TextUtils.orEmpty(subj.getName());
            subRows.add(new String[]{code, name, String.valueOf(entry.getValue())});
        }
        System.out.println("-- By subject --");
        FormatUtils.printTable(subHeaders, subRows);
        String[] deptHeaders = {"Department", "Enrolled"};
        List<String[]> deptRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stats.byDepartment.entrySet()) {
            deptRows.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
        }
        System.out.println("-- By department --");
        FormatUtils.printTable(deptHeaders, deptRows);
    }

    private void undo() {
        try {
            controller.undo();
            System.out.println("  Undid last change.");
        } catch (IllegalStateException e) {
            System.out.println("  Nothing to undo.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void redo() {
        try {
            controller.redo();
            System.out.println("  Redid last change.");
        } catch (IllegalStateException e) {
            System.out.println("  Nothing to redo.");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    // ===== Waitlist submenu =====

    private void waitlistMenu() {
        while (true) {
            FormatUtils.printHeader("WAITLIST (FIFO)");
            System.out.println("1. List waitlist");
            System.out.println("2. Join waitlist");
            System.out.println("3. Admit head");
            System.out.println("4. Leave waitlist");
            System.out.println("0. Back");
            int choice = InputUtils.readMenuChoice(scanner, 0, 4);
            switch (choice) {
                case 1: listWaitlist(); break;
                case 2: joinWaitlist(); break;
                case 3: admitHead(); break;
                case 4: leaveWaitlist(); break;
                case 0: return;
                default: break;
            }
        }
    }

    private void listWaitlist() {
        FormatUtils.printHeader("WAITLIST");
        List<WaitlistEntry> all = controller.getWaitlist();
        String[] headers = {"Pos", "Student", "Name", "Subject", "Sem", "Since"};
        List<String[]> rows = new ArrayList<>();
        int pos = 0;
        for (WaitlistEntry w : all) {
            pos++;
            Student stu = controller.findStudent(w.getStudentId());
            Subject subj = controller.findSubject(w.getSubjectId());
            rows.add(new String[]{
                    String.valueOf(pos),
                    w.getStudentId(),
                    stu == null ? "MISSING" : stu.getFullName(),
                    subj == null ? w.getSubjectId() : subj.getCode(),
                    TextUtils.orEmpty(w.getSemester()),
                    w.getRequestDate() == null ? "" : w.getRequestDate().toString()
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void joinWaitlist() {
        FormatUtils.printHeader("JOIN WAITLIST");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = controller.findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        String rawSem = InputUtils.readLine(scanner, "Semester [" + TextUtils.orEmpty(stu.getCurrentSemester()) + "]: ");
        String sem = rawSem.isEmpty() ? TextUtils.orEmpty(stu.getCurrentSemester()) : rawSem;
        doJoin(stu, subj, sem);
    }

    private void offerWaitlistJoin(Student stu, Subject subj, String semester) {
        boolean join = InputUtils.readYesNo(scanner, "Join waitlist?", false);
        if (!join) return;
        doJoin(stu, subj, semester);
    }

    private void doJoin(Student stu, Subject subj, String semester) {
        try {
            WaitlistEntry entry = controller.joinWaitlist(stu.getId(), subj.getId(), semester);
            System.out.println("  Waitlisted (#" + controller.getWaitlistSize(subj.getId(), entry.getSemester())
                    + " for " + subj.getCode() + " " + entry.getSemester() + ")");
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot waitlist: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void admitHead() {
        FormatUtils.printHeader("ADMIT FROM WAITLIST");
        Subject subj = readSubject();
        if (subj == null) return;
        String sem = InputUtils.readRequiredLine(scanner, "Semester: ");
        try {
            Enrollment admitted = controller.admitHead(subj.getId(), sem.trim());
            System.out.println("  Admitted " + admitted.getStudentId() + " -> " + subj.getCode()
                    + " (" + admitted.getSemester() + ")");
            System.out.println("  Note: undo reverts the enrollment only; re-join the waitlist manually if needed.");
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    private void leaveWaitlist() {
        FormatUtils.printHeader("LEAVE WAITLIST");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        String subInput = InputUtils.readRequiredLine(scanner, "Subject ID or code: ");
        Subject subj = controller.findSubject(subInput);
        String subjectId = subj == null ? subInput.trim() : subj.getId();
        String sem = InputUtils.readRequiredLine(scanner, "Semester: ");
        try {
            controller.leaveWaitlist(sid.trim(), subjectId, sem.trim());
            System.out.println("  Removed from waitlist.");
        } catch (IllegalArgumentException e) {
            System.out.println("  " + e.getMessage());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        }
    }

    // ===== Display helpers =====

    private void printEnrollments(List<Enrollment> list, boolean showStudent) {
        String[] headers = showStudent
                ? new String[]{"Student", "Name", "Subject", "Sem", "Date"}
                : new String[]{"Subject", "Sem", "Date"};
        List<String[]> rows = new ArrayList<>();
        for (Enrollment e : list) {
            Subject subj = controller.findSubject(e.getSubjectId());
            String subjShown = subj == null ? e.getSubjectId() + " MISSING" : subj.getCode();
            if (showStudent) {
                Student stu = controller.findStudent(e.getStudentId());
                rows.add(new String[]{
                        e.getStudentId(),
                        stu == null ? "MISSING" : stu.getFullName(),
                        subjShown,
                        TextUtils.orEmpty(e.getSemester()),
                        e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
                });
            } else {
                rows.add(new String[]{
                        subjShown,
                        TextUtils.orEmpty(e.getSemester()),
                        e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
                });
            }
        }
        FormatUtils.printTable(headers, rows);
    }

    // ===== Lookup helpers =====

    private Subject readSubject() {
        String input = InputUtils.readRequiredLine(scanner, "Subject ID or code: ");
        Subject subj = controller.findSubject(input);
        if (subj == null) {
            System.out.println("  Not found: " + input);
        }
        return subj;
    }
}
