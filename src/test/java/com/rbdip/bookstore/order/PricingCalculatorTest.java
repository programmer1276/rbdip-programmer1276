package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Регрессионные проверки порядка скидок, строгих границ и округления. */
class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "10, 2, regular, , 20.00",
        "10, 10, regular, , 100.00",
        "10, 11, regular, , 104.50",
        "100, 1, vip, , 90.00",
        "100, 1, wholesale, , 85.00",
        "100, 1, unknown, UNKNOWN, 100.00",
        "100, 1, , , 100.00",
        "100, 1, regular, SAVE10, 90.00",
        "100, 1, regular, SAVE20PERCENT, 80.00",
        "100, 1, vip, SAVE10, 80.00",
        "100, 1, wholesale, SAVE20PERCENT, 68.00",
        "5, 1, regular, SAVE10, 0.00",
        "10, 1, regular, SAVE10, 0.00",
        "-10, 1, regular, , 0.00",
        "1000, 1, regular, , 1000.00",
        "1001, 1, regular, , 980.98",
        "1010, 1, regular, SAVE10, 1000.00",
        "1100, 1, vip, , 990.00",
        "100, 20, vip, SAVE10, 1666.00",
        "0.335, 1, regular, , 0.34",
        "10, 0, regular, , 0.00",
        "10, -1, regular, , 0.00",
        "0, 11, vip, SAVE10, 0.00",
        "1000.01, 1, regular, , 980.01",
        "1010.01, 1, regular, SAVE10, 980.01",
        "100, 1, VIP, save10, 100.00",
        "100, 1, vip, SAVE20PERCENT, 72.00"
    })
    void preservesPricingRules(String price, int quantity, String customerType, String coupon, String expected) {
        BigDecimal total = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), customerType, coupon);
        assertThat(total).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void roundsOnceAfterSummingAllLines() {
        BigDecimal total = calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("0.335"), 1),
                new PricingCalculator.LineItem(new BigDecimal("0.335"), 1)), "regular", null);
        assertThat(total).isEqualTo(new BigDecimal("0.67"));
    }

    @Test
    void preservesRejectionOfNullPriceAndNullItemList() {
        assertThatThrownBy(() -> calculator.calculateOrderTotal(null, "regular", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(null, 1)), "regular", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void appliesCouponOnceToOrderRatherThanToEachLine() {
        BigDecimal total = calculator.calculateOrderTotal(List.of(
                new PricingCalculator.LineItem(new BigDecimal("20"), 1),
                new PricingCalculator.LineItem(new BigDecimal("30"), 1)), "regular", "SAVE10");
        assertThat(total).isEqualTo(new BigDecimal("40.00"));
    }

    @Test
    void handlesEmptyOrderTotal() {
        assertThat(calculator.calculateOrderTotal(List.of(), "regular", null)).isEqualTo(new BigDecimal("0.00"));
    }
}
