package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateCategoryRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateProductRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.PricingMode;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.orders.OrderDtos.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class OrderFlowIntegrationTest {
    @Autowired private CatalogService catalog;
    @Autowired private OrderService orders;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void walletOrderIsIdempotentAndSenderPhoneIsNotInOrderView() {
        var category = catalog.createCategory(new CreateCategoryRequest("Test-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Integration product", "piece", PricingMode.FIXED,
            new BigDecimal("10.00"), false
        ));
        var request = new CreateOrderRequest(
            "Integration customer", OrderType.TAKEAWAY,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("1"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS",
            List.of(new PaymentRequest(PaymentMethod.WALLET, new BigDecimal("10.00"), "0591234567"))
        );

        OrderView first = orders.createOrder("integration-" + UUID.randomUUID(), request);
        assertEquals(new BigDecimal("10.00"), first.total());
        assertEquals(PaymentStatus.PENDING_CONFIRMATION, first.paymentStatus());
        assertEquals(1, first.lines().size());
        assertEquals(1, first.payments().size());

        String storedSender = jdbc.queryForObject(
            "SELECT sender_phone FROM payments WHERE order_id = ?", String.class, first.id()
        );
        assertEquals("0591234567", storedSender);
        assertFalse(first.toString().contains(storedSender));

        String idempotencyKey = "replay-" + UUID.randomUUID();
        OrderView original = orders.createOrder(idempotencyKey, request);
        OrderView replay = orders.createOrder(idempotencyKey, request);
        assertEquals(original.id(), replay.id());

        CreateOrderRequest changedRequest = new CreateOrderRequest(
            "Different customer", request.orderType(), request.lines(),
            request.discountAmount(), request.taxAmount(), request.currency(), request.payments()
        );
        assertThrows(PosException.class, () -> orders.createOrder(idempotencyKey, changedRequest));

        OrderView paid = orders.confirmPayment(first.id(), first.payments().get(0).id(), "Integration owner");
        assertEquals(PaymentStatus.PAID, paid.paymentStatus());
        assertThrows(PosException.class, () -> orders.cancelOrder(first.id(), "Test cancellation"));
    }

    @Test
    void preparationTransitionsAreOrderedAndAudited() {
        var category = catalog.createCategory(new CreateCategoryRequest("Flow-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Flow product", "piece", PricingMode.FIXED,
            new BigDecimal("3.50"), false
        ));
        OrderView order = orders.createOrder("flow-" + UUID.randomUUID(), new CreateOrderRequest(
            "Flow customer", OrderType.DINE_IN,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("2"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));

        assertEquals(PreparationStatus.IN_PREPARATION,
            orders.changePreparationStatus(order.id(), PreparationStatus.IN_PREPARATION).preparationStatus());
        assertEquals(PreparationStatus.READY,
            orders.changePreparationStatus(order.id(), PreparationStatus.READY).preparationStatus());
        assertEquals(PreparationStatus.DELIVERED,
            orders.changePreparationStatus(order.id(), PreparationStatus.DELIVERED).preparationStatus());

        assertThrows(PosException.class, () ->
            orders.changePreparationStatus(order.id(), PreparationStatus.IN_PREPARATION)
        );
        assertThrows(PosException.class, () -> orders.cancelOrder(order.id(), "Too late"));
        Long events = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE aggregate_id = ?", Long.class, order.id()
        );
        assertEquals(3L, events);
    }
}