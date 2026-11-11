package com.rbdip.bookstore.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO customers (first_name, last_name, address, phone)
            VALUES (:firstName, :lastName, :address, :phone)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfMissing(@Param("firstName") String firstName, @Param("lastName") String lastName,
            @Param("address") String address, @Param("phone") String phone);

    Optional<Customer> findByFirstNameAndLastNameAndAddressAndPhone(
            String firstName, String lastName, String address, String phone);
}
