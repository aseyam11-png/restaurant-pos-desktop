package com.restaurantpos.localapi.purchases;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.cash.CashShiftService.Direction;
import com.restaurantpos.localapi.cash.CashShiftService.MovementType;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import com.restaurantpos.localapi.purchases.SupplierPaymentDtos.RecordPaymentRequest;
import com.restaurantpos.localapi.purchases.SupplierPaymentDtos.SupplierPaymentView;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierPaymentService {
    private final JdbcTemplate jdbc;
    private final CashShiftService cash;
    private final String currency;

    public SupplierPaymentService(JdbcTemplate jdbc, CashShiftService cash, @Value("${pos.currency:ILS}") String currency) {
        this.jdbc = jdbc;
        this.cash = cash;
        this.currency = currency.toUpperCase(Locale.ROOT);
    }

    @Transactional
    public SupplierPaymentView record(UUID purchaseId, String key, RecordPaymentRequest request) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw PosException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A non-empty Idempotency-Key is required.");
        }
        String hash = hash(request);
        int claimed = jdbc.update("""
            INSERT INTO supplier_payment_idempotency(idempotency_key, request_hash)
            VALUES (?, ?) ON CONFLICT DO NOTHING
            """, key, hash);
        if (claimed == 0) {
            ExistingPayment existing = jdbc.queryForObject(
                "SELECT request_hash, payment_id FROM supplier_payment_idempotency WHERE idempotency_key = ?",
                (rs, row) -> new ExistingPayment(rs.getString(1), rs.getObject(2, UUID.class)),
                key
            );
            if (!hash.equals(existing.hash())) {
                throw PosException.conflict("IDEMPOTENCY_KEY_REUSED", "This key was already used with a different supplier payment.");
            }
            if (existing.paymentId() == null) {
                throw PosException.conflict("IDEMPOTENCY_IN_PROGRESS", "The original payment is still processing.");
            }
            return get(existing.paymentId());
        }

        PurchaseRow purchase = jdbc.query("""
            SELECT status, total, paid_amount, due_amount, currency
            FROM purchases WHERE id = ? FOR UPDATE
            """,
            rs -> rs.next() ? new PurchaseRow(
                rs.getString("status"), rs.getBigDecimal("total"), rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("due_amount"), rs.getString("currency").trim()
            ) : null,
            purchaseId
        );
        if (purchase == null) throw PosException.notFound("Purchase was not found.");
        if (!"POSTED".equals(purchase.status())) {
            throw PosException.conflict("PURCHASE_NOT_PAYABLE", "A voided purchase cannot receive payments.");
        }

        BigDecimal amount = normalizeAmount(request.amount());
        if (amount.compareTo(purchase.due()) > 0) {
            throw PosException.badRequest("PAYMENT_EXCEEDS_PURCHASE_DUE", "Payment cannot exceed the outstanding balance.");
        }

        UUID shiftId = request.method() == PaymentMethod.CASH ? cash.requireOpenShiftForUpdate() : null;
        UUID paymentId = UUID.randomUUID();
        OffsetDateTime paidAt = OffsetDateTime.now();
        jdbc.update("""
            INSERT INTO supplier_payments(id, purchase_id, method, amount, currency, cash_shift_id, paid_at, recorded_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            paymentId, purchaseId, request.method().name(), amount, purchase.currency(),
            shiftId, paidAt, request.recordedBy().trim()
        );

        BigDecimal paidTotal = purchase.paid().add(amount).setScale(2);
        BigDecimal dueTotal = purchase.total().subtract(paidTotal).setScale(2);
        String paymentStatus = dueTotal.signum() == 0 ? "PAID" : "PARTIALLY_PAID";
        jdbc.update("""
            UPDATE purchases
            SET paid_amount = ?, due_amount = ?, payment_status = ?
            WHERE id = ?
            """, paidTotal, dueTotal, paymentStatus, purchaseId);

        if (request.method() == PaymentMethod.CASH) {
            cash.recordMovement(
                shiftId, MovementType.PURCHASE, Direction.OUT, amount,
                "Supplier payment for purchase " + purchaseId,
                request.recordedBy().trim(), "SUPPLIER_PAYMENT", paymentId
            );
        }

        jdbc.update("""
            INSERT INTO audit_events(id, aggregate_type, aggregate_id, event_type, actor, payload)
            VALUES (?, 'PURCHASE', ?, 'SUPPLIER_PAYMENT_RECORDED', ?, '{}'::jsonb)
            """, UUID.randomUUID(), purchaseId, request.recordedBy().trim());
        String payload = """
            {"eventVersion":1,"purchaseId":"%s","supplierPaymentId":"%s"}
            """.formatted(purchaseId, paymentId);
        jdbc.update("""
            INSERT INTO outbox_events(id, aggregate_type, aggregate_id, event_type, payload)
            VALUES (?, 'PURCHASE', ?, 'SUPPLIER_PAYMENT_RECORDED', CAST(? AS jsonb))
            """, UUID.randomUUID(), purchaseId, payload);
        jdbc.update("UPDATE supplier_payment_idempotency SET payment_id = ? WHERE idempotency_key = ?", paymentId, key);
        return get(paymentId);
    }

    @Transactional(readOnly = true)
    public java.util.List<SupplierPaymentView> list(UUID purchaseId) {
        Boolean exists = jdbc.query(
            "SELECT TRUE FROM purchases WHERE id = ?",
            rs -> rs.next() ? rs.getBoolean(1) : null,
            purchaseId
        );
        if (!Boolean.TRUE.equals(exists)) throw PosException.notFound("Purchase was not found.");
        return jdbc.query("""
            SELECT sp.id, sp.purchase_id, sp.method, sp.amount, sp.currency, sp.cash_shift_id,
                   sp.paid_at, sp.recorded_by, p.paid_amount, p.due_amount, p.payment_status
            FROM supplier_payments sp JOIN purchases p ON p.id = sp.purchase_id
            WHERE sp.purchase_id = ?
            ORDER BY sp.paid_at, sp.id
            """,
            (rs, row) -> new SupplierPaymentView(
                rs.getObject("id", UUID.class), rs.getObject("purchase_id", UUID.class),
                PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), rs.getObject("cash_shift_id", UUID.class),
                rs.getObject("paid_at", OffsetDateTime.class), rs.getString("recorded_by"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("due_amount"),
                rs.getString("payment_status")
            ),
            purchaseId
        );
    }

    @Transactional(readOnly = true)
    public SupplierPaymentView get(UUID paymentId) {
        return jdbc.query("""
            SELECT sp.id, sp.purchase_id, sp.method, sp.amount, sp.currency, sp.cash_shift_id,
                   sp.paid_at, sp.recorded_by, p.paid_amount, p.due_amount, p.payment_status
            FROM supplier_payments sp JOIN purchases p ON p.id = sp.purchase_id
            WHERE sp.id = ?
            """,
            rs -> rs.next() ? new SupplierPaymentView(
                rs.getObject("id", UUID.class), rs.getObject("purchase_id", UUID.class),
                PaymentMethod.valueOf(rs.getString("method")), rs.getBigDecimal("amount"),
                rs.getString("currency").trim(), rs.getObject("cash_shift_id", UUID.class),
                rs.getObject("paid_at", OffsetDateTime.class), rs.getString("recorded_by"),
                rs.getBigDecimal("paid_amount"), rs.getBigDecimal("due_amount"),
                rs.getString("payment_status")
            ) : null,
            paymentId
        );
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 14) {
            throw PosException.badRequest("INVALID_MONEY_AMOUNT", "Payment must be positive with at most two decimal places.");
        }
        return amount.setScale(2);
    }

    private static String hash(RecordPaymentRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(request.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record ExistingPayment(String hash, UUID paymentId) {}
    private record PurchaseRow(String status, BigDecimal total, BigDecimal paid, BigDecimal due, String currency) {}
}
