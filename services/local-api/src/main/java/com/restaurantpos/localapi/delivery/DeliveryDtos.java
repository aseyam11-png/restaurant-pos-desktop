package com.restaurantpos.localapi.delivery;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public final class DeliveryDtos {
 private DeliveryDtos(){}
 public record CreateRiderRequest(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=32) String phone,@Size(max=240) String notes){}
 public record AssignRiderRequest(UUID riderId,@NotBlank @Size(max=240) String address,@NotBlank @Size(max=32) String phone){}
 public record RiderView(UUID id,String name,String phone,String notes,boolean active,OffsetDateTime createdAt){}
 public record DeliveryAssignmentView(UUID orderId,long orderNumber,UUID riderId,String riderName,String riderPhone,String address,String customerPhone){}
}