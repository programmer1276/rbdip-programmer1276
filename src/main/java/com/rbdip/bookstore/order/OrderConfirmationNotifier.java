package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Эмулирует отправку письма: почтовый транспорт в учебном проекте не настроен. */
@Component
public class OrderConfirmationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderConfirmationNotifier.class);

    public void sendConfirmation(String customerName, Long orderId, BigDecimal total) {
        LOGGER.info("[email] Dear {}, your order #{} for {} has been placed.", customerName, orderId, total);
    }
}
