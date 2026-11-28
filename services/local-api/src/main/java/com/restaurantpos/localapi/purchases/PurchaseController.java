package com.restaurantpos.localapi.purchases;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.purchases.PurchaseDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {
 private final PurchaseService service;
 public PurchaseController(PurchaseService service){this.service=service;}
 @PostMapping public ResponseEntity<PurchaseView> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreatePurchaseRequest request){return ResponseEntity.ok(service.create(key,request));}
 @GetMapping("/{id}") public PurchaseView get(@PathVariable UUID id){return service.get(id);}
 @GetMapping public List<PurchaseView> list(@RequestParam(defaultValue="100") int limit){return service.list(limit);}
}