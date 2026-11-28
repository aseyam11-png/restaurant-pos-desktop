package com.restaurantpos.localapi.purchases;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public final class PurchaseDtos {
 private PurchaseDtos(){}
 public record PurchaseLineRequest(@NotNull UUID productId,@NotNull @DecimalMin("0.001") BigDecimal quantity,@NotNull @DecimalMin("0.01") BigDecimal unitCost){}
 public record CreatePurchaseRequest(UUID supplierId,@NotEmpty @Size(max=100) List<@Valid PurchaseLineRequest> lines,@DecimalMin("0.00") BigDecimal discountAmount,@NotNull @DecimalMin("0.00") BigDecimal paidAmount,PaymentMethod paymentMethod,@Pattern(regexp="[A-Z]{3}") String currency,@NotBlank @Size(max=80) String createdBy,OffsetDateTime purchasedAt){}
 public record PurchaseLineView(UUID id,UUID productId,long productNumber,String productName,String unit,BigDecimal quantity,BigDecimal unitCost,BigDecimal lineTotal){}
 public record PurchaseView(UUID id,long purchaseNumber,UUID supplierId,String status,BigDecimal subtotal,BigDecimal discountAmount,BigDecimal total,BigDecimal paidAmount,BigDecimal dueAmount,String currency,PaymentMethod paymentMethod,OffsetDateTime purchasedAt,String createdBy,List<PurchaseLineView> lines){}
}