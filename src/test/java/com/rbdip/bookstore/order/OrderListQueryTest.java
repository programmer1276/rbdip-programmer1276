package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Проверяет масштабирование запросов и полноту ответа с разными клиентами и товарами. */
class OrderListQueryTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50})
    void loadsAllCustomersAndItemsInTwoQueries(int orderCount) {
        for (int i = 0; i < orderCount; i++) {
            Long orderId = seedOrder("Customer", Integer.toString(i));
            for (int item = 1; item <= 3; item++) {
                Long productId = jdbcTemplate.queryForObject(
                        "INSERT INTO products (name, price) VALUES (?, 10) RETURNING id", Long.class,
                        "Book " + i + ":" + item);
                jdbcTemplate.update("INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, ?)",
                        orderId, productId, item);
            }
        }
        Statistics stats = statistics();
        stats.clear();
        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        assertThat(response.getBody()).hasSize(orderCount);
        for (int i = 0; i < orderCount; i++) {
            Map<?, ?> order = (Map<?, ?>) response.getBody().get(i);
            assertThat(order.keySet()).isEqualTo(Set.of("id", "customerFullName", "status", "items"));
            assertThat(order.get("customerFullName")).isEqualTo("Customer " + i);
            assertThat(order.get("status")).isEqualTo("new");
            List<?> items = (List<?>) order.get("items");
            assertThat(items).hasSize(3);
            for (int j = 0; j < items.size(); j++) {
                Map<?, ?> item = (Map<?, ?>) items.get(j);
                assertThat(item.get("productName")).isEqualTo("Book " + i + ":" + (j + 1));
                assertThat(item.get("quantity")).isEqualTo(j + 1);
            }
        }
    }

    @Test
    void emptyListNeedsOnlyOneQuery() {
        Statistics stats = statistics();
        stats.clear();
        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void keepsLegacyOrdersWithoutItemsInResponse() {
        Long id = seedOrder("Empty", "");
        Statistics stats = statistics();
        stats.clear();
        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        assertThat(response.getBody()).hasSize(1);
        Map<?, ?> order = (Map<?, ?>) response.getBody().get(0);
        assertThat(((Number) order.get("id")).longValue()).isEqualTo(id);
        assertThat(order.get("customerFullName")).isEqualTo("Empty");
        assertThat((List<?>) order.get("items")).isEmpty();
    }

    private Long seedOrder(String firstName, String lastName) {
        Long customer = jdbcTemplate.queryForObject("""
                INSERT INTO customers (first_name, last_name, address) VALUES (?, ?, 'Address') RETURNING id
                """, Long.class, firstName, lastName);
        return jdbcTemplate.queryForObject("INSERT INTO orders (customer_id, status) VALUES (?, 'new') RETURNING id",
                Long.class, customer);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
