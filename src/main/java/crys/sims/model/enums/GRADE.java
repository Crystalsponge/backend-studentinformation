package crys.sims.model.enums;

/**
 * Letter grades with GPA point values.
 * Note: A_PLUS and A intentionally share 4.0 points — they are distinct
 * letters on the transcript but contribute equally to GPA calculation.
 */
public enum GRADE {
    A_PLUS(4.0),
    A(4.0),
    A_MINUS(3.7),
    B_PLUS(3.3),
    B(3.0),
    B_MINUS(2.7),
    C_PLUS(2.3),
    C(2.0),
    D(1.0),
    F(0.0);

    private final double gpaValue;

    GRADE(double gpaValue) {
        this.gpaValue = gpaValue;
    }

    public double getGpaValue() {
        return gpaValue;
    }

    public static GRADE fromString(String s) {
        if (s == null) return null;
        String normalized = s.trim().toUpperCase().replace("+", "_PLUS").replace("-", "_MINUS");
        try {
            return GRADE.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
