package com.rbdip.bookstore.contracts;

/** Межмодульный контракт: проверка наличия заказа товара у автора с указанным именем. */
public interface PurchaseHistory {

    boolean hasPurchased(Long productId, String customerName);
}
