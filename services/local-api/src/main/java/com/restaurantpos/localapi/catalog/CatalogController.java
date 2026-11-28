package com.restaurantpos.localapi.catalog;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.restaurantpos.localapi.catalog.CatalogDtos.CategoryView;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateCategoryRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.CreateProductRequest;
import com.restaurantpos.localapi.catalog.CatalogDtos.ProductView;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return service.listCategories();
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryView created = service.createCategory(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + created.id())).body(created);
    }

    @GetMapping("/products")
    public List<ProductView> products(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.listProducts(query, categoryId, limit);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductView> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductView created = service.createProduct(request);
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + created.id())).body(created);
    }

    @PatchMapping("/products/{id}/deactivate")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        service.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}