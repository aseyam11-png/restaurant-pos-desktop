package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.restaurantpos.localapi.api.PosException;

public final class OrderPricing {
    private static final int MONEY_SCALE = 2;
    private static final int QUANTITY_SCALE = 3;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("10000");

    private OrderPricing() {}

    public static BigDecimal normalizeQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_QUANTITY) > 0) {
            throw PosException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero and no more than 10000.");
        }
        if (quantity.scale() > QUANTITY_SCALE) {
            throw PosException.badRequest("INVALID_QUANTITY_SCALE", "Quantity supports at most three decimal places.");
        }
        return quantity.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal normalizeUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw PosException.badRequest("INVALID_UNIT_PRICE", "Unit price must be greater than zero.");
        }
        if (unitPrice.scale() > MONEY_SCALE || unitPrice.precision() > 14) {
            throw PosException.badRequest("INVALID_UNIT_PRICE_SCALE", "Unit price supports at most two decimal places.");
        }
        return unitPrice.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal quantity) {
        return normalizeUnitPrice(unitPrice)
            .multiply(normalizeQuantity(quantity))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal moneyOrZero(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(MONEY_SCALE);
        if (value.signum() < 0 || value.scale() > MONEY_SCALE || value.precision() > 14) {
            throw PosException.badRequest("INVALID_MONEY_AMOUNT", "Money amounts must be non-negative with at most two decimal places.");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal orderTotal(BigDecimal subtotal, BigDecimal discount, BigDecimal tax) {
        BigDecimal safeSubtotal = moneyOrZero(subtotal);
        BigDecimal safeDiscount = moneyOrZero(discount);
        BigDecimal safeTax = moneyOrZero(tax);
        if (safeDiscount.compareTo(safeSubtotal) > 0) {
            throw PosException.badRequest("DISCOUNT_EXCEEDS_SUBTOTAL", "Discount cannot exceed the subtotal.");
        }
        return safeSubtotal.subtract(safeDiscount).add(safeTax).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}