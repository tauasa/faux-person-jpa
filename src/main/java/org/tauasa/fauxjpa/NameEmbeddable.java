package org.tauasa.fauxjpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.tauasa.faux.model.Name;

/** A faux-person {@link Name}, stored as two columns of the person's row. */
@Embeddable
public class NameEmbeddable {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** For JPA. */
    protected NameEmbeddable() {
    }

    /**
     * Copies a faux-person name.
     *
     * @param name the name to store
     * @return the embeddable
     */
    public static NameEmbeddable from(Name name) {
        NameEmbeddable e = new NameEmbeddable();
        e.firstName = name.firstName();
        e.lastName = name.lastName();
        return e;
    }

    /**
     * The stored name as a faux-person record.
     *
     * @return the name
     */
    public Name toName() {
        return new Name(firstName, lastName);
    }
}
