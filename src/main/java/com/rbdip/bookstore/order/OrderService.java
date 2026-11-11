package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Координирует создание заказа в одной транзакции.
 */
@Service
public class OrderService {

    private final OrderRequestValidator validator;
    private final OrderItemResolver itemResolver;
    private final OrderWriter orderWriter;
    private final PricingCalculator pricingCalculator;
    private final OrderConfirmationNotifier notifier;

    public OrderService(
            OrderRequestValidator validator,
            OrderItemResolver itemResolver,
            OrderWriter orderWriter,
            PricingCalculator pricingCalculator,
            OrderConfirmationNotifier notifier) {
        this.validator = validator;
        this.itemResolver = itemResolver;
        this.orderWriter = orderWriter;
        this.pricingCalculator = pricingCalculator;
        this.notifier = notifier;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);
        List<OrderItemResolver.ResolvedItem> items = itemResolver.resolve(request.items());
        List<PricingCalculator.LineItem> lineItems = items.stream()
                .map(OrderItemResolver.ResolvedItem::toLineItem)
                .toList();
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                lineItems, request.customerType() == null ? "regular" : request.customerType(), request.couponCode());
        Order order = orderWriter.save(request, items);
        notifier.sendConfirmation(request.customerFullName(), order.getId(), total);
        return order;
    }
}
