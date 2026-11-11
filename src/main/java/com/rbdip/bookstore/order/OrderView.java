package com.rbdip.bookstore.order;

import java.util.List;

/** Ответ API без JPA-сущностей и обращений к БД при сериализации. */
public record OrderView(Long id, String customerFullName, String status, List<Item> items) {

    public record Item(String productName, Integer quantity) {
    }
}
