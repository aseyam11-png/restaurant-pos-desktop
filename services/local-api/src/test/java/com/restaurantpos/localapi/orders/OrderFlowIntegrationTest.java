package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateCategoryRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateProductRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.PricingMode;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.cash.CashShiftDtos.OpenShiftRequest;
import com.restaurantpos.localapi.inventory.InventoryDtos.*;
import com.restaurantpos.localapi.inventory.InventoryService;
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
    @Autowired private CashShiftService cashShifts;
    @Autowired private InventoryService inventory;
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
        assertEquals(4L, events);
    }

    @Test
    void refundIsAuditedIdempotentAndCanBeFollowedByCancellation() {
        cashShifts.open(new OpenShiftRequest(BigDecimal.ZERO, "Owner"));
        var category = catalog.createCategory(new CreateCategoryRequest("Refund-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Refund product", "piece", PricingMode.FIXED,
            new BigDecimal("12.50"), false
        ));
        OrderView order = orders.createOrder("refund-" + UUID.randomUUID(), new CreateOrderRequest(
            "Refund customer", OrderType.TAKEAWAY,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("2"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS",
            List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("25.00"), null))
        ));
        assertEquals(PaymentStatus.PAID, order.paymentStatus());

        OrderView refunded = orders.refundOrder(order.id(), "Customer returned order", "Owner");
        assertEquals(PaymentStatus.REFUNDED, refunded.paymentStatus());
        assertEquals(IndividualPaymentStatus.REFUNDED, refunded.payments().get(0).status());

        OrderView repeatedRefund = orders.refundOrder(order.id(), "Duplicate request", "Owner");
        assertEquals(PaymentStatus.REFUNDED, repeatedRefund.paymentStatus());
        Long refundCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM payment_refunds WHERE order_id = ?", Long.class, order.id()
        );
        assertEquals(1L, refundCount);

        OrderView cancelled = orders.cancelOrder(order.id(), "Order closed after refund");
        assertEquals(PreparationStatus.CANCELLED, cancelled.preparationStatus());
        assertEquals(PaymentStatus.REFUNDED, cancelled.paymentStatus());
        assertEquals(IndividualPaymentStatus.REFUNDED, cancelled.payments().get(0).status());
    }

    @Test
    void cancellingPendingWalletPaymentVoidsPaymentWithoutLeakingSenderPhone() {
        var category = catalog.createCategory(new CreateCategoryRequest("Void-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Void product", "piece", PricingMode.FIXED,
            new BigDecimal("4.00"), false
        ));
        OrderView order = orders.createOrder("void-" + UUID.randomUUID(), new CreateOrderRequest(
            "Void customer", OrderType.DELIVERY,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("1"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS",
            List.of(new PaymentRequest(PaymentMethod.WALLET, new BigDecimal("4.00"), "0597654321"))
        ));

        OrderView cancelled = orders.cancelOrder(order.id(), "Wallet transfer not received");
        assertEquals(PreparationStatus.CANCELLED, cancelled.preparationStatus());
        assertEquals(IndividualPaymentStatus.VOIDED, cancelled.payments().get(0).status());
        assertFalse(cancelled.toString().contains("0597654321"));
    }
    @Test
    void cashSaleRefundAndPrePreparationCancellationReconcileCashAndInventory() {
        cashShifts.open(new OpenShiftRequest(new BigDecimal("100.00"), "Owner"));
        var category = catalog.createCategory(new CreateCategoryRequest("StockSale-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Tracked product", "piece", PricingMode.FIXED,
            new BigDecimal("5.00"), true
        ));
        inventory.recordMovement(new RecordMovementRequest(
            product.id(), MovementType.RECEIPT, new BigDecimal("5.000"), "Opening stock", "Owner"
        ));

        OrderView order = orders.createOrder("stock-sale-" + UUID.randomUUID(), new CreateOrderRequest(
            "Stock customer", OrderType.TAKEAWAY,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("2.000"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS",
            List.of(new PaymentRequest(PaymentMethod.CASH, new BigDecimal("10.00"), null))
        ));

        assertEquals(PaymentStatus.PAID, order.paymentStatus());
        assertEquals(new BigDecimal("110.00"), cashShifts.active().expectedCash());
        assertEquals(new BigDecimal("3.000"), inventory.listBalances("Tracked product", false, 10).get(0).quantityOnHand());

        OrderView refunded = orders.refundOrder(order.id(), "Customer return", "Owner");
        assertEquals(PaymentStatus.REFUNDED, refunded.paymentStatus());
        assertEquals(new BigDecimal("100.00"), cashShifts.active().expectedCash());

        OrderView cancelled = orders.cancelOrder(order.id(), "Returned before preparation");
        assertEquals(PreparationStatus.CANCELLED, cancelled.preparationStatus());
        assertEquals(PaymentStatus.REFUNDED, cancelled.paymentStatus());
        assertEquals(new BigDecimal("5.000"), inventory.listBalances("Tracked product", false, 10).get(0).quantityOnHand());
    }

}
