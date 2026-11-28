package com.restaurantpos.localapi.cash;

import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.cash.CashShiftDtos.*;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cash-shifts")
public class CashShiftController {
    private final CashShiftService service;

    public CashShiftController(CashShiftService service) {
        this.service = service;
    }

    @PostMapping("/open")
    public ResponseEntity<CashShiftView> open(@Valid @RequestBody OpenShiftRequest request) {
        return ResponseEntity.ok(service.open(request));
    }

    @GetMapping("/active")
    public ResponseEntity<CashShiftView> active() {
        CashShiftView active = service.active();
        return active == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(active);
    }

    @PostMapping("/{id}/close")
    public CashShiftView close(@PathVariable UUID id, @Valid @RequestBody CloseShiftRequest request) {
        return service.close(id, request);
    }

    @PostMapping("/movements")
    public CashMovementView movement(@Valid @RequestBody ManualMovementRequest request) {
        return service.addManualMovement(request);
    }

    @GetMapping("/{id}/movements")
    public List<CashMovementView> movements(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.listMovements(id, limit);
    }
}