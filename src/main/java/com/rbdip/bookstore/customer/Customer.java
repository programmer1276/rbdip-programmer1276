package com.rbdip.bookstore.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Контактные данные клиента, общие для его заказов. */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String address;
    private String phone;

    protected Customer() {
        // for JPA
    }

    public Customer(String fullName, String address, String phone) {
        CustomerName name = CustomerName.fromFullName(fullName);
        this.firstName = name.firstName();
        this.lastName = name.lastName();
        this.address = address;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return new CustomerName(firstName, lastName).fullName();
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }
}
