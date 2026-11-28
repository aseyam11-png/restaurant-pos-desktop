package com.restaurantpos.localapi.purchases;

import java.util.UUID;
import com.restaurantpos.localapi.purchases.SupplierPaymentDtos.RecordPaymentRequest;
import com.restaurantpos.localapi.purchases.SupplierPaymentDtos.SupplierPaymentView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchases/{purchaseId}/payments")
public class SupplierPaymentController {
    private final SupplierPaymentService service;
    public SupplierPaymentController(SupplierPaymentService service) { this.service = service; }

    @GetMapping
    public java.util.List<SupplierPaymentView> list(@PathVariable UUID purchaseId) {
        return service.list(purchaseId);
    }

    @PostMapping
    public ResponseEntity<SupplierPaymentView> record(
        @PathVariable UUID purchaseId,
        @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody RecordPaymentRequest request
    ) {
        return ResponseEntity.ok(service.record(purchaseId, key, request));
    }
}
