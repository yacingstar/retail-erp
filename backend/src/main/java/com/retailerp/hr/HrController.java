package com.retailerp.hr;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retailerp.common.PageResponse;
import com.retailerp.hr.HrDtos.EmployeePayslip;
import com.retailerp.hr.HrDtos.EmployeeRequest;
import com.retailerp.hr.HrDtos.EmployeeResponse;
import com.retailerp.hr.HrDtos.PayPreview;
import com.retailerp.hr.HrDtos.PayRequest;
import com.retailerp.hr.HrDtos.PayrollRunRequest;
import com.retailerp.hr.HrDtos.PayrollRunResponse;
import com.retailerp.hr.HrDtos.PayrollRunSummary;
import com.retailerp.hr.HrDtos.PayslipRequest;
import com.retailerp.security.Access;
import com.retailerp.subscription.Module;
import com.retailerp.subscription.RequiresModule;

import jakarta.validation.Valid;

/** Employees and payroll: salaries are sensitive, so admins and managers only. */
@RestController
@RequestMapping("/api")
@RequiresModule(Module.HR_PAYROLL)
@PreAuthorize(Access.MANAGE)
public class HrController {

    private final EmployeeService employees;
    private final PayrollService payroll;

    public HrController(EmployeeService employees, PayrollService payroll) {
        this.employees = employees;
        this.payroll = payroll;
    }

    @GetMapping("/employees")
    public PageResponse<EmployeeResponse> employees(@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 50, sort = "fullName") Pageable pageable) {
        return employees.search(active, q, pageable);
    }

    @GetMapping("/employees/{id}")
    public EmployeeResponse employee(@PathVariable UUID id) {
        return employees.get(id);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return employees.create(request);
    }

    @PutMapping("/employees/{id}")
    public EmployeeResponse updateEmployee(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
        return employees.update(id, request);
    }

    @GetMapping("/employees/{id}/payslips")
    public List<EmployeePayslip> employeePayslips(@PathVariable UUID id) {
        return employees.payslips(id);
    }

    @GetMapping("/payroll/preview")
    public PayPreview preview(@RequestParam BigDecimal baseSalary, @RequestParam(defaultValue = "0") BigDecimal bonus,
            @RequestParam(defaultValue = "0") BigDecimal allowance) {
        return EmployeeService.preview(baseSalary.max(BigDecimal.ZERO), bonus.max(BigDecimal.ZERO), allowance.max(BigDecimal.ZERO));
    }

    @GetMapping("/payroll-runs")
    public PageResponse<PayrollRunSummary> runs(
            @PageableDefault(size = 24, sort = {"periodYear", "periodMonth"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return payroll.list(pageable);
    }

    @GetMapping("/payroll-runs/{id}")
    public PayrollRunResponse run(@PathVariable UUID id) {
        return payroll.get(id);
    }

    @PostMapping("/payroll-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollRunResponse createRun(@Valid @RequestBody PayrollRunRequest request) {
        return payroll.create(request);
    }

    @PutMapping("/payroll-runs/{id}/payslips/{payslipId}")
    public PayrollRunResponse editPayslip(@PathVariable UUID id, @PathVariable UUID payslipId, @Valid @RequestBody PayslipRequest request) {
        return payroll.editPayslip(id, payslipId, request);
    }

    @DeleteMapping("/payroll-runs/{id}/payslips/{payslipId}")
    public PayrollRunResponse removePayslip(@PathVariable UUID id, @PathVariable UUID payslipId) {
        return payroll.removePayslip(id, payslipId);
    }

    @DeleteMapping("/payroll-runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRun(@PathVariable UUID id) {
        payroll.delete(id);
    }

    @PostMapping("/payroll-runs/{id}/approve")
    public PayrollRunResponse approve(@PathVariable UUID id) {
        return payroll.approve(id);
    }

    @PostMapping("/payroll-runs/{id}/pay")
    public PayrollRunResponse pay(@PathVariable UUID id, @Valid @RequestBody PayRequest request) {
        return payroll.pay(id, request);
    }
}
