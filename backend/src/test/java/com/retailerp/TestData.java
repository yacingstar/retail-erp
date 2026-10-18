package com.retailerp;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.retailerp.IntegrationTest.Session;

/** Small builders for request bodies, so tests read like the scenario they check. */
final class TestData {

    private TestData() {
    }

    static Map<String, Object> product(String sku, String salePrice, String taxRate) {
        return Map.of("sku", sku, "name", "Product " + sku, "salePrice", salePrice, "costPrice", "1.00", "taxRate", taxRate,
                "reorderLevel", 5);
    }

    static Map<String, Object> warehouse(String code) {
        return Map.of("code", code, "name", "Warehouse " + code);
    }

    static Map<String, Object> party(String name) {
        return Map.of("name", name, "email", name.toLowerCase().replace(' ', '.') + "@example.com");
    }

    static Map<String, Object> line(String productId, int quantity) {
        return Map.of("productId", productId, "quantity", quantity);
    }

    static Map<String, Object> salesOrder(String warehouseId, List<Map<String, Object>> lines) {
        return Map.of("warehouseId", warehouseId, "lines", lines);
    }

    static Map<String, Object> adjustment(String productId, String warehouseId, int quantity) {
        return Map.of("productId", productId, "warehouseId", warehouseId, "quantity", quantity, "note", "test");
    }

    record Ids(Session session, String productId, String warehouseId) {
        UUID product() {
            return UUID.fromString(productId);
        }
    }
}
