package com.restaurantpos.localapi.dining;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.*;
public final class DiningDtos {
 private DiningDtos(){}
 public enum TableStatus { AVAILABLE, RESERVED, OCCUPIED, OUT_OF_SERVICE }
 public record CreateZoneRequest(@NotBlank @Size(max=80) String name,@Min(0) Integer sortOrder){}
 public record CreateTableRequest(@NotNull UUID zoneId,@NotNull @Min(1) Integer tableNumber,@NotBlank @Size(max=40) String label,@NotNull @Min(1) @Max(50) Integer seats){}
 public record UpdateTableStatusRequest(@NotNull TableStatus status){}
 public record AssignOrderRequest(@NotNull UUID orderId){}
 public record TransferOrderRequest(@NotNull UUID targetTableId){}
 public record ZoneView(UUID id,String name,int sortOrder,boolean active){}
 public record TableView(UUID id,UUID zoneId,String zoneName,int tableNumber,String label,int seats,TableStatus status,UUID activeOrderId,long activeOrderNumber,OffsetDateTime updatedAt){}
}