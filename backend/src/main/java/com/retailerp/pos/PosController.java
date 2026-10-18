package com.retailerp.pos;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.common.PageResponse;
import com.retailerp.pos.PosDtos.CheckoutRequest;
import com.retailerp.pos.PosDtos.CloseShiftRequest;
import com.retailerp.pos.PosDtos.OpenShiftRequest;
import com.retailerp.pos.PosDtos.Receipt;
import com.retailerp.pos.PosDtos.ShiftResponse;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/pos")
@RequiresModule(Module.POINT_OF_SALE)
@PreAuthorize(Access.ANY)
public class PosController {

    private final PosService pos;

    public PosController(PosService pos) {
        this.pos = pos;
    }

    @PostMapping("/shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftResponse open(@Valid @RequestBody OpenShiftRequest request) {
        return pos.open(request);
    }

    /** 200 with the open shift, or 204 No Content when the cashier has none. */
    @GetMapping("/shifts/current")
    public ResponseEntity<ShiftResponse> current() {
        return pos.current().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/shifts/{id}/close")
    public ShiftResponse close(@PathVariable UUID id, @Valid @RequestBody CloseShiftRequest request) {
        return pos.close(id, request);
    }

    @GetMapping("/shifts")
    @PreAuthorize(Access.MANAGE)
    public PageResponse<ShiftResponse> list(@PageableDefault(size = 20, sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return pos.list(pageable);
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize(Access.MANAGE)
    public ShiftResponse get(@PathVariable UUID id) {
        return pos.get(id);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt checkout(@Valid @RequestBody CheckoutRequest request) {
        return pos.checkout(request);
    }
}
