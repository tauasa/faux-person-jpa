package org.tauasa.fauxexample;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.tauasa.faux.model.Gender;
import org.tauasa.faux.model.Person;
import org.tauasa.faux.model.Region;

/**
 * A faux-person {@link Person}, stored as one row of the {@code person} table.
 * The name and address are columns of the same row, and the employer is a
 * reference to a shared {@link CompanyEntity}.
 */
@Entity
@Table(name = "person", indexes = @Index(name = "person_last_name", columnList = "last_name"))
public class PersonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "person_seq")
    @SequenceGenerator(name = "person_seq", sequenceName = "person_seq", allocationSize = 50)
    private Long id;

    @Embedded
    private NameEmbeddable name;

    // By name, never by position, so a new value in the enum can't change
    // what a stored one means.
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 16)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 16)
    private Region region;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Embedded
    private AddressEmbeddable address;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false)
    private CompanyEntity employer;

    /** For JPA. */
    protected PersonEntity() {
    }

    /**
     * Copies a faux-person person, with the employer as an entity that's
     * already saved or saved in the same transaction.
     *
     * @param p        the person to store
     * @param employer the stored company the person works for
     * @return a new, unsaved entity
     */
    public static PersonEntity from(Person p, CompanyEntity employer) {
        PersonEntity e = new PersonEntity();
        e.name = NameEmbeddable.from(p.name());
        e.gender = p.gender();
        e.region = p.region();
        e.dateOfBirth = p.dateOfBirth();
        e.email = p.email();
        e.phone = p.phone();
        e.address = AddressEmbeddable.from(p.address());
        e.employer = employer;
        return e;
    }

    /**
     * The stored person as a faux-person record. Reads the employer, so call
     * it while the entity manager that loaded this entity is open.
     *
     * @return the person
     */
    public Person toPerson() {
        return new Person(name.toName(), gender, region, dateOfBirth, email, phone,
                address.toAddress(), employer.toCompany());
    }

    /**
     * The database id, or {@code null} before it's saved.
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }
}
