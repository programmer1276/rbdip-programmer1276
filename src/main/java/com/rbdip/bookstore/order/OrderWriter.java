package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Сохраняет заказ и его позиции внутри транзакции вызывающего сервиса. */
@Component
public class OrderWriter {

    private static final String INITIAL_STATUS = "new";

    private final CustomerService customerService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderWriter(
            OrderRepository orderRepository, OrderItemRepository orderItemRepository, CustomerService customerService) {
        this.customerService = customerService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Order save(CreateOrderRequest request, List<OrderItemResolver.ResolvedItem> items) {
        Customer customer = customerService.findOrCreate(
                request.customerFullName(), request.customerAddress(), request.customerPhone());
        Order order = orderRepository.save(new Order(customer, INITIAL_STATUS));
        for (OrderItemResolver.ResolvedItem item : items) {
            orderItemRepository.save(new OrderItem(
                    order.getId(), item.product(), item.quantity()));
        }
        return order;
    }
}
