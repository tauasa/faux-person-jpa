package org.tauasa.fauxexample;

import jakarta.persistence.PersistenceException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.tauasa.faux.PersonGenerator;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

/**
 * Generates random people with faux-person and saves them to a database.
 *
 * <pre>
 * java -jar target/faux-person-example-0.1.0-SNAPSHOT.jar -n 500 --seed 42
 * </pre>
 */
public final class App {

    /** The database used when {@code --db} isn't given: an H2 file under {@code ./data/}. */
    static final String DEFAULT_DB = "jdbc:h2:file:./data/faux-people";

    static final String USAGE = """
            usage: faux-person-example [options]

              -n, --count N        how many people to generate and save (default 100)
              -s, --seed S         seed, to generate the same people again (default random)
              -r, --region R       %s or mixed (default mixed)
                  --real-companies real employer names and domains (default: names
                                   end in [faux] and domains are under .example)
                  --db URL         JDBC URL (default %s)
              -h, --help           show this help

            Each run adds to what's already stored.
            """.formatted(regionList(), DEFAULT_DB);

    // Held so the settings aren't lost when the loggers are garbage-collected.
    private static final Logger HIBERNATE_LOG = Logger.getLogger("org.hibernate");
    // Warns that Hibernate's own connection pool isn't for production. True,
    // and expected in an example: a real application configures a pool.
    private static final Logger POOL_LOG = Logger.getLogger("org.hibernate.orm.connections.pooling");

    private App() {
    }

    /**
     * Entry point.
     *
     * @param args command-line arguments; {@code --help} lists them
     */
    public static void main(String[] args) {
        // Hibernate logs its start-up at INFO; keep warnings and errors only.
        HIBERNATE_LOG.setLevel(Level.WARNING);
        POOL_LOG.setLevel(Level.SEVERE);
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the app and returns its exit code: 0 ok, 1 a database failure, 2 bad arguments. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        int count = 100;
        Long seed = null;
        Region region = null;
        boolean realCompanies = false;
        String db = DEFAULT_DB;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-h", "--help" -> {
                        out.print(USAGE);
                        return 0;
                    }
                    case "-n", "--count" -> count = nonNegative(a, value(args, ++i, a));
                    case "-s", "--seed" -> seed = parseLong(a, value(args, ++i, a));
                    case "-r", "--region" -> region = parseRegion(value(args, ++i, a));
                    case "--real-companies" -> realCompanies = true;
                    case "--db" -> db = value(args, ++i, a);
                    default -> throw new IllegalArgumentException("unknown option: " + a);
                }
            }
        } catch (IllegalArgumentException e) {
            err.println("faux-person-example: " + e.getMessage());
            err.print(USAGE);
            return 2;
        }

        PersonGenerator.Builder b = PersonGenerator.builder().region(region).realCompanies(realCompanies);
        if (seed != null) {
            b.seed(seed);
        }
        PersonGenerator gen = b.build();
        err.println("seed: " + gen.seed());
        List<Person> people = gen.next(count);

        try (PeopleStore store = PeopleStore.open(db)) {
            int saved = store.saveAll(people);
            out.printf(Locale.ROOT, "saved %d people to %s%n", saved, db);
            out.printf(Locale.ROOT, "stored: %d people, %d companies%n", store.count(), store.countCompanies());
            Map<Region, Long> byRegion = store.countByRegion();
            if (!byRegion.isEmpty()) {
                out.println("largest regions: " + byRegion.entrySet().stream().limit(5)
                        .map(e -> e.getKey().name().toLowerCase(Locale.ROOT) + " " + e.getValue())
                        .collect(Collectors.joining(", ")));
            }
            return 0;
        } catch (PersistenceException | IllegalStateException e) {
            err.println("faux-person-example: database error: " + rootMessage(e));
            return 1;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage() != null ? c.getMessage() : c.toString();
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    private static long parseLong(String option, String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a whole number: " + v);
        }
    }

    private static int nonNegative(String option, String v) {
        long n = parseLong(option, v);
        if (n < 0 || n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(option + " must be 0 or more: " + v);
        }
        return (int) n;
    }

    private static Region parseRegion(String v) {
        if (v.equalsIgnoreCase("mixed")) {
            return null;
        }
        for (Region r : Region.values()) {
            if (r.name().equalsIgnoreCase(v)) {
                return r;
            }
        }
        throw new IllegalArgumentException("region must be " + regionList() + " or mixed: " + v);
    }

    private static String regionList() {
        return Arrays.stream(Region.values()).map(r -> r.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
