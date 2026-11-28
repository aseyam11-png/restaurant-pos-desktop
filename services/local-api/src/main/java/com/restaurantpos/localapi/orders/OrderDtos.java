package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrderDtos {
    private OrderDtos() {}

    public enum OrderType {
        DINE_IN,
        TAKEAWAY,
        DELIVERY
    }

    public enum PreparationStatus {
        NEW,
        IN_PREPARATION,
        READY,
        DELIVERED,
        CANCELLED
    }

    public enum PaymentStatus {
        UNPAID,
        PENDING_CONFIRMATION,
        PARTIALLY_PAID,
        PAID,
        REFUNDED
    }

    public enum PaymentMethod {
        CASH,
        WALLET,
        BANK_CARD
    }

    public enum IndividualPaymentStatus {
        PENDING_CONFIRMATION,
        CONFIRMED,
        REFUNDED
    }

    public record OrderLineRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin(value = "0.001", message = "Quantity must be positive") BigDecimal quantity,
        BigDecimal unitPrice
    ) {}

    public record PaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull @DecimalMin(value = "0.01", message = "Payment amount must be positive") BigDecimal amount,
        @Size(max = 24) String senderPhone
    ) {}

    public record CreateOrderRequest(
        @NotBlank @Size(max = 80) String customerName,
        @NotNull OrderType orderType,
        @NotEmpty @Size(max = 100) List<@Valid OrderLineRequest> lines,
        @DecimalMin(value = "0.00") BigDecimal discountAmount,
        @DecimalMin(value = "0.00") BigDecimal taxAmount,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @Valid List<PaymentRequest> payments
    ) {}

    public record CancelOrderRequest(
        @NotBlank @Size(max = 240) String reason
    ) {}

    public record ConfirmPaymentRequest(
        @NotBlank @Size(max = 80) String confirmedBy
    ) {}

    public record OrderLineView(
        UUID id,
        UUID productId,
        long productNumber,
        String productName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {}

    public record PaymentView(
        UUID id,
        PaymentMethod method,
        IndividualPaymentStatus status,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt,
        OffsetDateTime confirmedAt
    ) {}

    public record OrderView(
        UUID id,
        long orderNumber,
        String customerName,
        OrderType orderType,
        PreparationStatus preparationStatus,
        PaymentStatus paymentStatus,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        String currency,
        OffsetDateTime createdAt,
        List<OrderLineView> lines,
        List<PaymentView> payments
    ) {}

    public record PrintJobView(
        UUID id,
        UUID orderId,
        long orderNumber,
        String documentType,
        String status,
        boolean reprint,
        int attempts,
        OffsetDateTime createdAt,
        String lastError
    ) {}
}