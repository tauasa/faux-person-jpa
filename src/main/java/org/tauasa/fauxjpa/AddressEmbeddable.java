package org.tauasa.fauxjpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.tauasa.faux.model.Address;

/** A faux-person {@link Address}, stored as columns of the person's row. */
@Embeddable
public class AddressEmbeddable {

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    /** For JPA. */
    protected AddressEmbeddable() {
    }

    /**
     * Copies a faux-person address.
     *
     * @param a the address to store
     * @return the embeddable
     */
    public static AddressEmbeddable from(Address a) {
        AddressEmbeddable e = new AddressEmbeddable();
        e.street = a.street();
        e.city = a.city();
        e.state = a.state();
        e.postalCode = a.postalCode();
        e.country = a.country();
        return e;
    }

    /**
     * The stored address as a faux-person record.
     *
     * @return the address
     */
    public Address toAddress() {
        return new Address(street, city, state, postalCode, country);
    }
}
