package crys.sims.view;

import crys.sims.model.AcademicRecord;
import crys.sims.model.Enrollment;
import crys.sims.model.Student;
import crys.sims.model.Subject;
import crys.sims.model.WaitlistEntry;
import crys.sims.service.FileService;
import crys.sims.service.UndoRedoService;
import crys.sims.service.WaitlistService;
import crys.sims.utils.AcademicUtils;
import crys.sims.utils.FormatUtils;
import crys.sims.utils.InputUtils;
import crys.sims.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Console UI for course registration: register/drop with rule validation,
 * rosters, semester statistics, waitlist, and undo/redo.
 * Temporary: talks directly to the in-memory lists + FileService.
 * An EnrollmentController will take over business logic later; this view keeps only I/O.
 */
public class EnrollmentView {

    private final List<Enrollment> enrollments;
    private final Path filePath;
    private final List<Student> students;
    private final List<Subject> subjects;
    private final List<AcademicRecord> records;
    private final WaitlistService waitlist;
    private final UndoRedoService history;
    private final Scanner scanner;

    public EnrollmentView(List<Enrollment> enrollments, Path filePath,
                          List<Student> students, List<Subject> subjects,
                          List<AcademicRecord> records, WaitlistService waitlist,
                          Scanner scanner) {
        this.enrollments = enrollments;
        this.filePath = filePath;
        this.students = students;
        this.subjects = subjects;
        this.records = records;
        this.waitlist = waitlist;
        this.history = new UndoRedoService();
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
            System.out.println("7. Undo" + (history.canUndo() ? "" : " (empty)"));
            System.out.println("8. Redo" + (history.canRedo() ? "" : " (empty)"));
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

    // ===== Actions =====

    private void viewByStudent() {
        String id = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(id);
        if (stu == null) {
            System.out.println("  Not found: " + id);
            return;
        }
        FormatUtils.printHeader("ENROLLMENTS: " + stu.getId() + " " + stu.getFullName());
        List<Enrollment> mine = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId())) mine.add(e);
        }
        printEnrollments(mine, true);
        Map<String, Integer> creditsPerSem = new HashMap<>();
        for (Enrollment e : mine) {
            Subject subj = findSubjectById(e.getSubjectId());
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
        FormatUtils.printHeader("ROSTER: " + subj.getCode() + " " + text(subj.getName()));
        String[] headers = {"Student", "Name", "Program", "Sem", "Date"};
        List<String[]> rows = new ArrayList<>();
        int count = 0;
        for (Enrollment e : enrollments) {
            if (!subj.getId().equals(e.getSubjectId())) continue;
            count++;
            Student stu = findStudent(e.getStudentId());
            rows.add(new String[]{
                    e.getStudentId(),
                    stu == null ? "MISSING" : stu.getFullName(),
                    stu == null ? "" : text(stu.getProgram()),
                    text(e.getSemester()),
                    e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
            });
        }
        FormatUtils.printTable(headers, rows);
        System.out.println("  Enrolled: " + count + " / " + subj.getMaxCapacity());
    }

    private void register() {
        FormatUtils.printHeader("REGISTER STUDENT");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        String rawSem = InputUtils.readLine(scanner, "Semester [" + text(stu.getCurrentSemester()) + "]: ");
        String sem = rawSem.isEmpty() ? text(stu.getCurrentSemester()) : rawSem;
        try {
            ValidationUtils.validateRegistration(stu.getId(), subj.getId(), sem,
                    students, subjects, enrollments, records);
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot register: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().startsWith("Subject is full")) {
                offerWaitlistJoin(stu, subj, sem.trim());
            }
            return;
        }
        history.snapshot(enrollments);
        enrollments.add(new Enrollment(stu.getId(), subj.getId(), sem.trim(), LocalDate.now()));
        if (save()) {
            System.out.println("  Registered " + stu.getId() + " -> " + subj.getCode() + " (" + sem.trim() + ")");
        }
    }

    private void drop() {
        FormatUtils.printHeader("DROP ENROLLMENT");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        List<Enrollment> matches = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (stu.getId().equals(e.getStudentId()) && subj.getId().equals(e.getSubjectId())) {
                matches.add(e);
            }
        }
        if (matches.isEmpty()) {
            System.out.println("  No enrollment found for " + stu.getId() + " in " + subj.getCode() + ".");
            return;
        }
        Enrollment target = matches.get(0);
        if (matches.size() > 1) {
            printEnrollments(matches, false);
            String sem = InputUtils.readRequiredLine(scanner, "Multiple semesters — which semester to drop: ");
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
        history.snapshot(enrollments);
        enrollments.remove(target);
        if (save()) {
            System.out.println("  Dropped " + stu.getId() + " from " + subj.getCode());
            int waiting = waitlist.size(subj.getId(), target.getSemester());
            if (waiting > 0) {
                WaitlistEntry head = waitlist.peekHead(subj.getId(), target.getSemester());
                System.out.println("  " + waiting + " student(s) on waitlist (head: "
                        + (head == null ? "?" : head.getStudentId()) + ") — admit via Waitlist menu (9).");
            }
        }
    }

    private void viewAll() {
        FormatUtils.printHeader("ALL ENROLLMENTS");
        printEnrollments(enrollments, true);
    }

    private void semesterStats() {
        FormatUtils.printHeader("SEMESTER STATISTICS");
        String sem = InputUtils.readLine(scanner, "Semester (empty = all): ").trim();
        List<Enrollment> filtered = new ArrayList<>();
        for (Enrollment e : enrollments) {
            if (sem.isEmpty() || sem.equalsIgnoreCase(e.getSemester())) {
                filtered.add(e);
            }
        }
        Map<String, Integer> bySubject = new HashMap<>();
        for (Enrollment e : filtered) {
            bySubject.put(e.getSubjectId(), bySubject.getOrDefault(e.getSubjectId(), 0) + 1);
        }
        String[] subHeaders = {"Subject", "Name", "Enrolled"};
        List<String[]> subRows = new ArrayList<>();
        Map<String, Integer> byDept = new HashMap<>();
        for (Map.Entry<String, Integer> entry : bySubject.entrySet()) {
            Subject subj = findSubjectById(entry.getKey());
            String code = subj == null ? entry.getKey() : subj.getCode();
            String name = subj == null ? "MISSING" : text(subj.getName());
            subRows.add(new String[]{code, name, String.valueOf(entry.getValue())});
            String dept = (subj == null || subj.getDepartment() == null) ? "(unknown)" : subj.getDepartment();
            byDept.put(dept, byDept.getOrDefault(dept, 0) + entry.getValue());
        }
        System.out.println("-- By subject --");
        FormatUtils.printTable(subHeaders, subRows);
        String[] deptHeaders = {"Department", "Enrolled"};
        List<String[]> deptRows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : byDept.entrySet()) {
            deptRows.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
        }
        System.out.println("-- By department --");
        FormatUtils.printTable(deptHeaders, deptRows);
    }

    private void undo() {
        if (!history.canUndo()) {
            System.out.println("  Nothing to undo.");
            return;
        }
        List<Enrollment> prev = history.undo(enrollments);
        enrollments.clear();
        enrollments.addAll(prev);
        if (save()) {
            System.out.println("  Undid last change.");
        }
    }

    private void redo() {
        if (!history.canRedo()) {
            System.out.println("  Nothing to redo.");
            return;
        }
        List<Enrollment> next = history.redo(enrollments);
        enrollments.clear();
        enrollments.addAll(next);
        if (save()) {
            System.out.println("  Redid last change.");
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
        List<WaitlistEntry> all = waitlist.listAll();
        String[] headers = {"Pos", "Student", "Name", "Subject", "Sem", "Since"};
        List<String[]> rows = new ArrayList<>();
        int pos = 0;
        for (WaitlistEntry w : all) {
            pos++;
            Student stu = findStudent(w.getStudentId());
            Subject subj = findSubjectById(w.getSubjectId());
            rows.add(new String[]{
                    String.valueOf(pos),
                    w.getStudentId(),
                    stu == null ? "MISSING" : stu.getFullName(),
                    subj == null ? w.getSubjectId() : subj.getCode(),
                    text(w.getSemester()),
                    w.getRequestDate() == null ? "" : w.getRequestDate().toString()
            });
        }
        FormatUtils.printTable(headers, rows);
    }

    private void joinWaitlist() {
        FormatUtils.printHeader("JOIN WAITLIST");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        Student stu = findStudent(sid);
        if (stu == null) {
            System.out.println("  Not found: " + sid);
            return;
        }
        Subject subj = readSubject();
        if (subj == null) return;
        String rawSem = InputUtils.readLine(scanner, "Semester [" + text(stu.getCurrentSemester()) + "]: ");
        String sem = rawSem.isEmpty() ? text(stu.getCurrentSemester()) : rawSem;
        doJoin(stu, subj, sem);
    }

    private void offerWaitlistJoin(Student stu, Subject subj, String semester) {
        boolean join = InputUtils.readYesNo(scanner, "Join waitlist?", false);
        if (!join) return;
        doJoin(stu, subj, semester);
    }

    private void doJoin(Student stu, Subject subj, String semester) {
        try {
            ValidationUtils.validateWaitlistJoin(stu.getId(), subj.getId(), semester,
                    students, subjects, enrollments, records, waitlist.listAll());
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot waitlist: " + e.getMessage());
            return;
        }
        try {
            waitlist.join(new WaitlistEntry(stu.getId(), subj.getId(), semester.trim(), LocalDate.now()));
            System.out.println("  Waitlisted (#" + waitlist.size(subj.getId(), semester.trim())
                    + " for " + subj.getCode() + " " + semester.trim() + ")");
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot waitlist: " + e.getMessage());
        }
    }

    private void admitHead() {
        FormatUtils.printHeader("ADMIT FROM WAITLIST");
        Subject subj = readSubject();
        if (subj == null) return;
        String sem = InputUtils.readRequiredLine(scanner, "Semester: ");
        WaitlistEntry head = waitlist.peekHead(subj.getId(), sem.trim());
        if (head == null) {
            System.out.println("  No one waiting for " + subj.getCode() + " (" + sem.trim() + ").");
            return;
        }
        try {
            ValidationUtils.validateRegistration(head.getStudentId(), head.getSubjectId(), head.getSemester(),
                    students, subjects, enrollments, records);
        } catch (IllegalArgumentException e) {
            System.out.println("  Cannot admit " + head.getStudentId() + ": " + e.getMessage()
                    + " — kept on waitlist.");
            return;
        }
        history.snapshot(enrollments);
        enrollments.add(new Enrollment(head.getStudentId(), head.getSubjectId(),
                head.getSemester(), LocalDate.now()));
        try {
            waitlist.admit(subj.getId(), sem.trim());
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return;
        }
        if (save()) {
            System.out.println("  Admitted " + head.getStudentId() + " -> " + subj.getCode()
                    + " (" + head.getSemester() + ")");
            System.out.println("  Note: undo reverts the enrollment only; re-join the waitlist manually if needed.");
        }
    }

    private void leaveWaitlist() {
        FormatUtils.printHeader("LEAVE WAITLIST");
        String sid = InputUtils.readRequiredLine(scanner, "Student ID: ");
        String subInput = InputUtils.readRequiredLine(scanner, "Subject ID or code: ");
        Subject subj = findSubjectById(subInput);
        if (subj == null) subj = findSubjectByCode(subInput);
        String subjectId = subj == null ? subInput.trim() : subj.getId();
        String sem = InputUtils.readRequiredLine(scanner, "Semester: ");
        try {
            boolean removed = waitlist.leave(sid.trim(), subjectId, sem.trim());
            System.out.println(removed ? "  Removed from waitlist." : "  No matching waitlist entry.");
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
            Subject subj = findSubjectById(e.getSubjectId());
            String subjShown = subj == null ? e.getSubjectId() + " MISSING" : subj.getCode();
            if (showStudent) {
                Student stu = findStudent(e.getStudentId());
                rows.add(new String[]{
                        e.getStudentId(),
                        stu == null ? "MISSING" : stu.getFullName(),
                        subjShown,
                        text(e.getSemester()),
                        e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
                });
            } else {
                rows.add(new String[]{
                        subjShown,
                        text(e.getSemester()),
                        e.getEnrollmentDate() == null ? "" : e.getEnrollmentDate().toString()
                });
            }
        }
        FormatUtils.printTable(headers, rows);
    }

    // ===== Lookup helpers =====

    private Student findStudent(String id) {
        for (Student s : students) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private Subject readSubject() {
        String input = InputUtils.readRequiredLine(scanner, "Subject ID or code: ");
        Subject subj = findSubjectById(input);
        if (subj == null) subj = findSubjectByCode(input);
        if (subj == null) {
            System.out.println("  Not found: " + input);
        }
        return subj;
    }

    private Subject findSubjectById(String id) {
        for (Subject s : subjects) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(id.trim())) {
                return s;
            }
        }
        return null;
    }

    private Subject findSubjectByCode(String code) {
        for (Subject s : subjects) {
            if (s.getCode() != null && s.getCode().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }

    private boolean save() {
        try {
            FileService.saveEnrollments(filePath, enrollments);
            return true;
        } catch (IOException e) {
            System.out.println("  Save failed: " + e.getMessage());
            return false;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
