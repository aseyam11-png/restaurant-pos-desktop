package com.restaurantpos.localapi.delivery;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.*;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.delivery.DeliveryDtos.*;
import com.restaurantpos.localapi.orders.OrderDtos.*;
import com.restaurantpos.localapi.orders.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class DeliveryIntegrationTest {
    @Autowired private DeliveryService delivery;
    @Autowired private CatalogService catalog;
    @Autowired private OrderService orders;

    @Test
    void deliveryOrderCanBeAssignedAndReassignedToAnActiveRider() {
        RiderView firstRider = delivery.createRider(new CreateRiderRequest("Rider One", "0591111111", null));
        RiderView secondRider = delivery.createRider(new CreateRiderRequest("Rider Two", "0592222222", null));
        var category = catalog.createCategory(new CreateCategoryRequest("Delivery " + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Delivery item", "piece", PricingMode.FIXED, new BigDecimal("7.00"), false
        ));
        OrderView order = orders.createOrder("delivery-" + UUID.randomUUID(), new CreateOrderRequest(
            "Delivery customer", OrderType.DELIVERY,
            List.of(new OrderLineRequest(product.id(), BigDecimal.ONE, null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));

        DeliveryAssignmentView assigned = delivery.assign(order.id(),
            new AssignRiderRequest(firstRider.id(), "Main street 12", "0593333333"));
        assertEquals(firstRider.id(), assigned.riderId());
        assertEquals("Main street 12", assigned.address());
        assertEquals("0593333333", assigned.customerPhone());

        DeliveryAssignmentView reassigned = delivery.assign(order.id(),
            new AssignRiderRequest(secondRider.id(), "Main street 12", "0593333333"));
        assertEquals(secondRider.id(), reassigned.riderId());

        assertThrows(PosException.class, () -> delivery.assign(order.id(),
            new AssignRiderRequest(UUID.randomUUID(), "Main street 12", "0593333333")));
    }

    @Test
    void nonDeliveryOrdersCannotReceiveRiderAssignments() {
        RiderView rider = delivery.createRider(new CreateRiderRequest("Test Rider", "0594444444", null));
        var category = catalog.createCategory(new CreateCategoryRequest("NonDelivery " + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Counter item", "piece", PricingMode.FIXED, new BigDecimal("2.00"), false
        ));
        OrderView order = orders.createOrder("takeaway-" + UUID.randomUUID(), new CreateOrderRequest(
            "Counter customer", OrderType.TAKEAWAY,
            List.of(new OrderLineRequest(product.id(), BigDecimal.ONE, null)),
            BigDecimal.ZERO, BigDecimal.ZERO, "ILS", List.of()
        ));
        assertThrows(PosException.class, () -> delivery.assign(order.id(),
            new AssignRiderRequest(rider.id(), "Some address", "0595555555")));
    }
}
