package com.retailerp.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.common.PageResponse;
import com.retailerp.inventory.StockDtos.AdjustmentRequest;
import com.retailerp.inventory.StockDtos.LowStock;
import com.retailerp.inventory.StockDtos.MovementResponse;
import com.retailerp.inventory.StockDtos.StockLevel;
import com.retailerp.inventory.StockDtos.TransferRequest;
import com.retailerp.security.Access;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stock;

    public StockController(StockService stock) {
        this.stock = stock;
    }

    @GetMapping("/levels")
    @PreAuthorize(Access.ANY)
    public List<StockLevel> levels(@RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) String q) {
        return stock.levels(warehouseId, q);
    }

    @GetMapping("/levels/product/{productId}")
    @PreAuthorize(Access.ANY)
    public List<StockLevel> levelsOfProduct(@PathVariable UUID productId) {
        return stock.levelsOfProduct(productId);
    }

    @GetMapping("/low")
    @PreAuthorize(Access.ANY)
    public List<LowStock> lowStock() {
        return stock.lowStock();
    }

    @GetMapping("/movements")
    @PreAuthorize(Access.ANY)
    public PageResponse<MovementResponse> movements(@RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId, @RequestParam(required = false) MovementType type,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stock.movements(productId, warehouseId, type, pageable);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public MovementResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        return stock.adjust(request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Access.MANAGE)
    public List<MovementResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return stock.transfer(request);
    }
}
