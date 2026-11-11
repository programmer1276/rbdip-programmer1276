package com.rbdip.bookstore.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select i from OrderItem i join fetch i.product where i.orderId in :orderIds order by i.id")
    List<OrderItem> findAllWithProducts(@Param("orderIds") List<Long> orderIds);

    @Query("""
            select count(i) from OrderItem i
            join Order o on o.id = i.orderId
            join o.customer c
            where i.product.id = :productId and c.firstName = :firstName and c.lastName = :lastName
            """)
    long countPurchasedItems(@Param("productId") Long productId,
            @Param("firstName") String firstName, @Param("lastName") String lastName);
}
