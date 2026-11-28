package com.restaurantpos.localapi.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {
    private CatalogDtos() {}

    public enum PricingMode {
        FIXED,
        ASK_AT_SALE
    }

    public record CategoryView(UUID id, String name, int sortOrder, boolean active) {}

    public record CreateCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        Integer sortOrder
    ) {}

    public record ProductView(
        UUID id,
        long productNumber,
        UUID categoryId,
        String categoryName,
        String name,
        String unit,
        PricingMode pricingMode,
        BigDecimal price,
        boolean stockTracked,
        boolean active
    ) {}

    public record CreateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 24) String unit,
        @NotNull PricingMode pricingMode,
        @DecimalMin(value = "0.01", message = "Price must be positive") BigDecimal price,
        Boolean stockTracked
    ) {}
}