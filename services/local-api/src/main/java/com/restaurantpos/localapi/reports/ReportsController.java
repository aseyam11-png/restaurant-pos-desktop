package com.restaurantpos.localapi.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
    private final JdbcTemplate jdbc;

    public ReportsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/sales/summary")
    public SalesSummary salesSummary(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC) : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start) || end.isAfter(start.plusDays(366))) {
            throw new IllegalArgumentException("Invalid reporting date range.");
        }
        OffsetDateTime startAt = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endAt = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        SummaryRow totals = jdbc.query(
            """
            SELECT
              COUNT(*) FILTER (WHERE preparation_status <> 'CANCELLED') AS completed_count,
              COUNT(*) FILTER (WHERE preparation_status = 'CANCELLED') AS cancelled_count,
              COALESCE(SUM(total) FILTER (WHERE preparation_status <> 'CANCELLED'), 0) AS order_value
            FROM orders
            WHERE created_at >= ? AND created_at < ?
            """,
            rs -> rs.next() ? new SummaryRow(
                rs.getLong("completed_count"),
                rs.getLong("cancelled_count"),
                rs.getBigDecimal("order_value")
            ) : new SummaryRow(0, 0, BigDecimal.ZERO),
            startAt,
            endAt
        );

        PaymentTotals payments = jdbc.query(
            """
            SELECT
              COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'CONFIRMED'), 0) AS collected,
              COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'PENDING_CONFIRMATION'), 0) AS pending,
              COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'REFUNDED'), 0) AS refunded
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            WHERE o.created_at >= ? AND o.created_at < ?
              AND o.preparation_status <> 'CANCELLED'
            """,
            rs -> rs.next() ? new PaymentTotals(
                rs.getBigDecimal("collected"),
                rs.getBigDecimal("pending"),
                rs.getBigDecimal("refunded")
            ) : new PaymentTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
            startAt,
            endAt
        );

        return new SalesSummary(
            start,
            end,
            totals.orderCount(),
            totals.cancelledCount(),
            totals.orderValue(),
            payments.collected(),
            payments.pending(),
            payments.refunded(),
            totals.orderValue().subtract(payments.refunded()),
            "UTC"
        );
    }

    @GetMapping("/sales/products")
    public List<ProductSalesRow> productSales(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "50") int limit
    ) {
        LocalDate start = from == null ? LocalDate.now(ZoneOffset.UTC) : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start) || end.isAfter(start.plusDays(366))) {
            throw new IllegalArgumentException("Invalid reporting date range.");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query(
            """
            SELECT l.product_id, l.product_no_snapshot, l.product_name_snapshot,
                   SUM(l.quantity) AS quantity_sold, SUM(l.line_total) AS gross_value
            FROM order_lines l
            JOIN orders o ON o.id = l.order_id
            WHERE o.created_at >= ? AND o.created_at < ?
              AND o.preparation_status <> 'CANCELLED'
            GROUP BY l.product_id, l.product_no_snapshot, l.product_name_snapshot
            ORDER BY quantity_sold DESC, gross_value DESC
            LIMIT ?
            """,
            (rs, row) -> new ProductSalesRow(
                rs.getObject("product_id", UUID.class),
                rs.getLong("product_no_snapshot"),
                rs.getString("product_name_snapshot"),
                rs.getBigDecimal("quantity_sold"),
                rs.getBigDecimal("gross_value")
            ),
            start.atStartOfDay().atOffset(ZoneOffset.UTC),
            end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
            safeLimit
        );
    }

    public record SalesSummary(
        LocalDate from,
        LocalDate to,
        long orders,
        long cancelledOrders,
        BigDecimal orderValue,
        BigDecimal confirmedCollections,
        BigDecimal pendingConfirmations,
        BigDecimal refundedAmount,
        BigDecimal netSalesAfterRefunds,
        String reportingTimeZone
    ) {}

    public record ProductSalesRow(
        UUID productId,
        long productNumber,
        String productName,
        BigDecimal quantitySold,
        BigDecimal grossValue
    ) {}

    private record SummaryRow(long orderCount, long cancelledCount, BigDecimal orderValue) {}
    private record PaymentTotals(BigDecimal collected, BigDecimal pending, BigDecimal refunded) {}
}