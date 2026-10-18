package com.retailerp.hr;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.retailerp.common.BusinessRuleException;
import com.retailerp.common.NotFoundException;
import com.retailerp.common.PageResponse;
import com.retailerp.hr.HrDtos.EmployeePayslip;
import com.retailerp.hr.HrDtos.EmployeeRequest;
import com.retailerp.hr.HrDtos.EmployeeResponse;
import com.retailerp.hr.HrDtos.PayPreview;
import com.retailerp.hr.PayrollRules.Calculation;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employees;
    private final PayrollRunRepository runs;

    public EmployeeService(EmployeeRepository employees, PayrollRunRepository runs) {
        this.employees = employees;
        this.runs = runs;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(Boolean active, String q, Pageable pageable) {
        return PageResponse.of(employees.search(active, q.trim(), pageable), EmployeeService::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        return toResponse(find(id));
    }

    public EmployeeResponse create(EmployeeRequest request) {
        String nss = blankToNull(request.nss());
        if (nss != null && employees.existsByNss(nss)) {
            throw new BusinessRuleException("Another employee already has social security number " + nss + ".");
        }
        Employee employee = new Employee(request.fullName().trim());
        apply(employee, request);
        return toResponse(employees.save(employee));
    }

    public EmployeeResponse update(UUID id, EmployeeRequest request) {
        Employee employee = find(id);
        String nss = blankToNull(request.nss());
        if (nss != null && employees.existsByNssAndIdNot(nss, id)) {
            throw new BusinessRuleException("Another employee already has social security number " + nss + ".");
        }
        apply(employee, request);
        return toResponse(employee);
    }

    @Transactional(readOnly = true)
    public List<EmployeePayslip> payslips(UUID id) {
        find(id);
        return runs.payslipsOf(id).stream().map(p -> new EmployeePayslip(p.getRun().getId(), p.getRun().getNumber(),
                p.getRun().getPeriod().getYear(), p.getRun().getPeriod().getMonthValue(), p.getRun().getStatus(), p.getGross(), p.getNetPay()))
                .toList();
    }

    /** Pure calculation for the live preview in the form: nothing is saved. */
    public static PayPreview preview(BigDecimal baseSalary, BigDecimal bonus, BigDecimal allowance) {
        Calculation c = PayrollRules.calculate(baseSalary, bonus, allowance);
        return new PayPreview(c.gross(), c.cnasEmployee(), c.taxableIncome(), c.irg(), c.netPay(), c.cnasEmployer(),
                c.gross().add(c.cnasEmployer()));
    }

    private void apply(Employee employee, EmployeeRequest r) {
        if (r.endDate() != null && r.endDate().isBefore(r.hireDate())) {
            throw new BusinessRuleException("The end date can't be before the hire date.");
        }
        employee.update(r.fullName().trim(), blankToNull(r.jobTitle()), r.contractType(), r.hireDate(), r.endDate(), blankToNull(r.nin()),
                blankToNull(r.nss()), r.baseSalary(), r.monthlyAllowance() == null ? BigDecimal.ZERO : r.monthlyAllowance(),
                blankToNull(r.phone()), blankToNull(r.bankAccount()), blankToNull(r.notes()), r.active() == null || r.active());
    }

    private Employee find(UUID id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Employee", id));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static EmployeeResponse toResponse(Employee e) {
        return new EmployeeResponse(e.getId(), e.getFullName(), e.getJobTitle(), e.getContractType(), e.getHireDate(), e.getEndDate(),
                e.getNin(), e.getNss(), e.getBaseSalary(), e.getMonthlyAllowance(), e.getPhone(), e.getBankAccount(), e.getNotes(),
                e.isActive(), e.getCreatedAt());
    }
}
