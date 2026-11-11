package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Проверяет переход с заполненной V1, а не только создание пустой схемы. */
class NormalizationMigrationTest extends AbstractIntegrationTest {

    @Test
    void preservesLegacyOrdersAndReconstructsMissingProductLinks() {
        try {
            migrateTo("1");
            seedLegacyData();
            migrateTo("2");
            assertPreservedData();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lab2_upgrade_test.products", Long.class)).isEqualTo(5);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lab2_upgrade_test.reviews WHERE product_id = 1", Long.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'lab2_upgrade_test' AND table_name = 'orders'
                    """, String.class)).contains("customer_id")
                    .doesNotContain("customer_full_name", "customer_address", "customer_phone");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'lab2_upgrade_test' AND table_name = 'order_items'
                    """, String.class)).contains("product_id").doesNotContain("product_name", "product_price");
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS lab2_upgrade_test CASCADE");
        }
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(jdbcTemplate.getDataSource())
                .schemas("lab2_upgrade_test").defaultSchema("lab2_upgrade_test")
                .locations("classpath:db/migration").target(version).load().migrate();
    }

    private void seedLegacyData() {
        jdbcTemplate.execute("""
                INSERT INTO lab2_upgrade_test.products (name, price, description) VALUES
                ('Book', 10, 'original'), ('Book', 10, 'duplicate'), ('Book', 20, 'new price');
                INSERT INTO lab2_upgrade_test.orders
                    (customer_full_name, customer_address, customer_phone, status, created_at) VALUES
                ('Customer', 'Address', NULL, 'paid', '2020-01-02 03:04:05'),
                ('Customer', 'Address', NULL, 'new', '2021-02-03 04:05:06'),
                ('Customer', 'Other address', NULL, 'new', '2022-03-04 05:06:07'),
                ('Legacy', NULL, NULL, 'new', '2023-04-05 06:07:08');
                INSERT INTO lab2_upgrade_test.order_items (order_id, product_name, product_price, quantity) VALUES
                (1, 'Book', 10, 2), (2, 'Archived', 9, 3),
                (3, 'Archived', 9, 4), (4, 'Archived', 12, 1);
                INSERT INTO lab2_upgrade_test.reviews (product_id, author_name, rating) VALUES (1, 'Reader', 5);
                """);
    }

    private void assertPreservedData() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lab2_upgrade_test.customers", Long.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("""
                SELECT c.full_name || ':' || COALESCE(c.address, '<null>') || ':' || o.status
                FROM lab2_upgrade_test.orders o JOIN lab2_upgrade_test.customers c ON c.id = o.customer_id
                ORDER BY o.id
                """, String.class)).containsExactly(
                "Customer:Address:paid", "Customer:Address:new", "Customer:Other address:new", "Legacy:<null>:new");
        assertThat(jdbcTemplate.queryForList("""
                SELECT p.name || ':' || p.price::text || ':' || i.quantity
                FROM lab2_upgrade_test.order_items i JOIN lab2_upgrade_test.products p ON p.id = i.product_id
                ORDER BY i.id
                """, String.class)).containsExactly("Book:10.00:2", "Archived:9.00:3", "Archived:9.00:4", "Archived:12.00:1");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT product_id FROM lab2_upgrade_test.order_items WHERE id = 1", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(DISTINCT product_id) FROM lab2_upgrade_test.order_items WHERE id IN (2, 3)
                """, Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("""
                SELECT created_at::text FROM lab2_upgrade_test.orders ORDER BY id
                """, String.class)).isEqualTo(List.of(
                "2020-01-02 03:04:05", "2021-02-03 04:05:06", "2022-03-04 05:06:07", "2023-04-05 06:07:08"));
    }
}
