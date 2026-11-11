package com.rbdip.bookstore.order;

import com.rbdip.bookstore.contracts.PurchaseHistory;
import com.rbdip.bookstore.customer.CustomerName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Адаптер истории покупок: детали хранения заказов не выходят за границу модуля. */
@Service
public class OrderPurchaseHistory implements PurchaseHistory {

    private final OrderItemRepository orderItemRepository;

    public OrderPurchaseHistory(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPurchased(Long productId, String customerName) {
        if (productId == null || customerName == null || customerName.isBlank()) {
            return false;
        }
        CustomerName name = CustomerName.fromFullName(customerName);
        return orderItemRepository.countPurchasedItems(productId, name.firstName(), name.lastName()) > 0;
    }
}
