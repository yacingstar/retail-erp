import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { ContractType, Employee, EmployeePayslip, Page, PayPreview, PayrollRun, PayrollRunSummary, Payslip, SalaryPaymentMethod } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { date, money } from '../components/format'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

const CONTRACTS: Record<ContractType, string> = { CDI: 'CDI (permanent)', CDD: 'CDD (fixed-term)', OTHER: 'Other' }

const monthName = (year: number, month: number) =>
  new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })

const invalidatePayroll = (queryClient: ReturnType<typeof useQueryClient>) => {
  for (const key of ['payroll', 'employees', 'accounting']) queryClient.invalidateQueries({ queryKey: [key] })
}

export function EmployeesPage() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [active, setActive] = useState('true')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const employees = useQuery({
    queryKey: ['employees', search, active, page],
    queryFn: () => api.get<Page<Employee>>(`/api/employees${query({ q: search, active: active || undefined, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Employees" actions={<Link className="btn btn-primary" to="/employees/new">New employee</Link>} />
      <div className="toolbar">
        <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Name or job…" />
        <select value={active} onChange={(e) => { setActive(e.target.value); setPage(0) }} aria-label="Status">
          <option value="true">Active</option>
          <option value="false">Inactive</option>
          <option value="">All</option>
        </select>
      </div>
      <ErrorBox error={employees.error} />
      {employees.isLoading ? <Loading /> : !employees.data || employees.data.content.length === 0 ? <Empty>No employees yet.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Name</th><th>Job</th><th>Contract</th><th>Hired</th><th className="num">Base salary</th><th>Status</th></tr></thead>
            <tbody>
              {employees.data.content.map((e) => (
                <tr key={e.id} className="clickable" onClick={() => navigate(`/employees/${e.id}`)}>
                  <td>{e.fullName}</td><td>{e.jobTitle}</td><td>{e.contractType}</td><td>{date(e.hireDate)}</td>
                  <td className="num">{money(e.baseSalary)}</td><td><Badge value={e.active} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={employees.data.totalPages} onChange={setPage} />
        </div>
      )}
    </>
  )
}

export function EmployeeFormPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['employees', 'one', id], queryFn: () => api.get<Employee>(`/api/employees/${id}`), enabled: !!id })
  if (id && existing.isLoading) return <Loading />
  if (id && !existing.data) return <ErrorBox error={existing.error} />
  return <EmployeeForm key={id ?? 'new'} employee={existing.data ?? null} />
}

function EmployeeForm({ employee }: { employee: Employee | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    fullName: employee?.fullName ?? '', jobTitle: employee?.jobTitle ?? '', contractType: employee?.contractType ?? 'CDI' as ContractType,
    hireDate: employee?.hireDate ?? new Date().toISOString().slice(0, 10), endDate: employee?.endDate ?? '',
    nin: employee?.nin ?? '', nss: employee?.nss ?? '', baseSalary: employee ? String(employee.baseSalary) : '',
    monthlyAllowance: employee ? String(employee.monthlyAllowance) : '0', phone: employee?.phone ?? '', bankAccount: employee?.bankAccount ?? '',
    notes: employee?.notes ?? '', active: employee?.active ?? true,
  })
  const set = (patch: Partial<typeof form>) => setForm({ ...form, ...patch })
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, endDate: form.endDate || null, baseSalary: Number(form.baseSalary), monthlyAllowance: Number(form.monthlyAllowance) || 0 }
      return employee ? api.put<Employee>(`/api/employees/${employee.id}`, body) : api.post<Employee>('/api/employees', body)
    },
    onSuccess: () => { invalidatePayroll(queryClient); navigate('/employees') },
  })

  return (
    <>
      <PageHeader title={employee ? employee.fullName : 'New employee'} />
      <div className="grid-2">
        <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
          <ErrorBox error={save.error} />
          <div className="form-grid">
            <Field label="Full name" error={fieldError(save.error, 'fullName')}><input value={form.fullName} onChange={(e) => set({ fullName: e.target.value })} required /></Field>
            <Field label="Job title"><input value={form.jobTitle} onChange={(e) => set({ jobTitle: e.target.value })} placeholder="e.g. Vendeur, Caissière" /></Field>
            <Field label="Contract">
              <select value={form.contractType} onChange={(e) => set({ contractType: e.target.value as ContractType })}>
                {(Object.keys(CONTRACTS) as ContractType[]).map((c) => <option key={c} value={c}>{CONTRACTS[c]}</option>)}
              </select>
            </Field>
            <Field label="Hire date"><input type="date" value={form.hireDate} onChange={(e) => set({ hireDate: e.target.value })} required /></Field>
            <Field label="End date (optional)"><input type="date" value={form.endDate} onChange={(e) => set({ endDate: e.target.value })} /></Field>
            <Field label="Base salary (monthly gross)" error={fieldError(save.error, 'baseSalary')}>
              <input type="number" min="0" step="0.01" value={form.baseSalary} onChange={(e) => set({ baseSalary: e.target.value })} required />
            </Field>
            <Field label="Panier + transport (monthly)"><input type="number" min="0" step="0.01" value={form.monthlyAllowance} onChange={(e) => set({ monthlyAllowance: e.target.value })} /></Field>
            <Field label="NSS (social security no.)" error={fieldError(save.error, 'nss')}><input value={form.nss} onChange={(e) => set({ nss: e.target.value })} /></Field>
            <Field label="NIN (national ID no.)"><input value={form.nin} onChange={(e) => set({ nin: e.target.value })} /></Field>
            <Field label="Phone"><input value={form.phone} onChange={(e) => set({ phone: e.target.value })} /></Field>
            <Field label="RIB / CCP"><input value={form.bankAccount} onChange={(e) => set({ bankAccount: e.target.value })} /></Field>
          </div>
          <Field label="Notes"><textarea rows={2} value={form.notes} onChange={(e) => set({ notes: e.target.value })} /></Field>
          <label className="checkbox"><input type="checkbox" checked={form.active} onChange={(e) => set({ active: e.target.checked })} /> Active (included in new payrolls)</label>
          <div className="modal-actions">
            <span className="spacer" />
            <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
            <button className="btn btn-primary" disabled={save.isPending}>Save</button>
          </div>
        </form>
        <div>
          <PayPreviewCard baseSalary={form.baseSalary} allowance={form.monthlyAllowance} />
          {employee && <EmployeePayslips employeeId={employee.id} />}
        </div>
      </div>
    </>
  )
}

/** Live "what will they take home" box: the server computes it with the same rules as the payroll. */
function PayPreviewCard({ baseSalary, allowance }: { baseSalary: string; allowance: string }) {
  // Debounce the two numbers (not an object: a new object every render would never settle).
  const base = useDebounced(Number(baseSalary) || 0)
  const allow = useDebounced(Number(allowance) || 0)
  const params = { baseSalary: base, allowance: allow }
  const preview = useQuery({
    queryKey: ['payroll', 'preview', params],
    queryFn: () => api.get<PayPreview>(`/api/payroll/preview${query(params)}`),
    placeholderData: keepPreviousData,
  })
  const p = preview.data
  return (
    <div className="card">
      <h2>Monthly pay</h2>
      {!p ? <Loading /> : (
        <table className="table">
          <tbody>
            <tr><td>Gross</td><td className="num">{money(p.gross)}</td></tr>
            <tr><td>− CNAS (9%)</td><td className="num">{money(p.cnasEmployee)}</td></tr>
            <tr><td>− IRG</td><td className="num">{money(p.irg)}</td></tr>
            <tr className="total-row"><td>Net pay</td><td className="num">{money(p.netPay)}</td></tr>
            <tr><td className="muted">Employer CNAS (26%)</td><td className="num muted">{money(p.cnasEmployer)}</td></tr>
            <tr><td className="muted">Total cost to the shop</td><td className="num muted">{money(p.employerCost)}</td></tr>
          </tbody>
        </table>
      )}
      <p className="muted small-note">Without bonuses. Panier and transport aren't subject to CNAS but are taxed (IRG).</p>
    </div>
  )
}

function EmployeePayslips({ employeeId }: { employeeId: string }) {
  const slips = useQuery({ queryKey: ['employees', 'payslips', employeeId], queryFn: () => api.get<EmployeePayslip[]>(`/api/employees/${employeeId}/payslips`) })
  return (
    <div className="card">
      <h2>Payslips</h2>
      {slips.isLoading ? <Loading /> : !slips.data?.length ? <Empty>No payslips yet.</Empty> : (
        <table className="table">
          <thead><tr><th>Month</th><th>Status</th><th className="num">Gross</th><th className="num">Net</th></tr></thead>
          <tbody>
            {slips.data.map((s) => (
              <tr key={s.runId}>
                <td><Link to={`/payroll/${s.runId}`}>{monthName(s.year, s.month)}</Link></td><td><Badge value={s.status} /></td>
                <td className="num">{money(s.gross)}</td><td className="num">{money(s.netPay)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export function PayrollRunsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [month, setMonth] = useState(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
  const runs = useQuery({
    queryKey: ['payroll', 'runs', page],
    queryFn: () => api.get<Page<PayrollRunSummary>>(`/api/payroll-runs${query({ page })}`),
    placeholderData: keepPreviousData,
  })
  const create = useMutation({
    mutationFn: () => {
      const [year, m] = month.split('-').map(Number)
      return api.post<PayrollRun>('/api/payroll-runs', { year, month: m })
    },
    onSuccess: (run) => { invalidatePayroll(queryClient); navigate(`/payroll/${run.id}`) },
  })
  return (
    <>
      <PageHeader title="Payroll" />
      <form className="card toolbar" onSubmit={(e: FormEvent) => { e.preventDefault(); create.mutate() }}>
        <div className="actions">
          <label className="muted" htmlFor="payroll-month">Month</label>
          <input id="payroll-month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} required />
          <button className="btn btn-primary" disabled={create.isPending || !month}>Prepare payroll</button>
        </div>
        <span className="muted">Creates a draft payslip for every active employee working that month.</span>
      </form>
      <ErrorBox error={create.error ?? runs.error} />
      {runs.isLoading ? <Loading /> : !runs.data || runs.data.content.length === 0 ? <Empty>No payroll yet.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Month</th><th>Number</th><th>Status</th><th className="num">Employees</th><th className="num">Gross</th><th className="num">Net paid</th><th className="num">Employer CNAS</th></tr></thead>
            <tbody>
              {runs.data.content.map((r) => (
                <tr key={r.id} className="clickable" onClick={() => navigate(`/payroll/${r.id}`)}>
                  <td>{monthName(r.year, r.month)}</td><td>{r.number}</td><td><Badge value={r.status} /></td><td className="num">{r.employees}</td>
                  <td className="num">{money(r.totalGross)}</td><td className="num">{money(r.totalNet)}</td><td className="num">{money(r.totalCnasEmployer)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={runs.data.totalPages} onChange={setPage} />
        </div>
      )}
    </>
  )
}

export function PayrollRunPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [method, setMethod] = useState<SalaryPaymentMethod>('BANK_TRANSFER')
  const run = useQuery({ queryKey: ['payroll', 'run', id], queryFn: () => api.get<PayrollRun>(`/api/payroll-runs/${id}`) })
  const onSaved = (r: PayrollRun) => { queryClient.setQueryData(['payroll', 'run', id], r); invalidatePayroll(queryClient) }
  const approve = useMutation({ mutationFn: () => api.post<PayrollRun>(`/api/payroll-runs/${id}/approve`), onSuccess: onSaved })
  const pay = useMutation({ mutationFn: () => api.post<PayrollRun>(`/api/payroll-runs/${id}/pay`, { method }), onSuccess: onSaved })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/payroll-runs/${id}`),
    onSuccess: () => { invalidatePayroll(queryClient); navigate('/payroll') },
  })
  if (run.isLoading) return <Loading />
  if (!run.data) return <ErrorBox error={run.error} />
  const r = run.data
  const draft = r.status === 'DRAFT'

  return (
    <>
      <PageHeader title={`Payroll ${monthName(r.year, r.month)}`} actions={<>
        {draft && <button className="btn btn-danger" onClick={() => confirm('Delete this draft payroll?') && remove.mutate()}>Delete draft</button>}
        {draft && <button className="btn btn-primary" onClick={() => confirm('Approve? The payslips will be locked and the salaries recorded in the accounts.') && approve.mutate()}>Approve</button>}
      </>} />
      <ErrorBox error={approve.error ?? pay.error ?? remove.error} />
      {draft && <div className="trial-banner">Draft: adjust each payslip (a partial month, a bonus, other allowances), then approve.</div>}
      <div className="card details">
        <div><small>Number</small>{r.number}</div>
        <div><small>Status</small><Badge value={r.status} /></div>
        <div><small>Gross</small>{money(r.totalGross)}</div>
        <div><small>CNAS withheld (9%)</small>{money(r.totalCnasEmployee)}</div>
        <div><small>IRG withheld</small>{money(r.totalIrg)}</div>
        <div><small>Net to pay</small><strong>{money(r.totalNet)}</strong></div>
        <div><small>Employer CNAS (26%)</small>{money(r.totalCnasEmployer)}</div>
        <div><small>Total cost</small>{money(r.employerCost)}</div>
      </div>
      <div className="card">
        <table className="table">
          <thead><tr>
            <th>Employee</th><th className="num">Base</th><th className="num">Bonus</th><th className="num">Panier + transport</th>
            <th className="num">CNAS</th><th className="num">IRG</th><th className="num">Net</th><th />
          </tr></thead>
          <tbody>{r.payslips.map((p) => <PayslipRow key={p.id} run={r} payslip={p} onSaved={onSaved} />)}</tbody>
          <tfoot><tr className="total-row">
            <td>Total</td><td colSpan={3} className="num">{money(r.totalGross)}</td><td className="num">{money(r.totalCnasEmployee)}</td>
            <td className="num">{money(r.totalIrg)}</td><td className="num">{money(r.totalNet)}</td><td />
          </tr></tfoot>
        </table>
      </div>
      {r.status === 'APPROVED' && (
        <div className="card toolbar">
          <span>Salaries owed: <strong>{money(r.totalNet)}</strong></span>
          <div className="actions">
            <select value={method} onChange={(e) => setMethod(e.target.value as SalaryPaymentMethod)} aria-label="Payment method">
              <option value="BANK_TRANSFER">Bank transfer</option>
              <option value="CASH">Cash</option>
            </select>
            <button className="btn btn-primary" disabled={pay.isPending} onClick={() => pay.mutate()}>Mark salaries as paid</button>
          </div>
        </div>
      )}
      {r.status === 'PAID' && <p className="muted">Paid on {date(r.paidAt)} by {r.paymentMethod === 'CASH' ? 'cash' : 'bank transfer'}.</p>}
      {r.status !== 'DRAFT' && (
        <p className="muted small-note">
          The CNAS ({money(r.totalCnasEmployee + r.totalCnasEmployer)}) and IRG ({money(r.totalIrg)}) withheld are paid to the CNAS and the tax office
          separately: record those payments as journal entries (Dr 431 or 442 / Cr 512).
        </p>
      )}
    </>
  )
}

