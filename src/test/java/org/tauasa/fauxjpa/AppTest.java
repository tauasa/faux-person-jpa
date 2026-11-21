package org.tauasa.fauxjpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppTest {

    private String out;
    private String err;

    private int run(String... args) {
        var o = new ByteArrayOutputStream();
        var e = new ByteArrayOutputStream();
        int code = App.run(args, new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        out = o.toString(StandardCharsets.UTF_8);
        err = e.toString(StandardCharsets.UTF_8);
        return code;
    }

    @Test
    void savesToTheDatabaseGivenAndTotalsGrowAcrossRuns(@TempDir Path dir) {
        String db = "jdbc:h2:file:" + dir.resolve("people").toAbsolutePath();
        assertEquals(0, run("-n", "120", "-s", "42", "--db", db));
        assertTrue(out.contains("saved 120 people to " + db), out);
        assertTrue(out.contains("stored: 120 people"), out);
        assertTrue(out.contains("largest regions: "), out);
        assertTrue(err.contains("seed: 42"), err);

        assertEquals(0, run("-n", "30", "-r", "jpn", "--db", db));
        assertTrue(out.contains("stored: 150 people"), out);

        try (PeopleStore store = PeopleStore.open(db)) {
            assertEquals(150, store.count());
            assertTrue(store.countByRegion().get(org.tauasa.faux.model.Region.JPN) >= 30);
        }
    }

    @Test
    void badArgumentsExitTwoWithUsage() {
        assertEquals(2, run("-n", "-1"));
        assertEquals(2, run("--bogus"));
        assertEquals(2, run("-r", "atlantis"));
        assertEquals(2, run("--db"));
        assertTrue(err.contains("usage:"), err);
        assertEquals(0, run("--help"));
        assertTrue(out.contains("--db URL"), out);
    }

    @Test
    void aDatabaseItCantOpenExitsOne() {
        assertEquals(1, run("-n", "1", "--db", "jdbc:nosuchdb:x"));
        assertTrue(err.contains("database error"), err);
    }
}
