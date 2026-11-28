package com.restaurantpos.localapi.suppliers;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public final class SupplierDtos {
 private SupplierDtos(){}
 public record CreateSupplierRequest(@NotBlank @Size(max=120) String name,@Size(max=32) String phone,@Email @Size(max=160) String email,@Size(max=500) String notes){}
 public record SupplierView(UUID id,String name,String phone,String email,String notes,boolean active,OffsetDateTime createdAt){}
}