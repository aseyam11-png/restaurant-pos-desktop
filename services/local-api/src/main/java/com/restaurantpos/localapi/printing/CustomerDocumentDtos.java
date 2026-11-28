package com.restaurantpos.localapi.printing;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CustomerDocumentDtos {
    private CustomerDocumentDtos() {}

    public record CustomerOrderLine(
        String productName,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {}

    public record CustomerOrderDocument(
        UUID orderId,
        long orderNumber,
        String customerName,
        String orderType,
        OffsetDateTime createdAt,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        String currency,
        List<CustomerOrderLine> lines
    ) {}
}