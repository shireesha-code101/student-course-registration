package org.example.registration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {

    @Test
    void isValidEmail_validCases() {
        assertTrue(ValidationUtil.isValidEmail("user@example.com"));
        assertTrue(ValidationUtil.isValidEmail("USER+tag@sub.domain.co"));
        assertTrue(ValidationUtil.isValidEmail("  user.name+tag@Example.ORG  "));
    }

    @Test
    void isValidEmail_invalidCases() {
        assertFalse(ValidationUtil.isValidEmail(null));
        assertFalse(ValidationUtil.isValidEmail(""));
        assertFalse(ValidationUtil.isValidEmail("no-at-symbol.com"));
        assertFalse(ValidationUtil.isValidEmail("a@b"));
        assertFalse(ValidationUtil.isValidEmail("@nouser.com"));
    }

    @Test
    void normalizeEmail_behaviour() {
        assertEquals("user@example.com", ValidationUtil.normalizeEmail("  User@Example.com  "));
        assertEquals("x@y.z", ValidationUtil.normalizeEmail("X@Y.Z"));
    }

    @Test
    void isValidPassword_rules() {
        // At least 8 chars, one upper, one lower, one special (per your regex)
        assertTrue(ValidationUtil.isValidPassword("Abcdefg!"));
        assertTrue(ValidationUtil.isValidPassword("Strong#Pass1"));
        assertFalse(ValidationUtil.isValidPassword(null));
        assertFalse(ValidationUtil.isValidPassword("short!1"));      // too short
        assertFalse(ValidationUtil.isValidPassword("alllower!!"));   // no upper
        assertFalse(ValidationUtil.isValidPassword("ALLUPPER!!"));   // no lower
        assertFalse(ValidationUtil.isValidPassword("NoSpecial12"));  // no special
    }
}
