package com.restaurantpos.localapi.purchases;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SupplierPaymentDtos {
    private SupplierPaymentDtos() {}

    public record RecordPaymentRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull PaymentMethod method,
        @NotBlank @Size(max = 80) String recordedBy
    ) {}

    public record SupplierPaymentView(
        UUID id,
        UUID purchaseId,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        UUID cashShiftId,
        OffsetDateTime paidAt,
        String recordedBy,
        BigDecimal purchasePaidTotal,
        BigDecimal purchaseDueTotal,
        String purchasePaymentStatus
    ) {}
}
