package org.example.registration.util;

import java.util.Locale;
import java.util.regex.Pattern;

public class ValidationUtil {

    // allow letters, digits, dash and underscore, min length 3
    private static final Pattern STUDENT_ID = Pattern.compile("^[A-Za-z0-9_-]{3,}$");

    // simple email pattern (sufficient for typical validation in examples)
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // password: at least 8 chars, at least one uppercase, one lowercase, and one special char
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9]).{8,}$");

    /**
     * Normalizes email (lowercase, trim).
     */
    public static String normalizeEmail(String e) {
        if (e == null) return null;
        return e.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Validates student id. Accepts letters, digits, hyphen and underscore.
     */
    public static boolean isValidStudentId(String id) {
        if (id == null) return false;
        return STUDENT_ID.matcher(id).matches();
    }

    /**
     * Validates email format.
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL.matcher(email.trim()).matches();
    }

    /**
     * Validates password strength: at least 8 chars, one upper, one lower, one special char.
     */
    public static boolean isValidPassword(String pw) {
        if (pw == null) return false;
        return PASSWORD.matcher(pw).matches();
    }
}
