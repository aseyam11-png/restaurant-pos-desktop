package com.restaurantpos.localapi.inventory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public final class InventoryDtos {
 private InventoryDtos() {}
 public enum MovementType { RECEIPT, CONSUMPTION, WASTE, ADJUSTMENT }
 public record RecordMovementRequest(@NotNull UUID productId,@NotNull MovementType movementType,@NotNull BigDecimal quantityDelta,@NotBlank @Size(max=240) String reason,@NotBlank @Size(max=80) String createdBy) {}
 public record MinimumStockRequest(@NotNull BigDecimal minimumQuantity) {}
 public record BalanceView(UUID productId,long productNumber,String productName,String unit,BigDecimal quantityOnHand,BigDecimal minimumQuantity,boolean lowStock,OffsetDateTime updatedAt) {}
 public record MovementView(UUID id,UUID productId,String productName,MovementType movementType,BigDecimal quantityDelta,String reason,String createdBy,OffsetDateTime createdAt) {}
}