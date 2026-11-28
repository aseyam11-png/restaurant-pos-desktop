package com.restaurantpos.localapi.orders;

import java.math.BigDecimal;

import com.restaurantpos.localapi.api.PosException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderPricingTest {
    @Test
    void roundsEachLineToTwoMinorDigits() {
        assertEquals(new BigDecimal("2.51"), OrderPricing.lineTotal(
            new BigDecimal("1.255"), new BigDecimal("2")
        ));
    }

    @Test
    void roundsFractionalQuantitiesDeterministically() {
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
    void rejectsDiscountAboveSubtotal() {
        assertThrows(PosException.class, () -> OrderPricing.orderTotal(
            new BigDecimal("10.00"), new BigDecimal("10.01"), BigDecimal.ZERO
        ));
    }

    @Test
    void calculatesTotalWithoutFloatingPointArithmetic() {
        assertEquals(new BigDecimal("16.75"), OrderPricing.orderTotal(
            new BigDecimal("20.00"), new BigDecimal("5.25"), new BigDecimal("2.00")
        ));
    }
}