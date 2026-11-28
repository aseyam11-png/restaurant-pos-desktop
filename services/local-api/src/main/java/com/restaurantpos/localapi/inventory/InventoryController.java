package com.restaurantpos.localapi.inventory;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.inventory.InventoryDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/balances") public List<BalanceView> balances(@RequestParam(required=false) String query,@RequestParam(defaultValue="false") boolean lowStockOnly,@RequestParam(defaultValue="100") int limit){return service.listBalances(query,lowStockOnly,limit);}
 @PostMapping("/movements") public MovementView movement(@Valid @RequestBody RecordMovementRequest request){return service.recordMovement(request);}
 @PatchMapping("/products/{id}/minimum") public BalanceView minimum(@PathVariable UUID id,@Valid @RequestBody MinimumStockRequest request){return service.setMinimum(id,request.minimumQuantity());}
 @GetMapping("/products/{id}/movements") public List<MovementView> movements(@PathVariable UUID id,@RequestParam(defaultValue="100") int limit){return service.listMovements(id,limit);}
}