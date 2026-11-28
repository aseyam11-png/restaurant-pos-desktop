package com.restaurantpos.localapi.dining;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.*;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.dining.DiningDtos.*;
import com.restaurantpos.localapi.orders.OrderDtos.*;
import com.restaurantpos.localapi.orders.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class DiningIntegrationTest {
    @Autowired private DiningService dining;
    @Autowired private CatalogService catalog;
    @Autowired private OrderService orders;

    @Test
    void tableCanBeAssignedTransferredAndAutomaticallyReleasedOnDelivery() {
        ZoneView zone = dining.createZone(new CreateZoneRequest("Main " + UUID.randomUUID(), 0));
        TableView first = dining.createTable(new CreateTableRequest(zone.id(), 1, "T1", 2));
        TableView second = dining.createTable(new CreateTableRequest(zone.id(), 2, "T2", 4));

        var category = catalog.createCategory(new CreateCategoryRequest("Dining " + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Dining item", "piece", PricingMode.FIXED, new BigDecimal("5.00"), false
        ));
        OrderView order = orders.createOrder("dine-" + UUID.randomUUID(), new CreateOrderRequest(
            "Table customer", OrderType.DINE_IN,
            List.of(new OrderLineRequest(product.id(), new BigDecimal("1"), null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));

        TableView occupied = dining.assignOrder(first.id(), order.id());
        assertEquals(TableStatus.OCCUPIED, occupied.status());
        assertEquals(order.id(), occupied.activeOrderId());

        TableView transferred = dining.transferOrder(first.id(), second.id());
        assertEquals(TableStatus.OCCUPIED, transferred.status());
        assertEquals(order.id(), transferred.activeOrderId());
        assertEquals(TableStatus.AVAILABLE, dining.getTable(first.id()).status());

        orders.changePreparationStatus(order.id(), PreparationStatus.IN_PREPARATION);
        orders.changePreparationStatus(order.id(), PreparationStatus.READY);
        orders.changePreparationStatus(order.id(), PreparationStatus.DELIVERED);

        assertEquals(TableStatus.AVAILABLE, dining.getTable(second.id()).status());
        assertNull(dining.getTable(second.id()).activeOrderId());
    }

    @Test
    void tableCannotBeMarkedOccupiedWithoutAnAssignedOrder() {
        ZoneView zone = dining.createZone(new CreateZoneRequest("Zone " + UUID.randomUUID(), 0));
        TableView table = dining.createTable(new CreateTableRequest(zone.id(), 1, "T1", 2));
        assertThrows(PosException.class, () -> dining.setStatus(table.id(), TableStatus.OCCUPIED));
        assertEquals(TableStatus.AVAILABLE, dining.getTable(table.id()).status());
    }

    @Test
    void cancellingUnpaidDineInOrderAutomaticallyReleasesItsTable() {
        ZoneView zone = dining.createZone(new CreateZoneRequest("Cancel " + UUID.randomUUID(), 0));
        TableView table = dining.createTable(new CreateTableRequest(zone.id(), 3, "T3", 2));
        var category = catalog.createCategory(new CreateCategoryRequest("Cancel " + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Cancel item", "piece", PricingMode.FIXED, new BigDecimal("3.00"), false
        ));
        OrderView order = orders.createOrder("dine-cancel-" + UUID.randomUUID(), new CreateOrderRequest(
            "Cancel customer", OrderType.DINE_IN,
            List.of(new OrderLineRequest(product.id(), BigDecimal.ONE, null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));
        dining.assignOrder(table.id(), order.id());
        orders.cancelOrder(order.id(), "Customer left");
        assertEquals(TableStatus.AVAILABLE, dining.getTable(table.id()).status());
        assertNull(dining.getTable(table.id()).activeOrderId());
    }
}
