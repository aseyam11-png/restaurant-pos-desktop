package com.restaurantpos.localapi.delivery;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.delivery.DeliveryDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class DeliveryController {
 private final DeliveryService service;
 public DeliveryController(DeliveryService service){this.service=service;}
 @GetMapping("/delivery/riders") public List<RiderView> riders(@RequestParam(required=false)String query){return service.listRiders(query);}
 @PostMapping("/delivery/riders") public ResponseEntity<RiderView> create(@Valid @RequestBody CreateRiderRequest r){return ResponseEntity.ok(service.createRider(r));}
 @PatchMapping("/delivery/riders/{id}/deactivate") public ResponseEntity<Void> deactivate(@PathVariable UUID id){service.deactivate(id);return ResponseEntity.noContent().build();}
 @PutMapping("/orders/{orderId}/delivery") public DeliveryAssignmentView assign(@PathVariable UUID orderId,@Valid @RequestBody AssignRiderRequest r){return service.assign(orderId,r);}
 @GetMapping("/orders/{orderId}/delivery") public DeliveryAssignmentView delivery(@PathVariable UUID orderId){return service.getAssignment(orderId);}
}