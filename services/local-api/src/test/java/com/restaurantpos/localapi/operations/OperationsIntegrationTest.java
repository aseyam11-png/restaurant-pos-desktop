package com.restaurantpos.localapi.operations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.cash.CashShiftDtos.*;
import com.restaurantpos.localapi.cash.CashShiftService;
import com.restaurantpos.localapi.catalog.CatalogDtos.*;
import com.restaurantpos.localapi.catalog.CatalogService;
import com.restaurantpos.localapi.expenses.ExpenseDtos.CreateExpenseRequest;
import com.restaurantpos.localapi.expenses.ExpenseService;
import com.restaurantpos.localapi.inventory.InventoryDtos.*;
import com.restaurantpos.localapi.inventory.InventoryService;
import com.restaurantpos.localapi.orders.OrderDtos.PaymentMethod;
import com.restaurantpos.localapi.purchases.PurchaseDtos.*;
import com.restaurantpos.localapi.purchases.PurchaseService;
import com.restaurantpos.localapi.purchases.SupplierPaymentDtos.RecordPaymentRequest;
import com.restaurantpos.localapi.purchases.SupplierPaymentService;
import com.restaurantpos.localapi.suppliers.SupplierDtos.CreateSupplierRequest;
import com.restaurantpos.localapi.suppliers.SupplierService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "pos.security.local-token=integration-test-token")
@Transactional
class OperationsIntegrationTest {
    @Autowired private CashShiftService cashShifts;
    @Autowired private CatalogService catalog;
    @Autowired private InventoryService inventory;
    @Autowired private SupplierService suppliers;
    @Autowired private PurchaseService purchases;
    @Autowired private SupplierPaymentService supplierPayments;
    @Autowired private ExpenseService expenses;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void cashShiftReconcilesOpeningAndManualMovementsAndPreventsNegativeCash() {
        CashShiftView opened = cashShifts.open(new OpenShiftRequest(new BigDecimal("50.00"), "Owner"));
        assertEquals(new BigDecimal("50.00"), opened.expectedCash());

        cashShifts.addManualMovement(new ManualMovementRequest(
            ManualMovementType.CASH_OUT, new BigDecimal("20.00"), "Small cash expense", "Cashier"
        ));
        cashShifts.addManualMovement(new ManualMovementRequest(
            ManualMovementType.CASH_IN, new BigDecimal("5.00"), "Cash correction", "Cashier"
        ));

        CashShiftView active = cashShifts.active();
        assertEquals(new BigDecimal("35.00"), active.expectedCash());
        assertThrows(PosException.class, () ->
            cashShifts.addManualMovement(new ManualMovementRequest(
                ManualMovementType.CASH_OUT, new BigDecimal("36.00"), "Overdraw", "Cashier"
            ))
        );
        assertEquals(new BigDecimal("35.00"), cashShifts.active().expectedCash());

        CashShiftView closed = cashShifts.close(opened.id(), new CloseShiftRequest(new BigDecimal("35.00"), "Owner"));
        assertEquals("CLOSED", closed.status());
        assertEquals(new BigDecimal("0.00"), closed.variance());
    }

    @Test
    void inventoryLedgerPreventsNegativeStock() {
        var category = catalog.createCategory(new CreateCategoryRequest("Inventory-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Stock product", "piece", PricingMode.FIXED,
            new BigDecimal("4.00"), true
        ));

        inventory.recordMovement(new RecordMovementRequest(
            product.id(), MovementType.RECEIPT, new BigDecimal("3.500"), "Opening receipt", "Owner"
        ));
        inventory.recordMovement(new RecordMovementRequest(
            product.id(), MovementType.WASTE, new BigDecimal("-1.000"), "Damaged goods", "Manager"
        ));

        var balance = inventory.listBalances(null, false, 10).stream()
            .filter(row -> row.productId().equals(product.id()))
            .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2.500"), balance.quantityOnHand());

        assertThrows(PosException.class, () -> inventory.recordMovement(new RecordMovementRequest(
            product.id(), MovementType.WASTE, new BigDecimal("-3.000"), "Excess waste", "Manager"
        )));
        var unchanged = inventory.listBalances(null, false, 10).stream()
            .filter(row -> row.productId().equals(product.id()))
            .findFirst().orElseThrow();
        assertEquals(new BigDecimal("2.500"), unchanged.quantityOnHand());
    }

