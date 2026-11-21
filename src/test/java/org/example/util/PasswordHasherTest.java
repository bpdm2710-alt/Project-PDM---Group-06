package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hashThenVerify() {
        String h = PasswordHasher.hash("Stu@123");
        assertTrue(h.startsWith("pbkdf2_sha256$120000$"));
        assertTrue(PasswordHasher.isHashed(h));
        assertTrue(PasswordHasher.verify("Stu@123", h));
        assertFalse(PasswordHasher.verify("stu@123", h));
        assertFalse(PasswordHasher.verify("", h));
        assertTrue(h.length() <= 255, "fits USER.Password_hash VARCHAR(255)");
    }

    @Test
    void saltMakesHashesDifferent() {
        assertNotEquals(PasswordHasher.hash("same"), PasswordHasher.hash("same"));
    }

    @Test
    void rejectsGarbageAndLegacyValues() {
        assertFalse(PasswordHasher.isHashed("sha256:stu_hash_101"));
        assertFalse(PasswordHasher.verify("x", "sha256:stu_hash_101"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$abc$!!$??"));
        assertFalse(PasswordHasher.verify("x", "pbkdf2_sha256$1000"));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("x")));
        assertTrue(PasswordHasher.needsRehash("sha256:stu_hash_101"));
        assertFalse(PasswordHasher.needsRehash(PasswordHasher.hash("x")));
    }
}
