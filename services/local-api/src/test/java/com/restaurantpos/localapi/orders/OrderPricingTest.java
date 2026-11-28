package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;

import com.restaurantpos.localapi.api.PosException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderPricingTest {
    @Test
    void multipliesMoneyAndQuantityWithoutFloatingPointArithmetic() {
        assertEquals(new BigDecimal("2.50"), OrderPricing.lineTotal(
            new BigDecimal("1.25"), new BigDecimal("2")
        ));
    }

    @Test
    void roundsFractionalQuantitiesToCurrencyMinorUnit() {
        assertEquals(new BigDecimal("3.34"), OrderPricing.lineTotal(
            new BigDecimal("10.00"), new BigDecimal("0.334")
        ));
    }

    @Test
    void rejectsZeroOrNegativeQuantity() {
        assertThrows(PosException.class, () -> OrderPricing.normalizeQuantity(new BigDecimal("0")));
        assertThrows(PosException.class, () -> OrderPricing.normalizeQuantity(new BigDecimal("-1")));
    }

    @Test
    void rejectsMoreThanThreeQuantityDecimals() {
        assertThrows(PosException.class, () -> OrderPricing.normalizeQuantity(new BigDecimal("0.0001")));
    }

    @Test
    void rejectsUnitPriceWithMoreThanTwoDecimals() {
        assertThrows(PosException.class, () -> OrderPricing.normalizeUnitPrice(new BigDecimal("1.255")));
    }

    @Test
    void rejectsDiscountAboveSubtotal() {
        assertThrows(PosException.class, () -> OrderPricing.orderTotal(
            new BigDecimal("10.00"), new BigDecimal("10.01"), BigDecimal.ZERO
        ));
    }

    @Test
    void calculatesTotalDeterministically() {
        assertEquals(new BigDecimal("16.75"), OrderPricing.orderTotal(
            new BigDecimal("20.00"), new BigDecimal("5.25"), new BigDecimal("2.00")
        ));
    }
}