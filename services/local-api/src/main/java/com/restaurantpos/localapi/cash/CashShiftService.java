package com.restaurantpos.localapi.cash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftDtos.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashShiftService {
    public enum Direction {
        IN,
        OUT
    }

    public enum MovementType {
        OPENING,
        SALE,
        REFUND,
        CASH_IN,
        CASH_OUT,
        EXPENSE,
        PURCHASE
    }

    private final JdbcTemplate jdbc;
    private final String currency;

    public CashShiftService(
        JdbcTemplate jdbc,
        @Value("${pos.currency:ILS}") String currency
    ) {
        this.jdbc = jdbc;
        this.currency = currency.toUpperCase(java.util.Locale.ROOT);
    }

    @Transactional
    public CashShiftView open(OpenShiftRequest request) {
        BigDecimal opening = money(request.openingCash());
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cash_shifts(id, status, currency, opening_cash, expected_cash, opened_by)
            VALUES (?, 'OPEN', ?, ?, 0, ?)
            """, id, currency, opening, request.openedBy().trim());

        if (opening.signum() > 0) {
            recordMovementLocked(
                id, MovementType.OPENING, Direction.IN, opening,
                "Opening cash", request.openedBy().trim(), "CASH_SHIFT", id
            );
        }
        return getById(id);
    }

    @Transactional(readOnly = true)
    public CashShiftView active() {
        UUID id = jdbc.query(
            "SELECT id FROM cash_shifts WHERE status = 'OPEN'",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null
        );
        return id == null ? null : getById(id);
    }

    @Transactional
    public CashShiftView close(UUID shiftId, CloseShiftRequest request) {
        ShiftRow shift = jdbc.query(
            """
            SELECT id, status, currency, opening_cash, opened_by
            FROM cash_shifts WHERE id = ? FOR UPDATE
            """,
            rs -> rs.next() ? new ShiftRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getString("currency").trim(),
                rs.getBigDecimal("opening_cash"),
                rs.getString("opened_by")
            ) : null,
            shiftId
        );
        if (shift == null) throw PosException.notFound("Cash shift was not found.");
        if (!"OPEN".equals(shift.status())) {
            throw PosException.conflict("SHIFT_ALREADY_CLOSED", "This cash shift is already closed.");
        }

        BigDecimal expected = jdbc.queryForObject("""
            SELECT CAST(
                ROUND(
                    COALESCE(SUM(amount) FILTER (WHERE direction = 'IN'), 0)
                    - COALESCE(SUM(amount) FILTER (WHERE direction = 'OUT'), 0),
                    2
                ) AS NUMERIC(14,2)
            )
            FROM cash_movements WHERE shift_id = ?
            """, BigDecimal.class, shiftId);
        expected = money(expected);
        BigDecimal counted = money(request.countedCash());
        BigDecimal variance = counted.subtract(expected).setScale(2, RoundingMode.UNNECESSARY);

        jdbc.update("""
            UPDATE cash_shifts
            SET status = 'CLOSED', expected_cash = ?, counted_cash = ?, variance = ?,
                closed_by = ?, closed_at = now()
            WHERE id = ? AND status = 'OPEN'
            """, expected, counted, variance, request.closedBy().trim(), shiftId);
        return getById(shiftId);
    }

    @Transactional
    public CashMovementView addManualMovement(ManualMovementRequest request) {
        UUID shiftId = requireOpenShiftForUpdate();
        Direction direction = request.type() == ManualMovementType.CASH_IN ? Direction.IN : Direction.OUT;
        MovementType type = request.type() == ManualMovementType.CASH_IN ? MovementType.CASH_IN : MovementType.CASH_OUT;
        return recordMovement(
            shiftId, type, direction, moneyPositive(request.amount()),
            request.description().trim(), request.createdBy().trim(), "MANUAL", null
        );
    }

    @Transactional
    public UUID requireOpenShiftForUpdate() {
        UUID id = jdbc.query(
            "SELECT id FROM cash_shifts WHERE status = 'OPEN' FOR UPDATE",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null
        );
        if (id == null) {
            throw PosException.conflict("OPEN_CASH_SHIFT_REQUIRED", "Open a cash shift before recording a cash movement.");
        }
        return id;
    }

    @Transactional
    public CashMovementView recordMovement(
        UUID shiftId,
        MovementType type,
        Direction direction,
        BigDecimal amount,
        String description,
        String actor,
        String referenceType,
        UUID referenceId
    ) {
        ensureShiftOpenForUpdate(shiftId);
        return recordMovementLocked(shiftId, type, direction, moneyPositive(amount), description, actor, referenceType, referenceId);
    }

    @Transactional(readOnly = true)
    public List<CashMovementView> listMovements(UUID shiftId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 300));
        return jdbc.query("""
            SELECT id, movement_type, direction, amount, description, actor, created_at
            FROM cash_movements WHERE shift_id = ?
            ORDER BY created_at DESC, id DESC LIMIT ?
            """,
            (rs, row) -> new CashMovementView(
                rs.getObject("id", UUID.class),
                rs.getString("movement_type"),
                rs.getString("direction"),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getString("actor"),
                rs.getObject("created_at", OffsetDateTime.class)
            ),
            shiftId, limit
        );
    }

    private void ensureShiftOpenForUpdate(UUID shiftId) {
        String status = jdbc.query(
            "SELECT status FROM cash_shifts WHERE id = ? FOR UPDATE",
            rs -> rs.next() ? rs.getString(1) : null,
            shiftId
        );
        if (status == null) throw PosException.notFound("Cash shift was not found.");
        if (!"OPEN".equals(status)) {
            throw PosException.conflict("SHIFT_ALREADY_CLOSED", "Cash movements cannot be posted to a closed shift.");
        }
    }

    private CashMovementView recordMovementLocked(
        UUID shiftId,
        MovementType type,
        Direction direction,
        BigDecimal amount,
        String description,
        String actor,
        String referenceType,
        UUID referenceId
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO cash_movements(
                id, shift_id, movement_type, direction, amount,
                reference_type, reference_id, description, actor
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id, shiftId, type.name(), direction.name(), amount,
            referenceType, referenceId, description, actor
        );
        BigDecimal expectedDelta = direction == Direction.IN ? amount : amount.negate();
        int shiftUpdated = jdbc.update(
            """
            UPDATE cash_shifts
            SET expected_cash = expected_cash + ?
            WHERE id = ? AND status = 'OPEN' AND expected_cash + ? >= 0
            """,
            expectedDelta, shiftId, expectedDelta
        );
        if (shiftUpdated == 0) {
            throw PosException.conflict("INSUFFICIENT_CASH_FLOAT", "This cash-out movement exceeds the expected cash available in the drawer.");
        }
        OffsetDateTime createdAt = jdbc.queryForObject(
            "SELECT created_at FROM cash_movements WHERE id = ?",
            OffsetDateTime.class,
            id
        );
        return new CashMovementView(id, type.name(), direction.name(), amount, description, actor, createdAt);
    }

    private CashShiftView getById(UUID id) {
        return jdbc.query(
            """
            SELECT id, shift_no, status, currency, opening_cash, expected_cash,
                   counted_cash, variance, opened_by, closed_by, opened_at, closed_at
            FROM cash_shifts WHERE id = ?
            """,
            rs -> rs.next() ? new CashShiftView(
                rs.getObject("id", UUID.class),
                rs.getLong("shift_no"),
                rs.getString("status"),
                rs.getString("currency").trim(),
                rs.getBigDecimal("opening_cash"),
                rs.getBigDecimal("expected_cash"),
                rs.getBigDecimal("counted_cash"),
                rs.getBigDecimal("variance"),
                rs.getString("opened_by"),
                rs.getString("closed_by"),
                rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class)
            ) : null,
            id
        );
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        if (value.signum() < 0 || value.scale() > 2 || value.precision() > 14) {
            throw PosException.badRequest("INVALID_MONEY_AMOUNT", "Amount must be non-negative and have at most two decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal moneyPositive(BigDecimal value) {
        BigDecimal normalized = money(value);
        if (normalized.signum() <= 0) {
            throw PosException.badRequest("INVALID_MONEY_AMOUNT", "Amount must be greater than zero.");
        }
        return normalized;
    }

    private record ShiftRow(UUID id, String status, String currency, BigDecimal openingCash, String openedBy) {}
}