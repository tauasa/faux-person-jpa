package org.tauasa.fauxjpa;

import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.tauasa.faux.model.Name;
import org.tauasa.faux.model.NativeName;

/**
 * A faux-person {@link Name}, stored as columns of the person's row: the first
 * name and surname, and, when the name has one, its native form (Hangul, kanji,
 * Cyrillic and so on). The native columns are null for a name without one, and
 * for rows saved before they existed.
 */
@Embeddable
public class NameEmbeddable {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "native_first_name")
    private String nativeFirstName;

    @Column(name = "native_last_name")
    private String nativeLastName;

    @Column(name = "native_full_name")
    private String nativeFullName;

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
        name.nativeName().ifPresent(n -> {
            e.nativeFirstName = n.firstName();
            e.nativeLastName = n.lastName();
            e.nativeFullName = n.fullName();
        });
        return e;
    }

    /**
     * The stored name as a faux-person record.
     *
     * @return the name
     */
    public Name toName() {
        Optional<NativeName> nativeName = nativeFullName == null ? Optional.empty()
                : Optional.of(new NativeName(nativeFirstName, nativeLastName, nativeFullName));
        return new Name(firstName, lastName, nativeName);
    }
}
