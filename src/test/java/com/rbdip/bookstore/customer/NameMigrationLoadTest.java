package com.rbdip.bookstore.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbdip.bookstore.BookstoreApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Старые SQL-клиенты при expand/backfill и настоящий HTTP API при contract. */
@Testcontainers
class NameMigrationLoadTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void oldReadersAndWritersSurviveExpandAndNamesStaySynchronized() throws Exception {
        String schema = "lab3_old_clients";
        PGSimpleDataSource dataSource = dataSource(schema);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            migrate(dataSource, schema, "2");
            List<String> names = List.of("Иван Петров", "Madonna", "Anna Maria Smith", " Иван", "Иван ", "Иван  Петров ");
            for (int i = 0; i < names.size(); i++) {
                jdbc.update("INSERT INTO customers (full_name, address) VALUES (?, ?)", names.get(i), "fixture-" + i);
            }
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger operations = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
            CountDownLatch firstWrite = new CountDownLatch(1);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<?> legacyClient = pool.submit(() -> {
                while (running.get()) {
                    try {
                        jdbc.queryForList("SELECT full_name FROM customers", String.class);
                        int number = operations.incrementAndGet();
                        jdbc.update("INSERT INTO customers (full_name, address) VALUES (?, ?)",
                                "Legacy " + number, "legacy-" + number);
                        firstWrite.countDown();
                    } catch (Throwable failure) {
                        errors.add(failure);
                        break;
                    }
                }
            });
            try {
                assertThat(firstWrite.await(10, TimeUnit.SECONDS)).isTrue();
                migrate(dataSource, schema, "4");
            } finally {
                running.set(false);
                pool.shutdown();
                assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                legacyClient.get(1, TimeUnit.SECONDS);
            }
            assertThat(errors).isEmpty();
            assertThat(operations.get()).isPositive();
            assertThat(jdbc.queryForList("""
                    SELECT full_name FROM customers WHERE address LIKE 'fixture-%' ORDER BY address
                    """, String.class)).isEqualTo(names);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM customers WHERE first_name IS NULL OR last_name IS NULL
                        OR full_name <> first_name || CASE WHEN last_name = '' THEN '' ELSE ' ' || last_name END
                    """, Long.class)).isZero();
            verifyDualWrites(jdbc);
            migrate(dataSource, schema, "5");
            assertThat(columns(jdbc, schema)).doesNotContain("full_name").contains("first_name", "last_name");
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO customers (first_name, last_name, address) VALUES ('New', 'Customer', 'dual-write')
                    """)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            dropSchema(schema);
        }
    }

    @Test
    void apiKeepsReadingAndCreatingOrdersWhileContractIsApplied() throws Exception {
        String schema = "lab3_api_load";
        PGSimpleDataSource dataSource = dataSource(schema);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            migrate(dataSource, schema, "2");
            jdbc.update("INSERT INTO customers (full_name, address) VALUES ('Legacy Customer', 'Address')");
            migrate(dataSource, schema, "4");
            jdbc.update("INSERT INTO products (name, price) VALUES ('Book', 25.00)");
            try (ConfigurableApplicationContext app = startApplication(schema)) {
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                URI orders = URI.create("http://localhost:" + port + "/orders");
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                    send(client, orderRequest(orders, "Seed Customer"), 201);
                    assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE first_name = 'Seed'",
                            String.class)).isEqualTo("Seed Customer");
                    runApiLoadDuringContract(client, orders, dataSource, schema);
                    String body = send(client, getRequest(orders), 200);
                    assertThat(body).contains("Seed Customer", "Load Customer", "Book");
                }
                assertThat(columns(jdbc, schema)).doesNotContain("full_name");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Long.class)).isGreaterThan(1);
            }
        } finally {
            dropSchema(schema);
        }
    }

    private void verifyDualWrites(JdbcTemplate jdbc) {
        jdbc.update("UPDATE customers SET full_name = 'Updated Legacy' WHERE address = 'fixture-0'");
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE address = 'fixture-0'"))
                .containsEntry("first_name", "Updated").containsEntry("last_name", "Legacy");
        jdbc.update("UPDATE customers SET first_name = 'New', last_name = 'Writer' WHERE address = 'fixture-1'");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE address = 'fixture-1'", String.class))
                .isEqualTo("New Writer");
        jdbc.update("INSERT INTO customers (first_name, last_name, address) VALUES ('New', 'Customer', 'dual-write')");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE address = 'dual-write'", String.class))
                .isEqualTo("New Customer");
    }

    private void runApiLoadDuringContract(HttpClient client, URI orders, PGSimpleDataSource dataSource,
            String schema) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean migrated = new AtomicBoolean();
        AtomicInteger requests = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        CountDownLatch before = new CountDownLatch(3);
        CountDownLatch after = new CountDownLatch(3);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> workers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            workers.add(pool.submit(() -> {
                boolean reportedBefore = false;
                boolean reportedAfter = false;
                while (running.get()) {
                    try {
                        send(client, getRequest(orders), 200);
                        send(client, orderRequest(orders, "Load Customer"), 201);
                        requests.addAndGet(2);
                        if (!reportedBefore) {
                            before.countDown();
                            reportedBefore = true;
                        }
                        if (migrated.get() && !reportedAfter) {
                            after.countDown();
                            reportedAfter = true;
                        }
                    } catch (Throwable failure) {
                        errors.add(failure);
                        break;
                    }
                }
            }));
        }
        try {
            assertThat(before.await(15, TimeUnit.SECONDS)).as("Все потоки реально обращаются к API").isTrue();
            migrate(dataSource, schema, "5");
            migrated.set(true);
            assertThat(after.await(15, TimeUnit.SECONDS)).as("Все потоки продолжают работу после contract").isTrue();
        } finally {
            running.set(false);
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            for (Future<?> worker : workers) {
                worker.get(1, TimeUnit.SECONDS);
            }
        }
        assertThat(errors).as("Ни HTTP-ошибок, ни таймаутов во время миграции").isEmpty();
        assertThat(requests.get()).isGreaterThanOrEqualTo(12);
    }

    private HttpRequest getRequest(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
    }

    private HttpRequest orderRequest(URI uri, String name) {
        String body = """
                {"customerFullName":"%s","customerAddress":"Address",
                 "items":[{"productId":1,"quantity":1}]}
                """.formatted(name);
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private String send(HttpClient client, HttpRequest request, int expectedStatus) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", request.method(), request.uri(), response.body())
                .isEqualTo(expectedStatus);
        return response.body();
    }

    private ConfigurableApplicationContext startApplication(String schema) {
        String url = postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        return new SpringApplicationBuilder(BookstoreApplication.class).run(
                "--server.port=0", "--spring.flyway.enabled=false", "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF");
    }

    private PGSimpleDataSource dataSource(String schema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setCurrentSchema(schema);
        return dataSource;
    }

    private void migrate(PGSimpleDataSource dataSource, String schema, String target) {
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(target).locations("classpath:db/migration").load().migrate();
    }

    private List<String> columns(JdbcTemplate jdbc, String schema) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = 'customers'
                """, String.class, schema);
    }

    private void dropSchema(String schema) {
        PGSimpleDataSource admin = new PGSimpleDataSource();
        admin.setUrl(postgres.getJdbcUrl());
        admin.setUser(postgres.getUsername());
        admin.setPassword(postgres.getPassword());
        new JdbcTemplate(admin).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
}
