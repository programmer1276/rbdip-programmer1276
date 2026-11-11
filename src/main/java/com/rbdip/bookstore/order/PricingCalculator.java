package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/** Рассчитывает итог: скидка на позицию, скидка клиента, купон, скидка на крупный заказ. */
@Component
public class PricingCalculator {

    private static final String VIP_CUSTOMER = "vip";
    private static final String WHOLESALE_CUSTOMER = "wholesale";
    private static final String FIXED_DISCOUNT_COUPON = "SAVE10";
    private static final String PERCENT_DISCOUNT_COUPON = "SAVE20PERCENT";
    private static final int BULK_QUANTITY_THRESHOLD = 10;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal BULK_MULTIPLIER = new BigDecimal("0.95");
    private static final BigDecimal VIP_MULTIPLIER = new BigDecimal("0.9");
    private static final BigDecimal WHOLESALE_MULTIPLIER = new BigDecimal("0.85");
    private static final BigDecimal COUPON_DISCOUNT = BigDecimal.TEN;
    private static final BigDecimal COUPON_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_MULTIPLIER = new BigDecimal("0.98");

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal subtotal = items.stream()
                .map(this::calculateLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = applyCustomerDiscount(subtotal, customerType);
        total = applyCoupon(total, couponCode).max(BigDecimal.ZERO);
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            total = total.multiply(LARGE_ORDER_MULTIPLIER);
        }
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLineTotal(LineItem item) {
        BigDecimal total = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        return item.quantity() > BULK_QUANTITY_THRESHOLD ? total.multiply(BULK_MULTIPLIER) : total;
    }

    private BigDecimal applyCustomerDiscount(BigDecimal total, String customerType) {
        if (VIP_CUSTOMER.equals(customerType)) {
            return total.multiply(VIP_MULTIPLIER);
        }
        if (WHOLESALE_CUSTOMER.equals(customerType)) {
            return total.multiply(WHOLESALE_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if (FIXED_DISCOUNT_COUPON.equals(couponCode)) {
            return total.subtract(COUPON_DISCOUNT);
        }
        if (PERCENT_DISCOUNT_COUPON.equals(couponCode)) {
            return total.multiply(COUPON_MULTIPLIER);
        }
        return total;
    }
}
