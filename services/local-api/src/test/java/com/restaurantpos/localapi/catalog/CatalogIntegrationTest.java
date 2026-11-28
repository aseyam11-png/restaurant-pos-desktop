package com.restaurantpos.localapi.catalog;

import java.math.BigDecimal;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.*;
import com.restaurantpos.localapi.catalog.CatalogService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class CatalogIntegrationTest {
    @Autowired private CatalogService catalog;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void fixedPriceRequiresPositiveAmountWithAtMostTwoDecimals() {
        var category = catalog.createCategory(new CreateCategoryRequest("Price-" + UUID.randomUUID(), 0));

        assertThrows(PosException.class, () -> catalog.createProduct(new CreateProductRequest(
            category.id(), "Zero", "piece", PricingMode.FIXED, BigDecimal.ZERO, false
        )));
        assertThrows(PosException.class, () -> catalog.createProduct(new CreateProductRequest(
            category.id(), "Negative", "piece", PricingMode.FIXED, new BigDecimal("-1.00"), false
        )));
        assertThrows(PosException.class, () -> catalog.createProduct(new CreateProductRequest(
            category.id(), "Fractional cents", "piece", PricingMode.FIXED, new BigDecimal("1.005"), false
        )));
    }

    @Test
    void askAtSaleProductMustNotStoreFixedPrice() {
        var category = catalog.createCategory(new CreateCategoryRequest("Ask-" + UUID.randomUUID(), 0));
        assertThrows(PosException.class, () -> catalog.createProduct(new CreateProductRequest(
            category.id(), "Conflicting price", "piece", PricingMode.ASK_AT_SALE,
            new BigDecimal("4.00"), false
        )));

        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Price at counter", "piece", PricingMode.ASK_AT_SALE, null, false
        ));
        BigDecimal stored = jdbc.query(
            "SELECT price FROM products WHERE id = ?",
            rs -> rs.next() ? rs.getBigDecimal(1) : null,
            product.id()
        );
        assertNull(stored);
    }

    @Test
    void stockTrackedProductCreatesZeroBalance() {
        var category = catalog.createCategory(new CreateCategoryRequest("Stock-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Stock product", "piece", PricingMode.FIXED,
            new BigDecimal("2.50"), true
        ));
        BigDecimal balance = jdbc.queryForObject(
            "SELECT quantity_on_hand FROM inventory_balances WHERE product_id = ?",
            BigDecimal.class,
            product.id()
        );
        assertEquals(new BigDecimal("0.000"), balance);
    }
}
