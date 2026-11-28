package com.restaurantpos.localapi.suppliers;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.suppliers.SupplierDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
 private final SupplierService service;
 public SupplierController(SupplierService service){this.service=service;}
 @PostMapping public ResponseEntity<SupplierView> create(@Valid @RequestBody CreateSupplierRequest request){return ResponseEntity.ok(service.create(request));}
 @GetMapping public List<SupplierView> list(@RequestParam(required=false) String query,@RequestParam(defaultValue="100") int limit){return service.list(query,limit);}
 @PatchMapping("/{id}/deactivate") public ResponseEntity<Void> deactivate(@PathVariable UUID id){service.deactivate(id);return ResponseEntity.noContent().build();}
}