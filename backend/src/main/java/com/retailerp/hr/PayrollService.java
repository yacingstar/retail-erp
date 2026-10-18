package com.retailerp.hr;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.hr.HrDtos.PayRequest;
import com.retailerp.hr.HrDtos.PayrollRunRequest;
import com.retailerp.hr.HrDtos.PayrollRunResponse;
import com.retailerp.hr.HrDtos.PayrollRunSummary;
import com.retailerp.hr.HrDtos.PayslipRequest;
import com.retailerp.hr.HrDtos.PayslipResponse;
import com.retailerp.numbering.DocumentNumberService;
import com.retailerp.numbering.DocumentNumberService.DocumentType;
import com.retailerp.security.CurrentUser;

/**
 * Monthly payroll: create a draft run for a month (a payslip per employee working that month), adjust payslips,
 * approve (locks it; accounting records the salaries owed), then pay. Approved runs are never edited.
 */
@Service
@Transactional
public class PayrollService {

    private final PayrollRunRepository runs;
    private final EmployeeRepository employees;
    private final DocumentNumberService numbers;
    private final ApplicationEventPublisher events;

    public PayrollService(PayrollRunRepository runs, EmployeeRepository employees, DocumentNumberService numbers,
            ApplicationEventPublisher events) {
        this.runs = runs;
        this.employees = employees;
        this.numbers = numbers;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollRunSummary> list(Pageable pageable) {
        return PageResponse.of(runs.page(pageable), r -> new PayrollRunSummary(r.getId(), r.getNumber(), r.getPeriod().getYear(),
                r.getPeriod().getMonthValue(), r.getStatus(), r.getPayslips().size(), r.getTotalGross(), r.getTotalNet(),
                r.getTotalCnasEmployer(), r.getApprovedAt(), r.getPaidAt()));
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse get(UUID id) {
        return toResponse(runs.findWithPayslips(id).orElseThrow(() -> new NotFoundException("Payroll run", id)));
    }

    public PayrollRunResponse create(PayrollRunRequest request) {
        YearMonth period = YearMonth.of(request.year(), request.month());
        if (runs.existsByPeriodYearAndPeriodMonth(period.getYear(), period.getMonthValue())) {
            throw new BusinessRuleException("There is already a payroll for " + period + ".");
        }
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth();
        PayrollRun run = new PayrollRun(numbers.next(DocumentType.PAYROLL), period, CurrentUser.id());
        employees.findByActiveTrueOrderByFullName().stream().filter(e -> e.worksDuring(start, end)).forEach(run::addPayslipFor);
        if (run.getPayslips().isEmpty()) {
            throw new BusinessRuleException("No active employee works in " + period + ". Add employees first.");
        }
        return toResponse(runs.save(run));
    }

    public PayrollRunResponse editPayslip(UUID runId, UUID payslipId, PayslipRequest request) {
        PayrollRun run = lock(runId);
        run.editPayslip(payslip(run, payslipId), request.baseSalary(), request.bonus(), request.allowance());
        return toResponse(run);
    }

    public PayrollRunResponse removePayslip(UUID runId, UUID payslipId) {
        PayrollRun run = lock(runId);
        run.removePayslip(payslip(run, payslipId));
        return toResponse(run);
    }

    /** Only drafts: once approved, the salaries are in the books. */
    public void delete(UUID runId) {
        PayrollRun run = lock(runId);
        run.requireDraft("deleted");
        runs.delete(run);
    }

    public PayrollRunResponse approve(UUID runId) {
        PayrollRun run = lock(runId);
        run.approve();
        events.publishEvent(new PayrollApproved(run.getId()));
        return toResponse(run);
    }

    public PayrollRunResponse pay(UUID runId, PayRequest request) {
        PayrollRun run = lock(runId);
        run.markPaid(request.method());
        events.publishEvent(new PayrollPaid(run.getId()));
        return toResponse(run);
    }

    private PayrollRun lock(UUID id) {
        return runs.findForUpdate(id).orElseThrow(() -> new NotFoundException("Payroll run", id));
    }

    private static Payslip payslip(PayrollRun run, UUID payslipId) {
        return run.getPayslips().stream().filter(p -> p.getId().equals(payslipId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Payslip", payslipId));
    }

    static PayrollRunResponse toResponse(PayrollRun r) {
        return new PayrollRunResponse(r.getId(), r.getNumber(), r.getPeriod().getYear(), r.getPeriod().getMonthValue(), r.getPeriodEnd(),
                r.getStatus(), r.getPaymentMethod(), r.getTotalGross(), r.getTotalCnasEmployee(), r.getTotalIrg(), r.getTotalNet(),
                r.getTotalCnasEmployer(), r.getTotalGross().add(r.getTotalCnasEmployer()), r.getApprovedAt(), r.getPaidAt(),
                r.getPayslips().stream().map(PayrollService::toResponse).toList());
    }

    static PayslipResponse toResponse(Payslip p) {
        return new PayslipResponse(p.getId(), p.getEmployeeId(), p.getEmployeeName(), p.getJobTitle(), p.getNss(), p.getBaseSalary(),
                p.getBonus(), p.getAllowance(), p.getGross(), p.getCnasBase(), p.getCnasEmployee(), p.getTaxableIncome(), p.getIrg(),
                p.getNetPay(), p.getCnasEmployer());
    }
}
