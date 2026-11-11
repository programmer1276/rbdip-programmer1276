package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Сохраняет заказ и его позиции внутри транзакции вызывающего сервиса. */
@Component
public class OrderWriter {

    private static final String INITIAL_STATUS = "new";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderWriter(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(CreateOrderRequest request, List<OrderItemResolver.ResolvedItem> items) {
        Order order = orderRepository.save(new Order(
                request.customerFullName(), request.customerAddress(), request.customerPhone(), INITIAL_STATUS));
        for (OrderItemResolver.ResolvedItem item : items) {
            Product product = item.product();
            orderItemRepository.save(new OrderItem(
                    order.getId(), product.getName(), product.getPrice(), item.quantity()));
        }
        return order;
    }
}
