package com.rbdip.bookstore.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.contracts.PurchaseHistory;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseHistory history;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void checksExactProductAndCustomerWithOneQuery() {
        Long purchased = product("Purchased book");
        Long other = product("Other book");
        Map<String, Object> order = Map.of("customerFullName", "Anna Maria Smith", "customerAddress", "Address",
                "items", List.of(Map.of("productId", purchased, "quantity", 1)));
        assertThat(restTemplate.postForEntity("/orders", order, Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        assertThat(history.hasPurchased(purchased, "Anna Maria Smith")).isTrue();
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(history.hasPurchased(other, "Anna Maria Smith")).isFalse();
        assertThat(history.hasPurchased(purchased, "Other Reader")).isFalse();
        stats.clear();
        assertThat(history.hasPurchased(purchased, null)).isFalse();
        assertThat(history.hasPurchased(purchased, " ")).isFalse();
        assertThat(history.hasPurchased(null, "Anna Maria Smith")).isFalse();
        assertThat(stats.getPrepareStatementCount()).isZero();
    }

    @Test
    void reviewEndpointsKeepTheirResponseAndAcceptAnonymousReview() {
        Long product = product("Book");
        String uri = "/products/" + product + "/reviews";
        ResponseEntity<Map> created = restTemplate.postForEntity(uri, Map.of("rating", 4), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsKey("id");
        ResponseEntity<List> listed = restTemplate.getForEntity(uri, List.class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).containsExactly(Map.of("authorName", "anonymous", "rating", 4, "comment", ""));
    }

    private Long product(String name) {
        ResponseEntity<Map> response = restTemplate.postForEntity("/products", Map.of("name", name, "price", "25"),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }
}
