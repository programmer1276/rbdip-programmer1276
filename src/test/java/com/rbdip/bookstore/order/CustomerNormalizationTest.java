package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbdip.bookstore.customer.CustomerService;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Проверяет запись и чтение нормализованной схемы через настоящий PostgreSQL. */
class CustomerNormalizationTest extends AbstractIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Test
    void reusesCustomerWithMissingPhoneAndKeepsApiResponse() {
        Long productId = createProduct("Book", "25.00");
        createOrder("Address", productId);
        createOrder("Address", productId);

        assertThat(count("customers")).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT customer_id) FROM orders", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items", Long.class))
                .containsExactly(productId, productId);
        ResponseEntity<List> response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        for (Object value : response.getBody()) {
            Map<?, ?> order = (Map<?, ?>) value;
            assertThat(order.get("customerFullName")).isEqualTo("Customer");
            assertThat(order.get("status")).isEqualTo("new");
            Map<?, ?> item = (Map<?, ?>) ((List<?>) order.get("items")).get(0);
            assertThat(item.get("productName")).isEqualTo("Book");
            assertThat(item.get("quantity")).isEqualTo(2);
        }
    }

    @Test
    void keepsDifferentContactsAndIdenticallyNamedProductsSeparate() {
        Long first = createProduct("Same name", "10.00");
        Long second = createProduct("Same name", "20.00");
        createOrder("First address", first);
        createOrder("Second address", second);
        assertThat(count("customers")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(first, second);
        assertThat(jdbcTemplate.queryForList("SELECT address FROM customers ORDER BY id", String.class))
                .containsExactly("First address", "Second address");
    }

    @Test
    void doesNotLeaveCustomerOrOrderAfterInvalidProduct() {
        CreateOrderRequest request = new CreateOrderRequest("Customer", "Address", null, null, null,
                List.of(new CreateOrderRequest.Item(-1L, 1)));
        assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(count("customers")).isZero();
        assertThat(count("orders")).isZero();
        assertThat(count("order_items")).isZero();
    }

    @Test
    void concurrentCustomerCreationDoesNotCreateDuplicates() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return customerService.findOrCreate("Customer", "Address", null).getId();
                }));
            }
            start.countDown();
            List<Long> ids = new ArrayList<>();
            for (Future<Long> result : results) {
                ids.add(result.get(20, TimeUnit.SECONDS));
            }
            assertThat(ids).doesNotContainNull().containsOnly(ids.get(0));
        }
        assertThat(count("customers")).isEqualTo(1);
    }

    @Test
    void foreignKeysRejectMissingCustomerAndProduct() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO orders (customer_id, status) VALUES (-1, 'new')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long productId = createProduct("Book", "25.00");
        createOrder("Address", productId);
        Long orderId = jdbcTemplate.queryForObject("SELECT id FROM orders", Long.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, -1, 1)", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private Long createProduct(String name, String price) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/products", Map.of("name", name, "price", new BigDecimal(price)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }

    private void createOrder(String address, Long productId) {
        CreateOrderRequest request = new CreateOrderRequest("Customer", address, null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 2)));
        ResponseEntity<Map> response = restTemplate.postForEntity("/orders", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
