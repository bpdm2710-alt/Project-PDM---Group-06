package org.example.Service;

import org.example.DAO.jdbc.AttemptDAO.GradeRow;
import org.example.util.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Grading rule BR-42 (same as sp_finalize_attempt) and the login comparison, without a database. */
class GradingRulesTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");

    private static GradeRow mcq(Integer selected, boolean correct) {
        return new GradeRow(1, TEN, true, null, null, selected, correct);
    }

    private static GradeRow shortAnswer(String text) {
        return new GradeRow(2, TEN, false, text, "HAVING", null, false);
    }

    @Test
    void mcq() {
        assertEquals(TEN, StudentServiceImpl.autoMark(mcq(2, true)));
        assertEquals(BigDecimal.ZERO, StudentServiceImpl.autoMark(mcq(3, false)));
        assertEquals(BigDecimal.ZERO, StudentServiceImpl.autoMark(mcq(null, false)), "unanswered");
    }

    @Test
    void shortAnswerMatchingKeyGetsFullMarks() {
        assertEquals(TEN, StudentServiceImpl.autoMark(shortAnswer("having")));
        assertEquals(TEN, StudentServiceImpl.autoMark(shortAnswer("  HAVING \n")));
    }

    @Test
    void blankShortAnswerGetsZero() {
        assertEquals(BigDecimal.ZERO, StudentServiceImpl.autoMark(shortAnswer(null)));
        assertEquals(BigDecimal.ZERO, StudentServiceImpl.autoMark(shortAnswer(" \t\r\n")));
    }

    @Test
    void otherShortAnswerWaitsForInstructor() {
        assertNull(StudentServiceImpl.autoMark(shortAnswer("the HAVING clause")));
    }

    @Test
    void passwordComparison() {
        String h = PasswordHasher.hash("Inst@123");
        assertTrue(AuthServiceImpl.passwordMatches("Inst@123", h));
        assertFalse(AuthServiceImpl.passwordMatches("inst@123", h));
        assertTrue(AuthServiceImpl.passwordMatches("sha256:stu_hash_101", "sha256:stu_hash_101"), "legacy value");
        assertFalse(AuthServiceImpl.passwordMatches("x", null));
    }
}
