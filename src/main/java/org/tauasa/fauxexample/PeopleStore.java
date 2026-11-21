package org.tauasa.fauxexample;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

/**
 * Saves faux-person {@link Person}s to a database through JPA, and reads them
 * back. Close it when you're done, to close the database connection.
 *
 * <pre>{@code
 * try (PeopleStore store = PeopleStore.open("jdbc:h2:file:./data/faux-people")) {
 *     store.saveAll(PersonGenerator.builder().seed(42).build().next(100));
 * }
 * }</pre>
 */
public final class PeopleStore implements AutoCloseable {

    /** The persistence unit in {@code META-INF/persistence.xml}. */
    public static final String PERSISTENCE_UNIT = "faux-person-example";

    /** People saved between flushes, which is also the JDBC batch size. */
    static final int BATCH = 50;

    /** Domains per {@code IN} list when looking up stored companies. */
    private static final int LOOKUP_CHUNK = 500;

    private final EntityManagerFactory emf;

    private PeopleStore(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * Opens a store on a JDBC URL. Hibernate creates the tables if they
     * aren't there, and keeps whatever is already stored.
     *
     * @param jdbcUrl for example {@code jdbc:h2:file:./data/faux-people}
     * @return an open store
     * @throws jakarta.persistence.PersistenceException if the database can't be reached
     */
    public static PeopleStore open(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        return new PeopleStore(Persistence.createEntityManagerFactory(PERSISTENCE_UNIT,
                Map.of("jakarta.persistence.jdbc.url", jdbcUrl)));
    }

    /**
     * Saves people in one transaction: all of them, or none if anything fails.
     * Each employer is stored once, matched by domain against this call's
     * people and against companies already in the database.
     *
     * @param people the people to save
     * @return how many were saved
     */
    public int saveAll(List<Person> people) {
        Objects.requireNonNull(people, "people");
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Map<String, Long> companyIds = storeCompanies(em, people);
            int n = 0;
            for (Person p : people) {
                CompanyEntity employer = em.getReference(CompanyEntity.class,
                        companyIds.get(p.employer().domain()));
                em.persist(PersonEntity.from(p, employer));
                if (++n % BATCH == 0) {
                    // Keep memory flat on a large run: write this batch, then
                    // let the persistence context forget it.
                    em.flush();
                    em.clear();
                }
            }
            tx.commit();
            return n;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Finds each employer's stored row, creates the missing ones, and returns
     * their ids by domain.
     */
    private static Map<String, Long> storeCompanies(EntityManager em, List<Person> people) {
        Map<String, Company> byDomain = new LinkedHashMap<>();
        for (Person p : people) {
            Objects.requireNonNull(p, "people must not contain null");
            byDomain.putIfAbsent(p.employer().domain(), p.employer());
        }
        Map<String, Long> ids = new HashMap<>();
        List<String> domains = new ArrayList<>(byDomain.keySet());
        for (int i = 0; i < domains.size(); i += LOOKUP_CHUNK) {
            List<String> chunk = domains.subList(i, Math.min(i + LOOKUP_CHUNK, domains.size()));
            em.createQuery("select c from CompanyEntity c where c.domain in :domains", CompanyEntity.class)
                    .setParameter("domains", chunk)
                    .getResultList()
                    .forEach(c -> ids.put(c.getDomain(), c.getId()));
        }
        for (Company c : byDomain.values()) {
            if (!ids.containsKey(c.domain())) {
                CompanyEntity e = CompanyEntity.from(c);
                em.persist(e);
                ids.put(c.domain(), e.getId());
            }
        }
        return ids;
    }

    /**
     * How many people are stored.
     *
     * @return the count
     */
    public long count() {
        return read(em -> em.createQuery("select count(p) from PersonEntity p", Long.class).getSingleResult());
    }

    /**
     * How many companies are stored.
     *
     * @return the count
     */
    public long countCompanies() {
        return read(em -> em.createQuery("select count(c) from CompanyEntity c", Long.class).getSingleResult());
    }

    /**
     * People per region, most first, and by region name when two are equal.
     *
     * @return a map in that order; regions with nobody stored are left out
     */
    public Map<Region, Long> countByRegion() {
        return read(em -> {
            Map<Region, Long> out = new LinkedHashMap<>();
            em.createQuery("select p.region, count(p) from PersonEntity p group by p.region"
                            + " order by count(p) desc, p.region", Object[].class)
                    .getResultList()
                    .forEach(row -> out.put((Region) row[0], (Long) row[1]));
            return out;
        });
    }

    /**
     * Stored people with a surname, in the order they were saved.
     *
     * @param lastName the surname, matched exactly
     * @return the people, as faux-person records
     */
    public List<Person> findByLastName(String lastName) {
        Objects.requireNonNull(lastName, "lastName");
        return read(em -> em.createQuery("select p from PersonEntity p join fetch p.employer"
                        + " where p.name.lastName = :last order by p.id", PersonEntity.class)
                .setParameter("last", lastName)
                .getResultList().stream().map(PersonEntity::toPerson).toList());
    }

    /**
     * Every stored person, in the order they were saved. Loads them all into
     * memory, so it suits tests and small databases.
     *
     * @return the people, as faux-person records
     */
    public List<Person> findAll() {
        return read(em -> em.createQuery("select p from PersonEntity p join fetch p.employer order by p.id",
                        PersonEntity.class)
                .getResultList().stream().map(PersonEntity::toPerson).toList());
    }

    /** The distinct domains of the stored companies, for tests. */
    Set<String> companyDomains() {
        return read(em -> new LinkedHashSet<>(em.createQuery(
                "select c.domain from CompanyEntity c order by c.domain", String.class).getResultList()));
    }

    private <T> T read(Function<EntityManager, T> query) {
        EntityManager em = emf.createEntityManager();
        try {
            return query.apply(em);
        } finally {
            em.close();
        }
    }

    /** Closes the database connection. */
    @Override
    public void close() {
        emf.close();
    }
}
