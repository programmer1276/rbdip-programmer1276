package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

/** Проверяет обязательные поля запроса до обращения к каталогу и записи заказа. */
@Component
public class OrderRequestValidator {

    public void validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName");
        requireText(request.customerAddress(), "customerAddress");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
