package com.rbdip.bookstore.customer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Переиспользует клиента, в том числе при одновременном создании заказов. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer findOrCreate(String name, String address, String phone) {
        CustomerName customerName = CustomerName.fromFullName(name);
        customerRepository.insertIfMissing(customerName.firstName(), customerName.lastName(), address, phone);
        return customerRepository.findByFirstNameAndLastNameAndAddressAndPhone(
                        customerName.firstName(), customerName.lastName(), address, phone)
                .orElseThrow(() -> new IllegalStateException("customer was not saved"));
    }
}
