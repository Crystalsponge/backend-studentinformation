package crys.sims.model;


import crys.sims.model.enums.GENDER;

import java.util.List;

public class Student {
    private String id;
    private String name;
    GENDER gender;
    private int currentSemester;
    private String email;
    private int currentCredit;
    private List<String> enrolledSubjects;
    private String department;

}
