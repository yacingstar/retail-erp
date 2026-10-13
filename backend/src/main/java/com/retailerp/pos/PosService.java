package com.retailerp.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.catalog.CatalogService;
import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.Money;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.invoicing.InvoiceService;
import com.retailerp.invoicing.InvoicingDtos.PaymentRequest;
import com.retailerp.invoicing.PaymentMethod;
import com.retailerp.pos.PosDtos.CheckoutRequest;
import com.retailerp.pos.PosDtos.CloseShiftRequest;
import com.retailerp.pos.PosDtos.OpenShiftRequest;
import com.retailerp.pos.PosDtos.Receipt;
import com.retailerp.pos.PosDtos.ReceiptLine;
import com.retailerp.pos.PosDtos.SalesTotals;
import com.retailerp.pos.PosDtos.ShiftResponse;
import com.retailerp.sales.SalesDtos.SalesOrderRequest;
import com.retailerp.sales.SalesOrder;
import com.retailerp.sales.SalesService;
import com.retailerp.security.CurrentUser;
import com.retailerp.tenancy.TenantContext;
import com.retailerp.tenant.TenantRepository;
import com.retailerp.user.User;
import com.retailerp.user.UserRepository;

/**
 * Point of Sale = a fast path over the existing modules: a checkout creates a sales order, confirms it (stock +
 * invoice) and records the payment, all in ONE transaction, tagged with the cashier's open shift.
 */
@Service
@Transactional
public class PosService {

    private final PosShiftRepository shifts;
    private final CatalogService catalog;
    private final SalesService sales;
    private final InvoiceService invoices;
    private final TenantRepository tenants;
    private final UserRepository users;

    public PosService(PosShiftRepository shifts, CatalogService catalog, SalesService sales, InvoiceService invoices, TenantRepository tenants,
            UserRepository users) {
        this.users = users;
        this.shifts = shifts;
        this.catalog = catalog;
        this.sales = sales;
        this.invoices = invoices;
        this.tenants = tenants;
    }

    // --- shifts ------------------------------------------------------------------------------------------------------

    public ShiftResponse open(OpenShiftRequest request) {
        if (shifts.findFirstByOpenedByAndStatus(CurrentUser.id(), ShiftStatus.OPEN).isPresent()) {
            throw new BusinessRuleException("You already have an open shift. Close it first.");
        }
        PosShift shift = new PosShift(catalog.activeWarehouse(request.warehouseId()), CurrentUser.id(), Money.round(request.openingCash()));
        try {
            shifts.saveAndFlush(shift); // the partial unique index also guards against two tabs opening at once
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("You already have an open shift. Close it first.");
        }
        return toResponse(shift);
    }

    @Transactional(readOnly = true)
    public Optional<ShiftResponse> current() {
        return shifts.findFirstByOpenedByAndStatus(CurrentUser.id(), ShiftStatus.OPEN).map(this::toResponse);
    }

    /** The cashier closes their own shift; managers may close anyone's (e.g. someone forgot). */
    public ShiftResponse close(UUID id, CloseShiftRequest request) {
        PosShift shift = shift(id);
        if (!shift.getOpenedBy().equals(CurrentUser.id()) && !CurrentUser.isManager()) {
            throw new AccessDeniedException("Only the cashier or a manager can close this shift.");
        }
        BigDecimal expected = shift.getOpeningCash().add(shifts.cashTaken(id));
        shift.close(CurrentUser.id(), expected, Money.round(request.countedCash()), request.notes());
        shifts.flush();
        return toResponse(shift);
    }

    @Transactional(readOnly = true)
    public ShiftResponse get(UUID id) {
        return toResponse(shift(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShiftResponse> list(Pageable pageable) {
        return PageResponse.of(shifts.findPage(pageable), this::toResponse);
    }

    private PosShift shift(UUID id) {
        return shifts.findById(id).orElseThrow(() -> new NotFoundException("Shift", id));
    }

    private ShiftResponse toResponse(PosShift s) {
        SalesTotals totals = shifts.salesTotals(s.getId());
        BigDecimal expected = s.getStatus() == ShiftStatus.OPEN ? s.getOpeningCash().add(shifts.cashTaken(s.getId())) : s.getExpectedCash();
        BigDecimal difference = s.getCountedCash() == null ? null : s.getCountedCash().subtract(expected);
        return new ShiftResponse(s.getId(), s.getStatus(), s.getWarehouse().getId(), s.getWarehouse().getCode(), s.getWarehouse().getName(),
                s.getOpenedBy(), users.findById(s.getOpenedBy()).map(User::getFullName).orElse(null), s.getOpenedAt(), s.getOpeningCash(), s.getClosedBy(), s.getClosedAt(), totals.count(), totals.total(),
                shifts.paymentsByMethod(s.getId()), expected, s.getCountedCash(), difference, s.getNotes());
    }

    // --- checkout ----------------------------------------------------------------------------------------------------

    public Receipt checkout(CheckoutRequest request) {
        PosShift shift = shifts.findFirstByOpenedByAndStatus(CurrentUser.id(), ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessRuleException("Open a shift before selling."));
        if (request.paymentMethod() != PaymentMethod.CASH && request.paymentMethod() != PaymentMethod.CARD) {
            throw new IllegalArgumentException("At the till, payment is CASH or CARD.");
        }

        SalesOrder order = sales.createConfirmed(new SalesOrderRequest(request.customerId(), shift.getWarehouse().getId(), null, null,
                request.discountPercent(), request.lines()), shift.getId());
        BigDecimal total = order.getTotal();

        BigDecimal cashReceived = null;
        BigDecimal change = BigDecimal.ZERO;
        if (request.paymentMethod() == PaymentMethod.CASH) {
            cashReceived = request.cashReceived() == null ? total : Money.round(request.cashReceived());
            if (cashReceived.compareTo(total) < 0) {
                // Throwing rolls back the whole transaction: no order, no stock movement, no invoice.
                throw new BusinessRuleException("Cash received " + cashReceived + " is less than the total " + total + ".");
            }
            change = cashReceived.subtract(total);
        }

        var invoice = invoices.findFor(order.getId()).orElseThrow();
        if (total.signum() > 0) {
            invoices.recordPayment(invoice.getId(), new PaymentRequest(total, request.paymentMethod(), null, null));
        }

        List<ReceiptLine> lines = order.getLines().stream()
                .map(l -> new ReceiptLine(l.getProduct().getName(), l.getQuantity(), l.getUnitPrice(), l.getLineSubtotal())).toList();
        String shopName = tenants.findById(TenantContext.get()).map(t -> t.getName()).orElse("");
        return new Receipt(order.getId(), order.getNumber(), invoice.getId(), invoice.getNumber(), shopName, shift.getWarehouse().getName(),
                CurrentUser.name(), order.getCustomer() == null ? null : order.getCustomer().getName(), order.getConfirmedAt(), lines,
                order.getSubtotal(), order.getDiscountTotal(), order.getTaxTotal(), total, request.paymentMethod(), cashReceived, change);
    }
}
