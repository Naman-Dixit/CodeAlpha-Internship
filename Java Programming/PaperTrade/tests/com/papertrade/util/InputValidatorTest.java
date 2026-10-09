package com.papertrade.util;

import static org.junit.jupiter.api.Assertions.*;

import com.papertrade.exception.ValidationException;
import org.junit.jupiter.api.Test;

class InputValidatorTest {
    @Test
    void emailValidationAndNormalisation() throws Exception {
        assertEquals("a@b.com", InputValidator.validateEmail("  A@B.com "));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail("not-an-email"));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(""));
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(null));
    }

    @Test
    void passwordRules() {
        assertDoesNotThrow(() -> InputValidator.validatePassword("abcdef12"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("short1"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("onlyletters"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("12345678"));
    }

    @Test
    void nameMustNotBeBlank() {
        assertThrows(ValidationException.class, () -> InputValidator.validateName("   "));
    }

    @Test
    void quantityMustBePositiveInteger() throws Exception {
        assertEquals(5, InputValidator.parsePositiveInt(" 5 ", "Quantity"));
        for (String bad : new String[]{"abc", "-5", "0", "", "2.5", "99999999999"}) {
            assertThrows(ValidationException.class, () -> InputValidator.parsePositiveInt(bad, "Quantity"), bad);
        }
    }

    @Test
    void symbolAndMenuParsing() throws Exception {
        assertEquals("AAPL", InputValidator.normalizeSymbol(" aapl "));
        assertThrows(ValidationException.class, () -> InputValidator.normalizeSymbol("A1;DROP"));
        assertEquals(3, InputValidator.parseMenuChoice("3"));
        assertEquals(-1, InputValidator.parseMenuChoice("abc"));
        assertEquals(-1, InputValidator.parseMenuChoice(null));
    }
}
