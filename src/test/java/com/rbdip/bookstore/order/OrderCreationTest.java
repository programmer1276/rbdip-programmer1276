package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Проверяет поведение создания заказа после разделения обязанностей. */
class OrderCreationTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderConfirmationNotifier notifier = mock(OrderConfirmationNotifier.class);
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new OrderRequestValidator(), new OrderItemResolver(products),
                new OrderWriter(orders, items), new PricingCalculator(), notifier);
    }

    @Test
    void savesMatchingProductsAndQuantitiesAndNotifiesWithDiscountedTotal() {
        when(products.findById(1L)).thenReturn(Optional.of(new Product("First", new BigDecimal("10"), null)));
        when(products.findById(2L)).thenReturn(Optional.of(new Product("Second", new BigDecimal("30"), null)));
        Order saved = mock(Order.class);
        when(saved.getId()).thenReturn(42L);
        when(orders.save(any(Order.class))).thenReturn(saved);
        CreateOrderRequest request = request("Ivan", "Moscow", "vip", "SAVE10",
                List.of(new CreateOrderRequest.Item(1L, null), new CreateOrderRequest.Item(2L, 2)));

        assertThat(service.createOrder(request)).isSameAs(saved);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getCustomerFullName()).isEqualTo("Ivan");
        assertThat(orderCaptor.getValue().getCustomerAddress()).isEqualTo("Moscow");
        assertThat(orderCaptor.getValue().getCustomerPhone()).isEqualTo("+79990000000");
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("new");
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        verify(items, org.mockito.Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(OrderItem::getProductName).containsExactly("First", "Second");
        assertThat(itemCaptor.getAllValues()).extracting(OrderItem::getQuantity).containsExactly(1, 2);
        assertThat(itemCaptor.getAllValues()).extracting(OrderItem::getProductPrice)
                .containsExactly(new BigDecimal("10"), new BigDecimal("30"));
        assertThat(itemCaptor.getAllValues()).extracting(OrderItem::getOrderId).containsOnly(42L);
        verify(notifier).sendConfirmation("Ivan", 42L, new BigDecimal("53.00"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingNameBeforeDatabaseAccess(String name) {
        assertThatThrownBy(() -> service.createOrder(request(name, "Moscow", null, null,
                List.of(new CreateOrderRequest.Item(1L, 1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("customerFullName is required");
        verifyNoInteractions(products, orders, items, notifier);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingAddressBeforeDatabaseAccess(String address) {
        assertThatThrownBy(() -> service.createOrder(request("Ivan", address, null, null,
                List.of(new CreateOrderRequest.Item(1L, 1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("customerAddress is required");
        verifyNoInteractions(products, orders, items, notifier);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingItems(List<CreateOrderRequest.Item> rawItems) {
        assertThatThrownBy(() -> service.createOrder(request("Ivan", "Moscow", null, null, rawItems)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("order must contain at least one item");
        verifyNoInteractions(products, orders, items, notifier);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantityBeforeSaving(int quantity) {
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        assertThatThrownBy(() -> service.createOrder(request("Ivan", "Moscow", null, null,
                List.of(new CreateOrderRequest.Item(1L, quantity)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("quantity must be positive");
        verifyNoInteractions(orders, items, notifier);
    }

    @Test
    void resolvesAllProductsBeforeSavingAnything() {
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        when(products.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createOrder(request("Ivan", "Moscow", null, null,
                List.of(new CreateOrderRequest.Item(1L, 1), new CreateOrderRequest.Item(2L, 1)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("product 2 not found");
        verifyNoInteractions(orders, items, notifier);
    }

    @Test
    void doesNotNotifyWhenSavingItemsFails() {
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any(OrderItem.class))).thenThrow(new IllegalStateException("storage unavailable"));
        assertThatThrownBy(() -> service.createOrder(request("Ivan", "Moscow", null, null,
                List.of(new CreateOrderRequest.Item(1L, 1)))))
                .isInstanceOf(IllegalStateException.class).hasMessage("storage unavailable");
        verifyNoInteractions(notifier);
    }

    @Test
    void defaultsMissingCustomerTypeToRegular() {
        PricingCalculator calculator = mock(PricingCalculator.class);
        service = new OrderService(new OrderRequestValidator(), new OrderItemResolver(products),
                new OrderWriter(orders, items), calculator, notifier);
        when(products.findById(1L)).thenReturn(Optional.of(new Product("Book", BigDecimal.TEN, null)));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.createOrder(request("Ivan", "Moscow", null, null, List.of(new CreateOrderRequest.Item(1L, 1))));
        verify(calculator).calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(BigDecimal.TEN, 1)), "regular", null);
    }

    private CreateOrderRequest request(String name, String address, String customerType, String coupon,
            List<CreateOrderRequest.Item> rawItems) {
        return new CreateOrderRequest(name, address, "+79990000000", customerType, coupon, rawItems);
    }
}
