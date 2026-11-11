package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerNameTest {

    @ParameterizedTest
    @CsvSource(value = {"Иван Петров|Иван|Петров", "Anna Maria Smith|Anna|Maria Smith", "Madonna|Madonna|''"},
            delimiter = '|')
    void splitsFirstWordAndRemainingName(String source, String first, String last) {
        CustomerName name = CustomerName.fromFullName(source);
        assertThat(name.firstName()).isEqualTo(first);
        assertThat(name.lastName()).isEqualTo(last);
        assertThat(name.fullName()).isEqualTo(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Иван Петров", "Madonna", " Иван", "Иван ", "Иван  Петров ", "Иван\tПетров", " ", ""})
    void preservesOriginalNameIncludingWhitespace(String source) {
        CustomerName name = CustomerName.fromFullName(source);
        assertThat(name.fullName()).isEqualTo(source);
        assertThat(new Customer(source, "Address", null).getFullName()).isEqualTo(source);
    }
}
