package org.tauasa.fauxjpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.tauasa.faux.model.Company;

/**
 * A faux-person {@link Company}, stored once and shared by every person who
 * works there. Its domain is unique, so a marked company ({@code costco.example})
 * and the real one ({@code costco.com}) are different rows.
 */
@Entity
@Table(name = "company")
public class CompanyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "company_seq")
    @SequenceGenerator(name = "company_seq", sequenceName = "company_seq", allocationSize = 50)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city", nullable = false)
    private String city;

    /** Two letters for a US company; empty for one in another country. */
    @Column(name = "state", nullable = false, length = 2)
    private String state;

    /**
     * ISO 3166-1 alpha-2. Nullable, so Hibernate can add the column to a
     * database saved before it existed; those rows are all US companies.
     */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "domain", nullable = false, unique = true)
    private String domain;

    /** For JPA. */
    protected CompanyEntity() {
    }

    /**
     * Copies a faux-person company.
     *
     * @param c the company to store
     * @return a new, unsaved entity
     */
    public static CompanyEntity from(Company c) {
        CompanyEntity e = new CompanyEntity();
        e.name = c.name();
        e.city = c.city();
        e.state = c.state();
        e.country = c.country();
        e.domain = c.domain();
        return e;
    }

    /**
     * The stored company as a faux-person record.
     *
     * @return the company
     */
    public Company toCompany() {
        return new Company(name, city, state, country == null ? "US" : country, domain);
    }

    /**
     * The database id, or {@code null} before it's saved.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * The company's web domain, which identifies it.
     *
     * @return the domain
     */
    public String getDomain() {
        return domain;
    }
}
