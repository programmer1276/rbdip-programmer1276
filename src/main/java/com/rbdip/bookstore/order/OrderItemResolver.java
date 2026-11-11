package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/** Подготавливает позиции из каталога, сохраняя соответствие товара и количества. */
@Component
public class OrderItemResolver {

    private final ProductRepository productRepository;

    public OrderItemResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ResolvedItem> resolve(List<CreateOrderRequest.Item> items) {
        return items.stream().map(this::resolveItem).toList();
    }

    private ResolvedItem resolveItem(CreateOrderRequest.Item item) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new IllegalArgumentException("product " + item.productId() + " not found"));
        int quantity = item.quantity() == null ? 1 : item.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new ResolvedItem(product, quantity);
    }

    public record ResolvedItem(Product product, int quantity) {

        public PricingCalculator.LineItem toLineItem() {
            return new PricingCalculator.LineItem(product.getPrice(), quantity);
        }
    }
}
