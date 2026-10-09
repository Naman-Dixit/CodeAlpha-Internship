package com.papertrade.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {
    @Test
    void hashIsNotPlaintextAndVerifies() {
        String hash = PasswordUtil.hash("Secret123");
        assertNotEquals("Secret123", hash);
        assertFalse(hash.contains("Secret123"));
        assertTrue(PasswordUtil.verify("Secret123", hash));
    }

    @Test
    void wrongPasswordFails() {
        assertFalse(PasswordUtil.verify("Wrong123", PasswordUtil.hash("Secret123")));
    }

    @Test
    void sameInputGivesDifferentSalts() {
        assertNotEquals(PasswordUtil.hash("Secret123"), PasswordUtil.hash("Secret123"));
    }

    @Test
    void malformedStoredHashIsRejectedNotThrown() {
        assertFalse(PasswordUtil.verify("x", "garbage"));
        assertFalse(PasswordUtil.verify("x", "a:b:c"));
    }
}
