package com.restaurantpos.localapi.settings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.*;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.orders.OrderDtos.*;
import com.restaurantpos.localapi.orders.OrderService;
import com.restaurantpos.localapi.settings.StoreSettingsDtos.UpdateStoreSettingsRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class StoreSettingsIntegrationTest {
    @Autowired private StoreSettingsService settings;
    @Autowired private CatalogService catalog;
    @Autowired private OrderService orders;

    @Test
    void storeIdentityCurrencyAndTaxRateArePersisted() {
        var initial = settings.get();
        assertEquals("ILS", initial.currency());
        assertEquals(new BigDecimal("0.00"), initial.taxRatePercent());

        var updated = settings.update(new UpdateStoreSettingsRequest(
            "SAR", new BigDecimal("7.50"), "مطعم الاختبار", "أهلاً وسهلاً"
        ));
        assertEquals("SAR", updated.currency());
        assertEquals(new BigDecimal("7.50"), updated.taxRatePercent());
        assertEquals("مطعم الاختبار", updated.businessName());
        assertEquals("أهلاً وسهلاً", updated.receiptFooter());
    }

    @Test
    void currencyCannotChangeAfterAFinancialTransactionExists() {
        var category = catalog.createCategory(new CreateCategoryRequest("Settings-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Settings item", "piece", PricingMode.FIXED,
            new BigDecimal("4.00"), false
        ));
        orders.createOrder("settings-lock-" + UUID.randomUUID(), new CreateOrderRequest(
            "Settings customer", OrderType.TAKEAWAY,
            List.of(new OrderLineRequest(product.id(), BigDecimal.ONE, null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));

        PosException error = assertThrows(PosException.class, () ->
            settings.update(new UpdateStoreSettingsRequest(
                "SAR", new BigDecimal("0.00"), "Restaurant", "Thank you"
            ))
        );
        assertEquals("ILS", settings.get().currency());
    }

    @Test
    void taxRateCannotExceedOneHundredPercent() {
        assertThrows(Exception.class, () -> settings.update(new UpdateStoreSettingsRequest(
            "ILS", new BigDecimal("100.01"), "Restaurant", "Thank you"
        )));
    }
}
