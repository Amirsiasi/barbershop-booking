package at.barberbook.booking;

import jakarta.persistence.*;

/**
 * The customer from customer-crud-spring-react, now belonging to a shop and identified by phone number
 * (customers book without an account; the phone number is what a barbershop actually uses).
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long shopId;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;

    protected Customer() {
    }

    public Customer(Long shopId, String firstName, String lastName, String phone, String email) {
        this.shopId = shopId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.email = email;
    }

    /** Returning customers may correct their name or add an e-mail address. */
    public void updateContact(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        if (email != null && !email.isBlank()) this.email = email;
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
