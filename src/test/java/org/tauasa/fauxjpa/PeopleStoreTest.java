package org.tauasa.fauxjpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tauasa.faux.PersonGenerator;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

class PeopleStoreTest {

    private String url;
    private PeopleStore store;

    @BeforeEach
    void open() {
        // A fresh in-memory database per test, kept until the store closes.
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        store = PeopleStore.open(url);
    }

    @AfterEach
    void close() {
        store.close();
    }

    private static List<Person> people(long seed, int n) {
        return PersonGenerator.builder().seed(seed).build().next(n);
    }

    @Test
    void savedPeopleReadBackAsTheSameRecords() {
        // 500 is more than one batch, so flush-and-clear is covered too.
        List<Person> generated = people(42, 500);
        assertEquals(500, store.saveAll(generated));
        assertEquals(generated, store.findAll());
        assertEquals(500, store.count());
    }

    @Test
    void nativeNamesReadBackInTheirOwnScript() {
        List<Person> generated = PersonGenerator.builder().seed(11).region(Region.ROK).build().next(200);
        assertTrue(generated.stream().anyMatch(p -> p.name().nativeName().isPresent()),
                "the seed should give some Hangul names");
        store.saveAll(generated);
        assertEquals(generated, store.findAll());
    }

    @Test
    void companiesOutsideTheUsReadBackWithTheirCountry() {
        List<Person> generated = PersonGenerator.builder().seed(12).region(Region.JPN).build().next(100);
        assertTrue(generated.stream().anyMatch(p -> !p.employer().country().equals("US")),
                "the seed should give some employers outside the US");
        store.saveAll(generated);
        assertEquals(generated, store.findAll());
    }

    @Test
    void aCompanySavedBeforeTheCountryColumnReadsBackAsUs() throws SQLException {
        Person saved = PersonGenerator.builder().seed(13).region(Region.WESTERN).build().next(50).stream()
                .filter(p -> p.employer().country().equals("US")).findFirst().orElseThrow();
        store.saveAll(List.of(saved));
        // A database written on faux-person 0.2.0 has no country, and Hibernate's
        // update adds the column as null.
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            assertEquals(1, st.executeUpdate("UPDATE company SET country = NULL"));
        }
        assertEquals(List.of(saved), store.findAll());
    }

    @Test
    void eachCompanyIsStoredOnceAcrossRuns() {
        List<Person> first = people(1, 300);
        List<Person> second = people(2, 300);
        store.saveAll(first);
        store.saveAll(second);
        Set<String> domains = new ArrayList<Person>() {{ addAll(first); addAll(second); }}.stream()
                .map(p -> p.employer().domain()).collect(Collectors.toSet());
        assertEquals(domains, store.companyDomains());
        assertEquals(domains.size(), store.countCompanies());
        assertEquals(600, store.count());
    }

    @Test
    void markedAndRealCompaniesAreDifferentRows() {
        store.saveAll(PersonGenerator.builder().seed(5).build().next(50));
        long marked = store.countCompanies();
        store.saveAll(PersonGenerator.builder().seed(5).realCompanies(true).build().next(50));
        assertEquals(2 * marked, store.countCompanies());
        Set<String> domains = store.companyDomains();
        assertTrue(domains.stream().anyMatch(d -> d.endsWith(".example")), domains.toString());
        assertTrue(domains.stream().anyMatch(d -> !d.endsWith(".example")), domains.toString());
    }

    @Test
    void countByRegionSumsToTheTotalAndIsSorted() {
        store.saveAll(people(7, 400));
        Map<Region, Long> byRegion = store.countByRegion();
        assertEquals(400, byRegion.values().stream().mapToLong(Long::longValue).sum());
        List<Map.Entry<Region, Long>> rows = new ArrayList<>(byRegion.entrySet());
        for (int i = 1; i < rows.size(); i++) {
            Map.Entry<Region, Long> a = rows.get(i - 1), b = rows.get(i);
            assertTrue(a.getValue() > b.getValue()
                    || (a.getValue().equals(b.getValue()) && a.getKey().name().compareTo(b.getKey().name()) < 0),
                    rows.toString());
        }
    }

    @Test
    void findByLastNameReturnsOnlyThatSurname() {
        List<Person> generated = people(9, 300);
        store.saveAll(generated);
        String last = generated.get(0).lastName();
        List<Person> expected = generated.stream().filter(p -> p.lastName().equals(last)).toList();
        assertEquals(expected, store.findByLastName(last));
        assertTrue(store.findByLastName("No-such-surname").isEmpty());
    }

    @Test
    void aFailedSaveLeavesNothingBehind() {
        // The database rejects the last person (a phone longer than its column),
        // after a full batch has already been written, so the rollback has to
        // undo rows the database has seen.
        List<Person> broken = new ArrayList<>(people(3, PeopleStore.BATCH + 10));
        Person p = broken.get(broken.size() - 1);
        broken.set(broken.size() - 1, new Person(p.name(), p.gender(), p.region(), p.dateOfBirth(),
                p.email(), "5".repeat(100), p.address(), p.employer()));
        assertThrows(RuntimeException.class, () -> store.saveAll(broken));
        assertEquals(0, store.count());
        assertEquals(0, store.countCompanies());
    }

    @Test
    void nullPeopleAreRejectedBeforeAnythingIsWritten() {
        List<Person> withNull = new ArrayList<>(people(3, 5));
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> store.saveAll(withNull));
        assertEquals(0, store.count());
    }
}
