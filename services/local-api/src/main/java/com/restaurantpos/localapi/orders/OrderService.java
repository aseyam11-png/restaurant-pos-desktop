package com.restaurantpos.localapi.orders;

import com.restaurantpos.localapi.auth.CurrentActor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.cash.CashShiftService.Direction;
import com.restaurantpos.localapi.cash.CashShiftService.MovementType;
import com.restaurantpos.localapi.catalog.CatalogDtos.PricingMode;
import com.restaurantpos.localapi.orders.OrderDtos.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final JdbcTemplate jdbc;
    private final CashShiftService cashShifts;
    private final String defaultCurrency;

    public OrderService(
        JdbcTemplate jdbc,
        CashShiftService cashShifts,
        @Value("${pos.currency:ILS}") String defaultCurrency
    ) {
        this.jdbc = jdbc;
        this.cashShifts = cashShifts;
        this.defaultCurrency = defaultCurrency.toUpperCase(Locale.ROOT);
    }

    @Transactional
    public OrderView createOrder(String idempotencyKey, CreateOrderRequest request) {
        String configuredCurrency = jdbc.query(
            "SELECT setting_value FROM store_settings WHERE setting_key = 'currency'",
            rs -> rs.next() ? rs.getString(1) : "ILS"
        );
        if (!configuredCurrency.equalsIgnoreCase(request.currency())) {
            throw PosException.badRequest(
                "STORE_CURRENCY_MISMATCH",
                "The transaction currency must match the configured store currency."
            );
        }

        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw PosException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A non-empty Idempotency-Key header of at most 128 characters is required.");
        }

        String requestHash = requestHash(request);
        int claimed = jdbc.update("""
            INSERT INTO order_idempotency(idempotency_key, request_hash)
            VALUES (?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, idempotencyKey, requestHash);

        if (claimed == 0) {
            IdempotencyRow existing = jdbc.queryForObject(
                "SELECT request_hash, order_id FROM order_idempotency WHERE idempotency_key = ?",
                (rs, row) -> new IdempotencyRow(
                    rs.getString("request_hash"),
                    rs.getObject("order_id", UUID.class)
                ),
                idempotencyKey
            );
            if (!requestHash.equals(existing.requestHash())) {
                throw PosException.conflict(
                    "IDEMPOTENCY_KEY_REUSED",
                    "This idempotency key was already used with a different request."
                );
            }
            if (existing.orderId() == null) {
                throw PosException.conflict("IDEMPOTENCY_IN_PROGRESS", "The original request has not completed.");
            }
            return getOrder(existing.orderId());
        }

        String currency = request.currency() == null ? defaultCurrency : request.currency().toUpperCase(Locale.ROOT);
        if (!currency.equals(defaultCurrency)) {
            throw PosException.badRequest("CURRENCY_NOT_CONFIGURED", "The requested currency does not match the configured store currency.");
        }

        List<PreparedLine> preparedLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (OrderLineRequest line : request.lines()) {
            ProductForSale product = findProductForSale(line.productId());
            BigDecimal quantity = OrderPricing.normalizeQuantity(line.quantity());
            BigDecimal unitPrice;
            if (product.pricingMode() == PricingMode.FIXED) {
                if (line.unitPrice() != null && line.unitPrice().compareTo(product.price()) != 0) {
                    throw PosException.badRequest("FIXED_PRICE_TAMPERED", "The submitted unit price does not match the current fixed product price.");
                }
                unitPrice = product.price();
            } else {
                unitPrice = OrderPricing.normalizeUnitPrice(line.unitPrice());
            }
            BigDecimal lineTotal = OrderPricing.lineTotal(unitPrice, quantity);
            subtotal = subtotal.add(lineTotal);
            preparedLines.add(new PreparedLine(product, quantity, unitPrice, lineTotal));
        }

        BigDecimal discount = OrderPricing.moneyOrZero(request.discountAmount());
        BigDecimal tax = OrderPricing.moneyOrZero(request.taxAmount());
        BigDecimal total = OrderPricing.orderTotal(subtotal, discount, tax);
        List<PaymentRequest> requestedPayments = request.payments() == null ? List.of() : request.payments();

        BigDecimal paymentTotal = BigDecimal.ZERO.setScale(2);
        for (PaymentRequest payment : requestedPayments) {
            BigDecimal amount = OrderPricing.moneyOrZero(payment.amount());
            if (amount.signum() <= 0) {
                throw PosException.badRequest("INVALID_PAYMENT_AMOUNT", "Payment amount must be greater than zero.");
            }
            if (payment.method() != PaymentMethod.WALLET && payment.senderPhone() != null && !payment.senderPhone().isBlank()) {
                throw PosException.badRequest("SENDER_PHONE_NOT_ALLOWED", "A sender phone number can only be recorded for a wallet transfer.");
            }
            paymentTotal = paymentTotal.add(amount);
        }
        if (paymentTotal.compareTo(total) > 0) {
            throw PosException.badRequest("PAYMENT_EXCEEDS_ORDER", "The payment total cannot exceed the order total.");
        }

        UUID cashShiftId = requestedPayments.stream().anyMatch(p -> p.method() == PaymentMethod.CASH)
            ? cashShifts.requireOpenShiftForUpdate()
            : null;
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO orders(
                id, customer_name, order_type, preparation_status, payment_status,
                subtotal, discount_amount, tax_amount, total, currency
            ) VALUES (?, ?, ?, 'NEW', 'UNPAID', ?, ?, ?, ?, ?)
            """,
            orderId,
            request.customerName().trim(),
            request.orderType().name(),
            subtotal,
            discount,
            tax,
            total,
            currency
        );

        for (PreparedLine line : preparedLines) {
            jdbc.update("""
                INSERT INTO order_lines(
                    id, order_id, product_id, product_no_snapshot, product_name_snapshot,
                    unit_snapshot, quantity, unit_price, line_total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                orderId,
                line.product().id(),
                line.product().productNumber(),
                line.product().name(),
                line.product().unit(),
                line.quantity(),
                line.unitPrice(),
                line.lineTotal()
            );
            if (line.product().stockTracked()) {
                int stockUpdated = jdbc.update("""
                    UPDATE inventory_balances
                    SET quantity_on_hand = quantity_on_hand - ?, updated_at = now()
                    WHERE product_id = ? AND quantity_on_hand >= ?
                    """, line.quantity(), line.product().id(), line.quantity());
                if (stockUpdated == 0) {
                    throw PosException.conflict("INSUFFICIENT_STOCK", "A stock-tracked product does not have sufficient available quantity.");
                }
                jdbc.update("""
                    INSERT INTO inventory_movements(
                        id, product_id, movement_type, quantity_delta, reference_type,
                        reference_id, reason, created_by
                    ) VALUES (?, ?, 'SALE', ?, 'ORDER', ?, 'Order accepted', 'LOCAL_OPERATOR')
                    """, UUID.randomUUID(), line.product().id(), line.quantity().negate(), orderId);
            }
        }

        for (PaymentRequest payment : requestedPayments) {
            IndividualPaymentStatus status = payment.method() == PaymentMethod.CASH
                ? IndividualPaymentStatus.CONFIRMED
                : IndividualPaymentStatus.PENDING_CONFIRMATION;
            String senderPhone = payment.method() == PaymentMethod.WALLET && payment.senderPhone() != null && !payment.senderPhone().isBlank()
                ? payment.senderPhone().trim()
                : null;
            UUID paymentId = UUID.randomUUID();
            UUID paymentShiftId = payment.method() == PaymentMethod.CASH ? cashShiftId : null;
            BigDecimal paymentAmount = OrderPricing.moneyOrZero(payment.amount());
            jdbc.update("""
                INSERT INTO payments(
                    id, order_id, method, status, amount, currency, sender_phone,
                    confirmed_at, confirmed_by, cash_shift_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                paymentId,
                orderId,
                payment.method().name(),
                status.name(),
                paymentAmount,
                currency,
                senderPhone,
                status == IndividualPaymentStatus.CONFIRMED ? OffsetDateTime.now() : null,
                status == IndividualPaymentStatus.CONFIRMED ? CurrentActor.name() : null,
                paymentShiftId
            );
            if (payment.method() == PaymentMethod.CASH) {
                cashShifts.recordMovement(
                    cashShiftId, MovementType.SALE, Direction.IN, paymentAmount,
                    "Cash receipt for order " + orderId, CurrentActor.name(), "PAYMENT", paymentId
                );
            }
        }

        PaymentStatus paymentStatus = resolvePaymentStatus(orderId, total);
        jdbc.update("UPDATE orders SET payment_status = ?, updated_at = now() WHERE id = ?", paymentStatus.name(), orderId);

        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, payload)
            VALUES (?, 'ORDER', ?, 'ORDER_CREATED', 'LOCAL_OPERATOR', '{}'::jsonb)
            """, UUID.randomUUID(), orderId);

        Long orderNumber = jdbc.queryForObject("SELECT order_no FROM orders WHERE id = ?", Long.class, orderId);
        String outboxPayload = """
            {"eventVersion":1,"orderId":"%s","orderNumber":%d}
            """.formatted(orderId, orderNumber);
        jdbc.update("""
            INSERT INTO outbox_events(id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (?, 'ORDER', ?, 'ORDER_CREATED', CAST(? AS jsonb))
            """, UUID.randomUUID(), orderId, outboxPayload);

        jdbc.update("""
            INSERT INTO print_jobs(id, order_id, document_type, status, is_reprint)
            VALUES (?, ?, 'CUSTOMER_ORDER', 'QUEUED', FALSE)
            """, UUID.randomUUID(), orderId);

        jdbc.update(
            "UPDATE order_idempotency SET order_id = ? WHERE idempotency_key = ?",
            orderId,
            idempotencyKey
        );

        return getOrder(orderId);
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(UUID orderId) {
        OrderHeader header = jdbc.query(
            """
            SELECT id, order_no, customer_name, order_type, preparation_status, payment_status,
                   subtotal, discount_amount, tax_amount, total, currency, created_at
            FROM orders WHERE id = ?
            """,
            rs -> rs.next() ? new OrderHeader(
                rs.getObject("id", UUID.class),
                rs.getLong("order_no"),
                rs.getString("customer_name"),
                OrderType.valueOf(rs.getString("order_type")),
                PreparationStatus.valueOf(rs.getString("preparation_status")),
                PaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total"),
                rs.getString("currency").trim(),
                rs.getObject("created_at", OffsetDateTime.class)
            ) : null,
            orderId
        );
        if (header == null) {
            throw PosException.notFound("Order was not found.");
        }

        List<OrderLineView> lines = jdbc.query(
            """
            SELECT id, product_id, product_no_snapshot, product_name_snapshot, unit_snapshot,
                   quantity, unit_price, line_total
            FROM order_lines WHERE order_id = ? ORDER BY id
            """,
            (rs, row) -> new OrderLineView(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getLong("product_no_snapshot"),
                rs.getString("product_name_snapshot"),
                rs.getString("unit_snapshot"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total")
            ),
            orderId
        );
        List<PaymentView> payments = jdbc.query(
            """
            SELECT id, method, status, amount, currency, created_at, confirmed_at
            FROM payments WHERE order_id = ? ORDER BY created_at, id
            """,
            (rs, row) -> new PaymentView(
                rs.getObject("id", UUID.class),
                PaymentMethod.valueOf(rs.getString("method")),
                IndividualPaymentStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("confirmed_at", OffsetDateTime.class)
            ),
            orderId
        );

        return new OrderView(
            header.id(), header.orderNumber(), header.customerName(), header.orderType(),
            header.preparationStatus(), header.paymentStatus(), header.subtotal(),
            header.discountAmount(), header.taxAmount(), header.total(), header.currency(),
            header.createdAt(), lines, payments
        );
    }

    @Transactional(readOnly = true)
    public List<OrderView> listOrders(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        List<UUID> ids = jdbc.query(
            "SELECT id FROM orders ORDER BY created_at DESC, order_no DESC LIMIT ?",
            (rs, row) -> rs.getObject(1, UUID.class),
            limit
        );
        return ids.stream().map(this::getOrder).toList();
    }

    @Transactional
    public OrderView cancelOrder(UUID orderId, String reason) {
        OrderHeaderForUpdate current = jdbc.query(
            "SELECT preparation_status, payment_status FROM orders WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? new OrderHeaderForUpdate(rs.getString("preparation_status"), rs.getString("payment_status")) : null,
            orderId
        );
        if (current == null) throw PosException.notFound("Order was not found.");
        if ("CANCELLED".equals(current.preparationStatus())) return getOrder(orderId);
        if ("DELIVERED".equals(current.preparationStatus())) {
            throw PosException.conflict("DELIVERED_ORDER_CANNOT_CANCEL", "A delivered order cannot be cancelled. Use an audited refund workflow.");
        }

        long confirmedPayments = jdbc.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'CONFIRMED'",
            Long.class,
            orderId
        );
        if (confirmedPayments > 0) {
            throw PosException.conflict(
                "REFUND_REQUIRED",
                "This order has confirmed payments. Complete an audited refund before cancellation is available."
            );
        }

        if ("NEW".equals(current.preparationStatus())) {
            List<StockReturn> stockReturns = jdbc.query("""
                SELECT product_id, SUM(-quantity_delta) AS quantity
                FROM inventory_movements
                WHERE reference_type = 'ORDER' AND reference_id = ? AND movement_type = 'SALE'
                GROUP BY product_id
                """,
                (rs, row) -> new StockReturn(
                    rs.getObject("product_id", UUID.class),
                    rs.getBigDecimal("quantity")
                ),
                orderId
            );
            for (StockReturn stockReturn : stockReturns) {
                int changed = jdbc.update(
                    "UPDATE inventory_balances SET quantity_on_hand = quantity_on_hand + ?, updated_at = now() WHERE product_id = ?",
                    stockReturn.quantity(), stockReturn.productId()
                );
                if (changed == 0) throw PosException.conflict("INVENTORY_BALANCE_MISSING", "Cannot release stock for the cancelled order.");
                jdbc.update("""
                    INSERT INTO inventory_movements(
                        id, product_id, movement_type, quantity_delta, reference_type,
                        reference_id, reason, created_by
                    ) VALUES (?, ?, 'CANCEL_RETURN', ?, 'ORDER', ?, ?, 'LOCAL_OPERATOR')
                    """, UUID.randomUUID(), stockReturn.productId(), stockReturn.quantity(), orderId, reason.trim());
            }
        }
        jdbc.update(
            "UPDATE payments SET status = 'VOIDED' WHERE order_id = ? AND status = 'PENDING_CONFIRMATION'",
            orderId
        );
        String nextPaymentStatus = "REFUNDED".equals(current.paymentStatus()) ? "REFUNDED" : "UNPAID";
        UUID diningTableId = jdbc.query(
            "SELECT dining_table_id FROM orders WHERE id = ?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            orderId
        );
        jdbc.update("""
            UPDATE orders
            SET preparation_status = 'CANCELLED', payment_status = ?, dining_table_id = NULL,
                cancelled_at = now(), cancellation_reason = ?, updated_at = now()
            WHERE id = ?
            """, nextPaymentStatus, reason.trim(), orderId);
        if (diningTableId != null) {
            jdbc.update("UPDATE dining_tables SET status = 'AVAILABLE', updated_at = now() WHERE id = ?", diningTableId);
        }
        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, reason, payload)
            VALUES (?, 'ORDER', ?, 'ORDER_CANCELLED', 'LOCAL_OPERATOR', ?, '{}'::jsonb)
            """, UUID.randomUUID(), orderId, reason.trim());
        return getOrder(orderId);
    }

    @Transactional
    public OrderView refundOrder(UUID orderId, String reason, String refundedBy) {
        RefundOrderHeader order = jdbc.query(
            "SELECT payment_status FROM orders WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? new RefundOrderHeader(rs.getString(1)) : null,
            orderId
        );
        if (order == null) throw PosException.notFound("Order was not found.");

        List<RefundablePayment> refundable = jdbc.query(
            "SELECT id, amount, currency, method FROM payments WHERE order_id = ? AND status = 'CONFIRMED' FOR UPDATE",
            (rs, row) -> new RefundablePayment(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency").trim(),
                rs.getString("method")
            ),
            orderId
        );
        if (refundable.isEmpty()) {
            Long previousRefunds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_refunds WHERE order_id = ?",
                Long.class,
                orderId
            );
            if (previousRefunds != null && previousRefunds > 0) return getOrder(orderId);
            throw PosException.conflict("NO_CONFIRMED_PAYMENT", "There are no confirmed payments to refund.");
        }

        UUID refundShiftId = refundable.stream().anyMatch(p -> "CASH".equals(p.method()))
            ? cashShifts.requireOpenShiftForUpdate()
            : null;
        for (RefundablePayment payment : refundable) {
            jdbc.update("""
                INSERT INTO payment_refunds(id, order_id, payment_id, amount, currency, reason, refunded_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), orderId, payment.id(), payment.amount(), payment.currency(),
                reason.trim(), refundedBy.trim()
            );
            if ("CASH".equals(payment.method())) {
                cashShifts.recordMovement(
                    refundShiftId, MovementType.REFUND, Direction.OUT, payment.amount(),
                    "Cash refund for order " + orderId, refundedBy.trim(), "PAYMENT_REFUND", payment.id()
                );
            }
            jdbc.update(
                "UPDATE payments SET status = 'REFUNDED' WHERE id = ? AND order_id = ? AND status = 'CONFIRMED'",
                payment.id(), orderId
            );
        }
        jdbc.update(
            "UPDATE payments SET status = 'VOIDED' WHERE order_id = ? AND status = 'PENDING_CONFIRMATION'",
            orderId
        );
        jdbc.update(
            "UPDATE orders SET payment_status = 'REFUNDED', updated_at = now() WHERE id = ?",
            orderId
        );
        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, reason, payload)
            VALUES (?, 'ORDER', ?, 'ORDER_REFUNDED', ?, ?, '{}'::jsonb)
            """, UUID.randomUUID(), orderId, refundedBy.trim(), reason.trim());
        String payload = "{\"eventVersion\":1,\"orderId\":\"" + orderId + "\"}";
        jdbc.update("""
            INSERT INTO outbox_events(id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (?, 'ORDER', ?, 'ORDER_REFUNDED', CAST(? AS jsonb))
            """, UUID.randomUUID(), orderId, payload);
        return getOrder(orderId);
    }

    @Transactional
    public OrderView changePreparationStatus(UUID orderId, PreparationStatus nextStatus) {
        String current = jdbc.query(
            "SELECT preparation_status FROM orders WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? rs.getString(1) : null,
            orderId
        );
        if (current == null) throw PosException.notFound("Order was not found.");
        if (nextStatus == PreparationStatus.CANCELLED) {
            throw PosException.badRequest("USE_CANCEL_ENDPOINT", "Use the audited cancellation endpoint to cancel an order.");
        }
        if (current.equals(nextStatus.name())) return getOrder(orderId);

        boolean allowed = switch (current) {
            case "NEW" -> nextStatus == PreparationStatus.IN_PREPARATION;
            case "IN_PREPARATION" -> nextStatus == PreparationStatus.READY;
            case "READY" -> nextStatus == PreparationStatus.DELIVERED;
            default -> false;
        };
        if (!allowed) {
            throw PosException.conflict("INVALID_PREPARATION_TRANSITION", "The requested preparation status transition is not allowed.");
        }
        if (nextStatus == PreparationStatus.DELIVERED) {
            UUID diningTableId = jdbc.query(
                "SELECT dining_table_id FROM orders WHERE id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                orderId
            );
            jdbc.update(
                "UPDATE orders SET preparation_status = ?, dining_table_id = NULL, updated_at = now() WHERE id = ?",
                nextStatus.name(), orderId
            );
            if (diningTableId != null) {
                jdbc.update("UPDATE dining_tables SET status = 'AVAILABLE', updated_at = now() WHERE id = ?", diningTableId);
            }
        } else {
            jdbc.update("UPDATE orders SET preparation_status = ?, updated_at = now() WHERE id = ?", nextStatus.name(), orderId);
        }
        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, payload)
            VALUES (?, 'ORDER', ?, 'PREPARATION_STATUS_CHANGED', 'LOCAL_OPERATOR', '{}'::jsonb)
            """, UUID.randomUUID(), orderId);
        return getOrder(orderId);
    }

    @Transactional
    public OrderView confirmPayment(UUID orderId, UUID paymentId, String confirmedBy) {
        PaymentStatusRow order = jdbc.query(
            "SELECT total, payment_status, preparation_status FROM orders WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? new PaymentStatusRow(
                rs.getBigDecimal("total"), rs.getString("payment_status"), rs.getString("preparation_status")
            ) : null,
            orderId
        );
        if (order == null) throw PosException.notFound("Order was not found.");
        if ("CANCELLED".equals(order.preparationStatus())) {
            throw PosException.conflict("CANCELLED_ORDER_PAYMENT", "A cancelled order cannot accept payment confirmation.");
        }

        String currentStatus = jdbc.query(
            "SELECT status FROM payments WHERE id = ? AND order_id = ? FOR UPDATE",
            rs -> rs.next() ? rs.getString(1) : null,
            paymentId,
            orderId
        );
        if (currentStatus == null) throw PosException.notFound("Payment was not found.");
        if ("CONFIRMED".equals(currentStatus)) return getOrder(orderId);
        if (!"PENDING_CONFIRMATION".equals(currentStatus)) {
            throw PosException.conflict("PAYMENT_NOT_CONFIRMABLE", "This payment is not awaiting confirmation.");
        }

        jdbc.update("""
            UPDATE payments SET status = 'CONFIRMED', confirmed_at = now(), confirmed_by = ?
            WHERE id = ? AND order_id = ?
            """, confirmedBy.trim(), paymentId, orderId);
        PaymentStatus next = resolvePaymentStatus(orderId, order.total());
        jdbc.update("UPDATE orders SET payment_status = ?, updated_at = now() WHERE id = ?", next.name(), orderId);
        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, payload)
            VALUES (?, 'ORDER', ?, 'PAYMENT_CONFIRMED', ?, '{}'::jsonb)
            """, UUID.randomUUID(), orderId, confirmedBy.trim());
        return getOrder(orderId);
    }

    @Transactional
    public PrintJobView enqueueReprint(UUID orderId) {
        Long orderNumber = jdbc.queryForObject("SELECT order_no FROM orders WHERE id = ?", Long.class, orderId);
        if (orderNumber == null) throw PosException.notFound("Order was not found.");
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO print_jobs(id, order_id, document_type, status, is_reprint)
            VALUES (?, ?, 'CUSTOMER_ORDER', 'QUEUED', TRUE)
            """, jobId, orderId);
        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, payload)
            VALUES (?, 'ORDER', ?, 'CUSTOMER_ORDER_REPRINT_QUEUED', 'LOCAL_OPERATOR', '{}'::jsonb)
            """, UUID.randomUUID(), orderId);
        return new PrintJobView(jobId, orderId, orderNumber, "CUSTOMER_ORDER", "QUEUED", true, 0, OffsetDateTime.now(), null);
    }

    @Transactional(readOnly = true)
    public List<PrintJobView> listPrintJobs(String status, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        if (status == null || status.isBlank()) {
            return jdbc.query("""
                SELECT j.id, j.order_id, o.order_no, j.document_type, j.status,
                       j.is_reprint, j.attempts, j.created_at, j.last_error
                FROM print_jobs j JOIN orders o ON o.id = j.order_id
                ORDER BY j.created_at DESC LIMIT ?
                """,
                (rs, row) -> printJobFrom(rs),
                limit
            );
        }
        return jdbc.query("""
            SELECT j.id, j.order_id, o.order_no, j.document_type, j.status,
                   j.is_reprint, j.attempts, j.created_at, j.last_error
            FROM print_jobs j JOIN orders o ON o.id = j.order_id
            WHERE j.status = ?
            ORDER BY j.created_at LIMIT ?
            """,
            (rs, row) -> printJobFrom(rs),
            status.toUpperCase(Locale.ROOT),
            limit
        );
    }

    @Transactional(readOnly = true)
    public com.restaurantpos.localapi.printing.CustomerDocumentDtos.CustomerOrderDocument customerDocument(UUID orderId) {
        var header = jdbc.query(
            """
            SELECT id, order_no, customer_name, order_type, created_at,
                   subtotal, discount_amount, tax_amount, total, currency
            FROM orders WHERE id = ?
            """,
            rs -> rs.next() ? new CustomerDocumentHeader(
                rs.getObject("id", UUID.class),
                rs.getLong("order_no"),
                rs.getString("customer_name"),
                rs.getString("order_type"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total"),
                rs.getString("currency").trim()
            ) : null,
            orderId
        );
        if (header == null) throw PosException.notFound("Order was not found.");

        List<com.restaurantpos.localapi.printing.CustomerDocumentDtos.CustomerOrderLine> lines = jdbc.query(
            """
            SELECT product_name_snapshot, unit_snapshot, quantity, unit_price, line_total
            FROM order_lines WHERE order_id = ? ORDER BY id
            """,
            (rs, row) -> new com.restaurantpos.localapi.printing.CustomerDocumentDtos.CustomerOrderLine(
                rs.getString("product_name_snapshot"),
                rs.getString("unit_snapshot"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total")
            ),
            orderId
        );
        return new com.restaurantpos.localapi.printing.CustomerDocumentDtos.CustomerOrderDocument(
            header.id(), header.orderNumber(), header.customerName(), header.orderType(),
            header.createdAt(), header.subtotal(), header.discountAmount(), header.taxAmount(),
            header.total(), header.currency(), lines
        );
    }

    private ProductForSale findProductForSale(UUID productId) {
        ProductForSale product = jdbc.query(
            """
            SELECT id, product_no, name, unit, pricing_mode, price, stock_tracked
            FROM products WHERE id = ? AND active = TRUE
            FOR UPDATE
            """,
            rs -> rs.next() ? new ProductForSale(
                rs.getObject("id", UUID.class),
                rs.getLong("product_no"),
                rs.getString("name"),
                rs.getString("unit"),
                PricingMode.valueOf(rs.getString("pricing_mode")),
                rs.getBigDecimal("price"),
                rs.getBoolean("stock_tracked")
            ) : null,
            productId
        );
        if (product == null) throw PosException.notFound("An active product in this order was not found.");
        return product;
    }

    private PaymentStatus resolvePaymentStatus(UUID orderId, BigDecimal total) {
        PaymentTotals totals = jdbc.query(
            """
            SELECT
              COALESCE(SUM(amount) FILTER (WHERE status = 'CONFIRMED'), 0) AS confirmed_amount,
              COALESCE(SUM(amount) FILTER (WHERE status = 'PENDING_CONFIRMATION'), 0) AS pending_amount
            FROM payments WHERE order_id = ?
            """,
            rs -> rs.next() ? new PaymentTotals(
                rs.getBigDecimal("confirmed_amount"),
                rs.getBigDecimal("pending_amount")
            ) : new PaymentTotals(BigDecimal.ZERO, BigDecimal.ZERO),
            orderId
        );
        if (totals.confirmed().compareTo(total) >= 0) return PaymentStatus.PAID;
        if (totals.pending().signum() > 0) return PaymentStatus.PENDING_CONFIRMATION;
        if (totals.confirmed().signum() > 0) return PaymentStatus.PARTIALLY_PAID;
        return PaymentStatus.UNPAID;
    }

    private static String requestHash(CreateOrderRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static PrintJobView printJobFrom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PrintJobView(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getLong("order_no"),
            rs.getString("document_type"),
            rs.getString("status"),
            rs.getBoolean("is_reprint"),
            rs.getInt("attempts"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getString("last_error")
        );
    }

    private record CustomerDocumentHeader(
        UUID id, long orderNumber, String customerName, String orderType,
        OffsetDateTime createdAt, BigDecimal subtotal, BigDecimal discountAmount,
        BigDecimal taxAmount, BigDecimal total, String currency
    ) {}
    private record IdempotencyRow(String requestHash, UUID orderId) {}
    private record ProductForSale(UUID id, long productNumber, String name, String unit, PricingMode pricingMode, BigDecimal price, boolean stockTracked) {}
    private record StockReturn(UUID productId, BigDecimal quantity) {}
    private record PreparedLine(ProductForSale product, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    private record OrderHeader(
        UUID id, long orderNumber, String customerName, OrderType orderType,
        PreparationStatus preparationStatus, PaymentStatus paymentStatus,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
        BigDecimal total, String currency, OffsetDateTime createdAt
    ) {}
    private record OrderHeaderForUpdate(String preparationStatus, String paymentStatus) {}
    private record RefundOrderHeader(String paymentStatus) {}
    private record RefundablePayment(UUID id, BigDecimal amount, String currency, String method) {}
    private record PaymentStatusRow(BigDecimal total, String status, String preparationStatus) {}
    private record PaymentTotals(BigDecimal confirmed, BigDecimal pending) {}
}