package com.restaurantpos.localapi.orders;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.orders.OrderDtos.*;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderView> createOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderView created = service.createOrder(idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    @GetMapping("/orders")
    public List<OrderView> listOrders(@RequestParam(defaultValue = "50") int limit) {
        return service.listOrders(limit);
    }

    @GetMapping("/orders/{id}")
    public OrderView getOrder(@PathVariable UUID id) {
        return service.getOrder(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancelOrder(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return service.cancelOrder(id, request.reason());
    }

    @PatchMapping("/orders/{id}/preparation")
    public OrderView changePreparationStatus(
        @PathVariable UUID id,
        @RequestBody PreparationStatus nextStatus
    ) {
        return service.changePreparationStatus(id, nextStatus);
    }

    @PostMapping("/orders/{id}/refund")
    public OrderView refundOrder(@PathVariable UUID id, @Valid @RequestBody RefundOrderRequest request) {
        return service.refundOrder(id, request.reason(), request.refundedBy());
    }

    @PostMapping("/orders/{orderId}/payments/{paymentId}/confirm")
    public OrderView confirmPayment(
        @PathVariable UUID orderId,
        @PathVariable UUID paymentId,
        @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        return service.confirmPayment(orderId, paymentId, request.confirmedBy());
    }

    @PostMapping("/orders/{id}/reprints")
    public PrintJobView enqueueReprint(@PathVariable UUID id) {
        return service.enqueueReprint(id);
    }

    @GetMapping("/print-jobs")
    public List<PrintJobView> printJobs(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return service.listPrintJobs(status, limit);
    }
}