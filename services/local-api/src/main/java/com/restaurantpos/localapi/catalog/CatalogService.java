package com.restaurantpos.localapi.catalog;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurantpos.localapi.api.PosException;
import com.restaurantpos.localapi.catalog.CatalogDtos.CategoryView;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateCategoryRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateProductRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.ProductView;
import com.restaurantpos.localapi.catalog.CatalogDtos.PricingMode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;

    public CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryView> listCategories() {
        return jdbc.query(
            "SELECT id, name, sort_order, active FROM categories WHERE active = TRUE ORDER BY sort_order, name",
            (rs, row) -> new CategoryView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getInt("sort_order"),
                rs.getBoolean("active")
            )
        );
    }

    @Transactional
    public CategoryView createCategory(CreateCategoryRequest request) {
        String name = request.name().trim();
        int order = request.sortOrder() == null ? 0 : request.sortOrder();
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO categories(id, name, sort_order) VALUES (?, ?, ?)",
            id, name, order
        );
        return new CategoryView(id, name, order, true);
    }

    public List<ProductView> listProducts(String search, UUID categoryId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.product_no, p.category_id, c.name AS category_name,
                   p.name, p.unit, p.pricing_mode, p.price, p.stock_tracked, p.active
            FROM products p
            JOIN categories c ON c.id = p.category_id
            WHERE p.active = TRUE AND c.active = TRUE
            """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();

        if (categoryId != null) {
            sql.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        if (StringUtils.hasText(search)) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append(" AND (lower(p.name) LIKE ? OR CAST(p.product_no AS TEXT) LIKE ?)");
            args.add(term);
            args.add(term);
        }
        sql.append(" ORDER BY c.sort_order, p.name LIMIT ?");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, row) -> new ProductView(
            rs.getObject("id", UUID.class),
            rs.getLong("product_no"),
            rs.getObject("category_id", UUID.class),
            rs.getString("category_name"),
            rs.getString("name"),
            rs.getString("unit"),
            PricingMode.valueOf(rs.getString("pricing_mode")),
            rs.getBigDecimal("price"),
            rs.getBoolean("stock_tracked"),
            rs.getBoolean("active")
        ), args.toArray());
    }

    @Transactional
    public ProductView createProduct(CreateProductRequest request) {
        if (request.pricingMode() == CatalogDtos.PricingMode.FIXED) {
            java.math.BigDecimal price = request.price();
            if (price == null || price.signum() <= 0 || price.scale() > 2 || price.precision() > 14) {
                throw PosException.badRequest(
                    "INVALID_PRODUCT_PRICE",
                    "Fixed product prices must be positive and have at most two decimal places."
                );
            }
        } else if (request.price() != null) {
            throw PosException.badRequest(
                "UNEXPECTED_PRODUCT_PRICE",
                "Ask-at-sale products must not store a fixed catalogue price."
            );
        }
        UUID categoryId = request.categoryId();
        String categoryName = jdbc.query(
            "SELECT name FROM categories WHERE id = ? AND active = TRUE",
            rs -> rs.next() ? rs.getString(1) : null,
            categoryId
        );
        if (categoryName == null) {
            throw PosException.notFound("Active category was not found.");
        }

        if (request.pricingMode() == PricingMode.FIXED && request.price() == null) {
            throw PosException.badRequest("FIXED_PRICE_REQUIRED", "A fixed-price product requires a sale price.");
        }
        if (request.pricingMode() == PricingMode.ASK_AT_SALE && request.price() != null) {
            throw PosException.badRequest("ASK_PRICE_MUST_BE_NULL", "A price-at-sale product cannot store a default sale price.");
        }

        UUID id = UUID.randomUUID();
        Long productNumber = jdbc.queryForObject(
            "INSERT INTO products(id, category_id, name, unit, pricing_mode, price, stock_tracked) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING product_no",
            (rs, row) -> rs.getLong(1),
            id,
            categoryId,
            request.name().trim(),
            request.unit().trim(),
            request.pricingMode().name(),
            request.price(),
            Boolean.TRUE.equals(request.stockTracked())
        );
        boolean stockTracked = Boolean.TRUE.equals(request.stockTracked());
        if (stockTracked) {
            jdbc.update("INSERT INTO inventory_balances(product_id, quantity_on_hand, minimum_quantity) VALUES (?, 0, 0)", id);
        }
        return new ProductView(
            id,
            productNumber == null ? 0 : productNumber,
            categoryId,
            categoryName,
            request.name().trim(),
            request.unit().trim(),
            request.pricingMode(),
            request.price(),
            stockTracked,
            true
        );
    }

    @Transactional
    public void deactivateProduct(UUID id) {
        int changed = jdbc.update(
            "UPDATE products SET active = FALSE, updated_at = now() WHERE id = ? AND active = TRUE",
            id
        );
        if (changed == 0) {
            throw PosException.notFound("Active product was not found.");
        }
    }
}