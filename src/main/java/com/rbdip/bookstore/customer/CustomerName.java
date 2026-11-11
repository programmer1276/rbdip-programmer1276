package com.rbdip.bookstore.customer;

/** Разделяет имя по первому пробелу и позволяет восстановить исходную строку. */
public record CustomerName(String firstName, String lastName) {

    public static CustomerName fromFullName(String fullName) {
        int separator = fullName.indexOf(' ');
        if (separator > 0 && separator < fullName.length() - 1) {
            return new CustomerName(fullName.substring(0, separator), fullName.substring(separator + 1));
        }
        return new CustomerName(fullName, "");
    }

    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }
}
