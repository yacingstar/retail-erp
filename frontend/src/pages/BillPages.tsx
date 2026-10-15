import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Bill, BillStatus, BillSummary, Page, Party, SupplierPaymentMethod, SupplierStatement } from '../api/types'
import { date, dateTime, label, money } from '../components/format'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

const STATUSES: BillStatus[] = ['DRAFT', 'OPEN', 'PARTIALLY_PAID', 'PAID', 'VOID']
const METHODS: SupplierPaymentMethod[] = ['BANK_TRANSFER', 'CASH', 'CHEQUE', 'CARD', 'OTHER']

const invalidateBills = (queryClient: ReturnType<typeof useQueryClient>) => {
  for (const key of ['bills', 'statement', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
}

export function BillsPage() {
  const [params] = useSearchParams()
  const [q, setQ] = useState('')
  const [status, setStatus] = useState(params.get('status') ?? '')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const bills = useQuery({
    queryKey: ['bills', status, search, page],
    queryFn: () => api.get<Page<BillSummary>>(`/api/bills${query({ status, q: search, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Supplier bills" actions={<Link className="btn btn-primary" to="/bills/new">New bill</Link>} />
      <p className="muted">What you owe suppliers. Bills for received purchase orders are created automatically as drafts: check them against the supplier's paper bill, then approve.</p>
      <div className="toolbar">
        <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Bill number, supplier or their reference…" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
        </select>
      </div>
      <ErrorBox error={bills.error} />
      {bills.isLoading ? <Loading /> : bills.data?.content.length === 0 ? <Empty>No bills.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Bill</th><th>Supplier</th><th>Their ref.</th><th>Date</th><th>Due</th><th>Status</th><th className="num">Total</th><th className="num">Owed</th></tr></thead>
            <tbody>
              {bills.data?.content.map((b) => (
                <tr key={b.id}>
                  <td><Link to={`/bills/${b.id}`}>{b.number}</Link>{b.purchaseOrderNumber && <small className="muted"> · {b.purchaseOrderNumber}</small>}</td>
                  <td><Link to={`/suppliers/${b.supplierId}/statement`}>{b.supplierName}</Link></td><td>{b.supplierReference}</td>
                  <td>{date(b.billDate)}</td><td>{date(b.dueDate)}</td><td><Badge value={b.status} /></td>
                  <td className="num">{money(b.total)}</td><td className="num">{b.status === 'DRAFT' || b.status === 'VOID' ? '—' : money(b.balanceDue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={bills.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  )
}

interface EditableLine { description: string; quantity: string; unitPrice: string }

export function BillFormPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['bills', id], queryFn: () => api.get<Bill>(`/api/bills/${id}`), enabled: !!id })
  if (id && existing.isLoading) return <Loading />
  if (id && !existing.data) return <ErrorBox error={existing.error} />
  return <BillForm bill={existing.data ?? null} />
}

function BillForm({ bill }: { bill: Bill | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const suppliers = useQuery({ queryKey: ['suppliers', 'all'], queryFn: () => api.get<Page<Party>>('/api/suppliers?size=1000') })
  const [supplierId, setSupplierId] = useState(bill?.supplierId ?? '')
  const [reference, setReference] = useState(bill?.supplierReference ?? '')
  const [billDate, setBillDate] = useState(bill?.billDate ?? new Date().toISOString().slice(0, 10))
  const [dueDate, setDueDate] = useState(bill?.dueDate ?? '')
  const [description, setDescription] = useState(bill?.description ?? '')
  const [lines, setLines] = useState<EditableLine[]>(bill?.lines.map((l) => ({ description: l.description, quantity: String(l.quantity), unitPrice: String(l.unitPrice) }))
    ?? [{ description: '', quantity: '1', unitPrice: '' }])
  const total = lines.reduce((sum, l) => sum + (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0), 0)
  const update = (i: number, patch: Partial<EditableLine>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)))

  const save = useMutation({
    mutationFn: () => {
      const body = {
        supplierId, supplierReference: reference || null, billDate, dueDate: dueDate || null, description: description || null,
        lines: lines.map((l) => ({ description: l.description, quantity: Number(l.quantity), unitPrice: Number(l.unitPrice) })),
      }
      return bill ? api.put<Bill>(`/api/bills/${bill.id}`, body) : api.post<Bill>('/api/bills', body)
    },
    onSuccess: (saved) => { invalidateBills(queryClient); navigate(`/bills/${saved.id}`) },
  })

  return (
    <>
      <PageHeader title={bill ? `Edit ${bill.number}` : 'New supplier bill'} />
      <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Supplier" error={fieldError(save.error, 'supplierId')}>
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} required>
              <option value="">Select…</option>
              {suppliers.data?.content.filter((s) => s.active || s.id === supplierId).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Supplier's bill number"><input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="As printed on their bill" /></Field>
          <Field label="Bill date"><input type="date" value={billDate} onChange={(e) => setBillDate(e.target.value)} required /></Field>
          <Field label="Due date (optional)"><input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} /></Field>
        </div>
        <table className="table">
          <thead><tr><th>Description</th><th style={{ width: 110 }}>Qty</th><th style={{ width: 140 }}>Unit price</th><th className="num">Line</th><th /></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td><input value={l.description} onChange={(e) => update(i, { description: e.target.value })} placeholder="e.g. Rent, October" required /></td>
                <td><input type="number" min="0.001" step="0.001" value={l.quantity} onChange={(e) => update(i, { quantity: e.target.value })} required /></td>
                <td><input type="number" min="0" step="0.01" value={l.unitPrice} onChange={(e) => update(i, { unitPrice: e.target.value })} required /></td>
                <td className="num">{money((Number(l.quantity) || 0) * (Number(l.unitPrice) || 0))}</td>
                <td><button type="button" className="btn-icon" onClick={() => setLines(lines.filter((_, j) => j !== i))} aria-label="Remove line">×</button></td>
              </tr>
            ))}
          </tbody>
          <tfoot><tr className="total-row"><td colSpan={3}>Total</td><td className="num">{money(total)}</td><td /></tr></tfoot>
        </table>
        <button type="button" className="btn btn-small" onClick={() => setLines([...lines, { description: '', quantity: '1', unitPrice: '' }])}>+ Add line</button>
        <Field label="Notes"><textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
        <div className="modal-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending || lines.length === 0}>Save draft</button>
        </div>
      </form>
    </>
  )
}

export function BillDetailPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const bill = useQuery({ queryKey: ['bills', id], queryFn: () => api.get<Bill>(`/api/bills/${id}`) })
  const action = useMutation({
    mutationFn: (name: 'approve' | 'void') => api.post<Bill>(`/api/bills/${id}/${name}`),
    onSuccess: (b) => { queryClient.setQueryData(['bills', id], b); invalidateBills(queryClient) },
  })
  if (bill.isLoading) return <Loading />
  if (!bill.data) return <ErrorBox error={bill.error} />
  const b = bill.data
  const payable = b.status === 'OPEN' || b.status === 'PARTIALLY_PAID'

  return (
    <>
      <PageHeader title={`Supplier bill ${b.number}`} actions={<>
        {b.status === 'DRAFT' && <>
          <Link className="btn" to={`/bills/${b.id}/edit`}>Edit</Link>
          <button className="btn btn-primary" onClick={() => action.mutate('approve')}>Approve</button>
        </>}
        {(b.status === 'DRAFT' || b.status === 'OPEN') && <button className="btn btn-danger" onClick={() => confirm('Void this bill?') && action.mutate('void')}>Void</button>}
      </>} />
      <ErrorBox error={action.error} />
      {b.status === 'DRAFT' && <div className="trial-banner">Draft: not counted as owed yet. Check the lines and total against the supplier's paper bill, then approve.</div>}
      <div className="card details">
        <div><small>Status</small><Badge value={b.status} /></div>
        <div><small>Supplier</small><Link to={`/suppliers/${b.supplierId}/statement`}>{b.supplierName}</Link></div>
        <div><small>Their bill number</small>{b.supplierReference ?? '—'}</div>
        <div><small>Bill date</small>{date(b.billDate)}</div>
        <div><small>Due</small>{date(b.dueDate)}</div>
        {b.purchaseOrderId && <div><small>Purchase order</small><Link to={`/purchase-orders/${b.purchaseOrderId}`}>{b.purchaseOrderNumber}</Link></div>}
      </div>
      <div className="card">
        <table className="table">
          <thead><tr><th>#</th><th>Description</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Total</th></tr></thead>
          <tbody>{b.lines.map((l) => <tr key={l.lineNo}><td>{l.lineNo}</td><td>{l.description}</td><td className="num">{l.quantity}</td><td className="num">{money(l.unitPrice)}</td><td className="num">{money(l.lineTotal)}</td></tr>)}</tbody>
          <tfoot>
            <tr><td colSpan={4} className="num"><strong>Total</strong></td><td className="num"><strong>{money(b.total)}</strong></td></tr>
            <tr><td colSpan={4} className="num">Paid</td><td className="num">{money(b.amountPaid)}</td></tr>
            <tr><td colSpan={4} className="num"><strong>Still owed</strong></td><td className="num"><strong>{money(b.balanceDue)}</strong></td></tr>
          </tfoot>
        </table>
        {b.description && <p className="muted">{b.description}</p>}
      </div>
      <div className="grid-2">
        <div className="card">
          <h2>Payments</h2>
          {b.payments.length === 0 ? <Empty>No payments yet.</Empty> : (
            <table className="table">
              <thead><tr><th>When</th><th>Method</th><th>Reference</th><th className="num">Amount</th></tr></thead>
              <tbody>{b.payments.map((p) => <tr key={p.id}><td>{dateTime(p.paidAt)}</td><td>{label(p.method)}</td><td>{p.reference}</td><td className="num">{money(p.amount)}</td></tr>)}</tbody>
            </table>
          )}
        </div>
        {payable && <PayBill bill={b} />}
      </div>
    </>
  )
}

function PayBill({ bill }: { bill: Bill }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(String(bill.balanceDue))
  const [method, setMethod] = useState<SupplierPaymentMethod>('BANK_TRANSFER')
  const [reference, setReference] = useState('')
  const pay = useMutation({
    mutationFn: () => api.post<Bill>(`/api/bills/${bill.id}/payments`, { amount: Number(amount), method, reference: reference || null }),
    onSuccess: (b) => {
      queryClient.setQueryData(['bills', bill.id], b)
      invalidateBills(queryClient)
      setAmount(String(b.balanceDue))
      setReference('')
    },
  })
  return (
    <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); pay.mutate() }}>
      <h2>Pay the supplier</h2>
      <ErrorBox error={pay.error} />
      <div className="form-grid">
        <Field label="Amount"><input type="number" step="0.01" min="0.01" max={bill.balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} required /></Field>
        <Field label="Method">
          <select value={method} onChange={(e) => setMethod(e.target.value as SupplierPaymentMethod)}>{METHODS.map((m) => <option key={m} value={m}>{label(m)}</option>)}</select>
        </Field>
      </div>
      <Field label="Reference"><input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="Transfer / cheque number" /></Field>
      <button className="btn btn-primary" disabled={pay.isPending}>Record payment</button>
    </form>
  )
}

export function SupplierStatementPage() {
  const { id } = useParams()
  const statement = useQuery({ queryKey: ['statement', id], queryFn: () => api.get<SupplierStatement>(`/api/suppliers/${id}/statement`) })
  if (statement.isLoading) return <Loading />
  if (!statement.data) return <ErrorBox error={statement.error} />
  const s = statement.data
  return (
    <>
      <PageHeader title={`Statement · ${s.supplierName}`} actions={<>
        <Link className="btn" to={`/bills/new`}>New bill</Link>
        <button className="btn" onClick={() => window.print()}>Print</button>
      </>} />
      <div className="stats">
        <div className="card stat"><small>Total billed</small><strong>{money(s.totalBilled)}</strong></div>
        <div className="card stat"><small>Total paid</small><strong>{money(s.totalPaid)}</strong></div>
        <div className="card stat"><small>We owe</small><strong className={s.balance > 0 ? 'warn' : ''}>{money(s.balance)}</strong></div>
      </div>
      <div className="card">
        {s.entries.length === 0 ? <Empty>No approved bills or payments yet.</Empty> : (
          <table className="table">
            <thead><tr><th>Date</th><th>Type</th><th>Bill</th><th>Reference</th><th className="num">Amount</th><th className="num">Balance</th></tr></thead>
            <tbody>
              {s.entries.map((e, i) => (
                <tr key={i}>
                  <td>{date(e.date)}</td><td>{e.type === 'BILL' ? 'Bill' : 'Payment'}</td>
                  <td><Link to={`/bills/${e.billId}`}>{e.document}</Link></td><td>{e.reference}</td>
                  <td className={`num ${e.amount < 0 ? 'pos' : ''}`}>{money(e.amount)}</td><td className="num">{money(e.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted small-note">Only approved bills count. Drafts and voided bills are not included.</p>
      </div>
    </>
  )
}
