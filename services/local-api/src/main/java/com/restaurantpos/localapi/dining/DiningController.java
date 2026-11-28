package com.restaurantpos.localapi.dining;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.dining.DiningDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/dining")
public class DiningController {
 private final DiningService service;
 public DiningController(DiningService service){this.service=service;}
 @GetMapping("/zones") public List<ZoneView> zones(){return service.listZones();}
 @PostMapping("/zones") public ResponseEntity<ZoneView> zone(@Valid @RequestBody CreateZoneRequest r){return ResponseEntity.ok(service.createZone(r));}
 @GetMapping("/unassigned-orders") public List<UnassignedDineInOrderView> unassignedOrders(){return service.listUnassignedDineInOrders();}
 @GetMapping("/tables") public List<TableView> tables(@RequestParam(required=false)UUID zoneId,@RequestParam(required=false)TableStatus status){return service.listTables(zoneId,status);}
 @PostMapping("/tables") public ResponseEntity<TableView> table(@Valid @RequestBody CreateTableRequest r){return ResponseEntity.ok(service.createTable(r));}
 @PatchMapping("/tables/{id}/status") public TableView status(@PathVariable UUID id,@Valid @RequestBody UpdateTableStatusRequest r){return service.setStatus(id,r.status());}
 @PostMapping("/tables/{id}/orders") public TableView assign(@PathVariable UUID id,@Valid @RequestBody AssignOrderRequest r){return service.assignOrder(id,r.orderId());}
 @PostMapping("/tables/{id}/transfer") public TableView transfer(@PathVariable UUID id,@Valid @RequestBody TransferOrderRequest r){return service.transferOrder(id,r.targetTableId());}
}