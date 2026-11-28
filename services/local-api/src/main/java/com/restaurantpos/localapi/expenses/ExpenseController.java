package com.restaurantpos.localapi.expenses;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.expenses.ExpenseDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {
 private final ExpenseService service;
 public ExpenseController(ExpenseService service){this.service=service;}
 @PostMapping public ResponseEntity<ExpenseView> create(@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody CreateExpenseRequest request){return ResponseEntity.ok(service.create(key,request));}
 @GetMapping("/{id}") public ExpenseView get(@PathVariable UUID id){return service.get(id);}
 @GetMapping public List<ExpenseView> list(@RequestParam(required=false)OffsetDateTime from,@RequestParam(required=false)OffsetDateTime to,@RequestParam(defaultValue="100")int limit){return service.list(from,to,limit);}
}