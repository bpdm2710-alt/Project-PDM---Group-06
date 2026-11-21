package org.example.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Splitting of the demo SQL files (no database needed). */
class SqlScriptRunnerTest {

    @Test
    void splitsOnSemicolonAtLineEndAndSkipsCommentsAndTransactionLines() {
        List<String> s = SqlScriptRunner.split("""
                -- header
                USE `Online_Examination`;
                SET @now = NOW();
                START TRANSACTION;
                INSERT INTO `T` (`a`) VALUES
                  -- a comment inside the statement
                  ('x; y'),
                  ('z');
                COMMIT;
                SELECT 1""");
        assertEquals(3, s.size());
        assertEquals("SET @now = NOW()", s.get(0));
        assertTrue(s.get(1).contains("('x; y')"), "a ';' inside a value does not split");
        assertFalse(s.get(1).contains("comment"));
        assertEquals("SELECT 1", s.get(2));
    }

    @Test
    void packedDemoFilesSplitIntoStatements() throws IOException {
        for (String f : new String[]{"03_demo_seed.sql", "03_demo_cleanup.sql",
                "04_extra_sample_data.sql", "04_extra_sample_data_cleanup.sql"}) {
            try (InputStream in = SqlScriptRunner.class.getResourceAsStream("/sql/" + f)) {
                assertNotNull(in, "packed in the app: sql/" + f);
                List<String> s = SqlScriptRunner.split(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                assertFalse(s.isEmpty(), f);
                assertTrue(s.stream().noneMatch(x -> x.startsWith("--")), f);
                assertTrue(s.stream().noneMatch(x -> x.equalsIgnoreCase("COMMIT")), f);
            }
        }
    }
}