    @Test
    void purchaseIsIdempotentAndPostsOneStockMovement() {
        var supplier = suppliers.create(new CreateSupplierRequest(
            "Supplier-" + UUID.randomUUID(), "0590000000", null, "Test supplier"
        ));
        var category = catalog.createCategory(new CreateCategoryRequest("Purchase-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Purchased product", "box", PricingMode.FIXED,
            new BigDecimal("8.00"), true
        ));
        var request = new CreatePurchaseRequest(
            supplier.id(),
            List.of(new PurchaseLineRequest(product.id(), new BigDecimal("5.000"), new BigDecimal("2.00"))),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            "ILS",
            "Owner",
            OffsetDateTime.now()
        );
        String key = "purchase-" + UUID.randomUUID();
        PurchaseView first = purchases.create(key, request);
        PurchaseView replay = purchases.create(key, request);

        assertEquals(first.id(), replay.id());
        assertEquals(new BigDecimal("10.00"), first.total());
        assertEquals(new BigDecimal("10.00"), first.dueAmount());

        var balance = inventory.listBalances("Purchased product", false, 10).stream()
            .filter(row -> row.productId().equals(product.id()))
            .findFirst().orElseThrow();
        assertEquals(new BigDecimal("5.000"), balance.quantityOnHand());

        Long movements = jdbc.queryForObject(
            "SELECT COUNT(*) FROM inventory_movements WHERE reference_type = 'PURCHASE' AND reference_id = ?",
            Long.class,
            first.id()
        );
        assertEquals(1L, movements);
    }

    @Test
    void cashExpenseIsIdempotentAndReconcilesAgainstShift() {
        CashShiftView shift = cashShifts.open(new OpenShiftRequest(new BigDecimal("30.00"), "Owner"));
        var request = new CreateExpenseRequest(
            "Utilities", "Shop electricity", new BigDecimal("12.50"),
            PaymentMethod.CASH, OffsetDateTime.now(), "Owner"
        );
        String key = "expense-" + UUID.randomUUID();
        var first = expenses.create(key, request);
        var replay = expenses.create(key, request);

        assertEquals(first.id(), replay.id());
        assertEquals(shift.id(), first.cashShiftId());
        assertEquals(new BigDecimal("17.50"), cashShifts.active().expectedCash());

        Long movements = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cash_movements WHERE shift_id = ? AND reference_type = 'EXPENSE' AND reference_id = ?",
            Long.class,
            shift.id(),
            first.id()
        );
        assertEquals(1L, movements);
    }
    @Test
    void supplierPaymentsSettleOutstandingBalanceIdempotently() {
        var category = catalog.createCategory(new CreateCategoryRequest("SupplierPay-" + UUID.randomUUID(), 0));
        var product = catalog.createProduct(new CreateProductRequest(
            category.id(), "Supplier pay product", "pack", PricingMode.FIXED,
            new BigDecimal("10.00"), true
        ));
        PurchaseView purchase = purchases.create("supplier-pay-purchase-" + UUID.randomUUID(),
            new CreatePurchaseRequest(
                null,
                List.of(new PurchaseLineRequest(product.id(), new BigDecimal("1.000"), new BigDecimal("10.00"))),
                BigDecimal.ZERO, BigDecimal.ZERO, null, "ILS", "Owner", OffsetDateTime.now()
            )
        );
        assertEquals("UNPAID", purchase.paymentStatus());
        assertEquals(new BigDecimal("10.00"), purchase.dueAmount());

        String firstKey = "supplier-payment-" + UUID.randomUUID();
        var first = supplierPayments.record(
            purchase.id(), firstKey,
            new RecordPaymentRequest(new BigDecimal("3.00"), PaymentMethod.BANK_CARD, "Owner")
        );
        var replay = supplierPayments.record(
            purchase.id(), firstKey,
            new RecordPaymentRequest(new BigDecimal("3.00"), PaymentMethod.BANK_CARD, "Owner")
        );
        assertEquals(first.id(), replay.id());
        assertEquals(new BigDecimal("3.00"), first.purchasePaidTotal());
        assertEquals(new BigDecimal("7.00"), first.purchaseDueTotal());
        assertEquals("PARTIALLY_PAID", first.purchasePaymentStatus());

        var finalPayment = supplierPayments.record(
            purchase.id(), "supplier-payment-final-" + UUID.randomUUID(),
            new RecordPaymentRequest(new BigDecimal("7.00"), PaymentMethod.WALLET, "Owner")
        );
        assertEquals(new BigDecimal("10.00"), finalPayment.purchasePaidTotal());
        assertEquals(new BigDecimal("0.00"), finalPayment.purchaseDueTotal());
        assertEquals("PAID", finalPayment.purchasePaymentStatus());
        assertEquals(2, supplierPayments.list(purchase.id()).size());

        assertThrows(PosException.class, () -> supplierPayments.record(
            purchase.id(), "supplier-payment-over-" + UUID.randomUUID(),
            new RecordPaymentRequest(new BigDecimal("0.01"), PaymentMethod.BANK_CARD, "Owner")
        ));
    }

}
