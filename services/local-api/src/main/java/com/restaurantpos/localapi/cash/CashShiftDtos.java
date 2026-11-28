package com.restaurantpos.localapi.cash;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CashShiftDtos {
    private CashShiftDtos() {}

    public enum ManualMovementType {
        CASH_IN,
        CASH_OUT
    }

    public record OpenShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingCash,
        @NotBlank @Size(max = 80) String openedBy
    ) {}

    public record CloseShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal countedCash,
        @NotBlank @Size(max = 80) String closedBy
    ) {}

    public record ManualMovementRequest(
        @NotNull ManualMovementType type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max = 240) String description,
        @NotBlank @Size(max = 80) String createdBy
    ) {}

    public record CashShiftView(
        UUID id,
        long shiftNumber,
        String status,
        String currency,
        BigDecimal openingCash,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        String openedBy,
        String closedBy,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt
    ) {}

    public record CashMovementView(
        UUID id,
        String movementType,
        String direction,
        BigDecimal amount,
        String description,
        String actor,
        OffsetDateTime createdAt
    ) {}
}