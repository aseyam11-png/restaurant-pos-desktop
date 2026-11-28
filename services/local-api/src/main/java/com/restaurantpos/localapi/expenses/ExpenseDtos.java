package com.restaurantpos.localapi.expenses;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public final class ExpenseDtos {
 private ExpenseDtos(){}
 public record CreateExpenseRequest(@NotBlank @Size(max=80) String category,@NotBlank @Size(max=240) String description,@NotNull @DecimalMin("0.01") BigDecimal amount,@NotNull PaymentMethod paymentMethod,OffsetDateTime incurredAt,@NotBlank @Size(max=80) String createdBy){}
 public record ExpenseView(UUID id,String category,String description,BigDecimal amount,String currency,PaymentMethod paymentMethod,UUID cashShiftId,OffsetDateTime incurredAt,String createdBy){}
}