function PayslipRow({ run, payslip: p, onSaved }: { run: PayrollRun; payslip: Payslip; onSaved: (r: PayrollRun) => void }) {
  const [values, setValues] = useState({ baseSalary: String(p.baseSalary), bonus: String(p.bonus), allowance: String(p.allowance) })
  const changed = Number(values.baseSalary) !== p.baseSalary || Number(values.bonus) !== p.bonus || Number(values.allowance) !== p.allowance
  const save = useMutation({
    mutationFn: () => api.put<PayrollRun>(`/api/payroll-runs/${run.id}/payslips/${p.id}`,
      { baseSalary: Number(values.baseSalary) || 0, bonus: Number(values.bonus) || 0, allowance: Number(values.allowance) || 0 }),
    onSuccess: onSaved,
  })
  const remove = useMutation({ mutationFn: () => api.delete<PayrollRun>(`/api/payroll-runs/${run.id}/payslips/${p.id}`), onSuccess: onSaved })
  const draft = run.status === 'DRAFT'
  const input = (field: keyof typeof values, label: string) => draft
    ? <input type="number" min="0" step="0.01" value={values[field]} onChange={(e) => setValues({ ...values, [field]: e.target.value })} aria-label={`${label} ${p.employeeName}`} style={{ width: 110, textAlign: 'right' }} />
    : money(p[field])
  return (
    <tr>
      <td><Link to={`/employees/${p.employeeId}`}>{p.employeeName}</Link>{p.jobTitle && <div className="muted">{p.jobTitle}</div>}{save.error ? <ErrorBox error={save.error} /> : null}</td>
      <td className="num">{input('baseSalary', 'Base')}</td>
      <td className="num">{input('bonus', 'Bonus')}</td>
      <td className="num">{input('allowance', 'Allowance')}</td>
      <td className="num">{money(p.cnasEmployee)}</td>
      <td className="num">{money(p.irg)}</td>
      <td className="num"><strong>{money(p.netPay)}</strong></td>
      <td className="nowrap">
        {draft && changed && <button className="btn btn-small btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>Save</button>}
        <Link className="btn btn-small" to={`/payroll/${run.id}/payslips/${p.id}`}>Payslip</Link>
        {draft && <button className="btn-icon" onClick={() => confirm(`Remove ${p.employeeName} from this payroll?`) && remove.mutate()} aria-label={`Remove ${p.employeeName}`}>×</button>}
      </td>
    </tr>
  )
}

/** Bulletin de paie: one A4 page per employee, for printing or saving as PDF. */
export function PayslipPage() {
  const { id, payslipId } = useParams()
  const { user } = useAuth()
  const run = useQuery({ queryKey: ['payroll', 'run', id], queryFn: () => api.get<PayrollRun>(`/api/payroll-runs/${id}`) })
  if (run.isLoading) return <Loading />
  if (!run.data) return <ErrorBox error={run.error} />
  const r = run.data
  const p = r.payslips.find((s) => s.id === payslipId)
  if (!p) return <Empty>Payslip not found.</Empty>
  return (
    <>
      <PageHeader title={`Payslip · ${p.employeeName}`} actions={<>
        <Link className="btn" to={`/payroll/${r.id}`}>Back to payroll</Link>
        <button className="btn btn-primary" onClick={() => window.print()}>Print / PDF</button>
      </>} />
      <div className="card payslip">
        <div className="payslip-head">
          <div><h2>{user?.tenantName}</h2><small className="muted">Bulletin de paie · {monthName(r.year, r.month)}</small></div>
          <div className="right"><strong>{p.employeeName}</strong><div>{p.jobTitle}</div><div className="muted">NSS {p.nss ?? '—'}</div></div>
        </div>
        {r.status === 'DRAFT' && <div className="trial-banner">Draft: figures may still change.</div>}
        <table className="table">
          <thead><tr><th>Item</th><th className="num">Base</th><th className="num">Rate</th><th className="num">To pay</th><th className="num">Withheld</th></tr></thead>
          <tbody>
            <tr><td>Salaire de base</td><td /><td /><td className="num">{money(p.baseSalary)}</td><td /></tr>
            {p.bonus > 0 && <tr><td>Primes</td><td /><td /><td className="num">{money(p.bonus)}</td><td /></tr>}
            <tr><td>Retenue sécurité sociale (CNAS)</td><td className="num">{money(p.cnasBase)}</td><td className="num">9%</td><td /><td className="num">{money(p.cnasEmployee)}</td></tr>
            {p.allowance > 0 && <tr><td>Indemnités panier et transport</td><td /><td /><td className="num">{money(p.allowance)}</td><td /></tr>}
            <tr><td>IRG (impôt sur le revenu)</td><td className="num">{money(p.taxableIncome)}</td><td className="num">barème</td><td /><td className="num">{money(p.irg)}</td></tr>
          </tbody>
          <tfoot>
            <tr><td colSpan={3}>Totals</td><td className="num">{money(p.gross)}</td><td className="num">{money(p.cnasEmployee + p.irg)}</td></tr>
            <tr className="total-row"><td colSpan={4}>Net à payer</td><td className="num">{money(p.netPay)}</td></tr>
          </tfoot>
        </table>
        <p className="muted small-note">Employer contribution (CNAS 26%, paid by the employer, not deducted): {money(p.cnasEmployer)}. Period ending {date(r.periodEnd)} · {r.number}.</p>
      </div>
    </>
  )
}
