import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Page, PaymentMethod, Returnable, ReturnSummary, SalesOrderSummary, SalesReturn } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { date, dateTime, label, money } from '../components/format'
import { Empty, ErrorBox, Field, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

export function ReturnsPage() {
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const returns = useQuery({
    queryKey: ['returns', search, page],
    queryFn: () => api.get<Page<ReturnSummary>>(`/api/returns${query({ q: search, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Returns" actions={<Link className="btn btn-primary" to="/returns/new">New return</Link>} />
      <p className="muted">Each return creates a credit note. If the sale was paid, the customer is refunded in cash, by card or as store credit.</p>
      <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Credit note, sale number or customer…" />
      <ErrorBox error={returns.error} />
      {returns.isLoading ? <Loading /> : returns.data?.content.length === 0 ? <Empty>No returns yet.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Credit note</th><th>Date</th><th>Sale</th><th>Customer</th><th className="num">Credited</th><th className="num">Refunded</th><th>Refund method</th></tr></thead>
            <tbody>
              {returns.data?.content.map((r) => (
                <tr key={r.id}>
                  <td><Link to={`/returns/${r.id}`}>{r.number}</Link></td><td>{date(r.returnDate)}</td>
                  <td><Link to={`/sales-orders/${r.salesOrderId}`}>{r.salesOrderNumber}</Link></td><td>{r.customerName ?? 'Walk-in'}</td>
                  <td className="num">{money(r.total)}</td><td className="num">{money(r.refundAmount)}</td><td>{r.refundMethod ? label(r.refundMethod) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={returns.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  )
}

/** Step 1: find the sale (unless we came from a sale's page). Step 2: pick items and quantities. */
export function NewReturnPage() {
  const [params, setParams] = useSearchParams()
  const salesOrderId = params.get('salesOrderId')
  return (
    <>
      <PageHeader title="New return" />
      {salesOrderId ? <ReturnForm salesOrderId={salesOrderId} /> : <FindSale onPick={(id) => setParams({ salesOrderId: id })} />}
    </>
  )
}

function FindSale({ onPick }: { onPick: (id: string) => void }) {
  const [q, setQ] = useState('')
  const search = useDebounced(q)
  const sales = useQuery({
    queryKey: ['sales-orders', 'CONFIRMED', search, 0],
    queryFn: () => api.get<Page<SalesOrderSummary>>(`/api/sales-orders${query({ status: 'CONFIRMED', q: search, size: 10 })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <div className="card">
      <h2>Which sale is being returned?</h2>
      <SearchBox value={q} onChange={setQ} placeholder="Sale number (SO-…) or customer name" />
      <table className="table">
        <tbody>
          {sales.data?.content.map((s) => (
            <tr key={s.id} className="clickable" onClick={() => onPick(s.id)}>
              <td>{s.number}</td><td>{s.customerName ?? 'Walk-in'}</td><td>{date(s.orderDate)}</td><td className="num">{money(s.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {sales.data?.content.length === 0 && <Empty>No confirmed sale matches.</Empty>}
    </div>
  )
}

function ReturnForm({ salesOrderId }: { salesOrderId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const returnable = useQuery({ queryKey: ['returnable', salesOrderId], queryFn: () => api.get<Returnable>(`/api/returns/returnable/${salesOrderId}`) })
  const [qty, setQty] = useState<Record<string, number>>({})
  const [restock, setRestock] = useState<Record<string, boolean>>({})
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [reason, setReason] = useState('')

  const save = useMutation({
    mutationFn: () => api.post<SalesReturn>('/api/returns', {
      salesOrderId,
      lines: Object.entries(qty).filter(([, n]) => n > 0).map(([lineId, n]) => ({ salesOrderLineId: lineId, quantity: n, restock: restock[lineId] ?? true })),
      refundMethod: method,
      reason: reason || null,
    }),
    onSuccess: (r) => {
      for (const key of ['returns', 'returnable', 'stock', 'invoices', 'sales-orders', 'pos', 'store-credit', 'dashboard', 'reports']) {
        queryClient.invalidateQueries({ queryKey: [key] })
      }
      navigate(`/returns/${r.id}`)
    },
  })

  if (returnable.isLoading) return <Loading />
  if (!returnable.data) return <ErrorBox error={returnable.error} />
  const r = returnable.data
  const estimate = r.lines.reduce((sum, l) => sum + (qty[l.salesOrderLineId] ?? 0) * l.unitRefund, 0)
  const nothing = Object.values(qty).every((n) => !n)
  const paid = r.amountPaid > 0

  return (
    <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
      <div className="details">
        <div><small>Sale</small><Link to={`/sales-orders/${r.salesOrderId}`}>{r.salesOrderNumber}</Link></div>
        <div><small>Customer</small>{r.customerName ?? 'Walk-in'}</div>
        <div><small>Invoice</small>{r.invoiceNumber} · {money(r.invoiceTotal)}</div>
        <div><small>Paid</small>{money(r.amountPaid)}</div>
      </div>
      <ErrorBox error={save.error} />
      <table className="table">
        <thead><tr><th>Product</th><th className="num">Sold</th><th className="num">Already returned</th><th style={{ width: 110 }}>Return</th><th>Back to stock?</th><th className="num">Refund (approx.)</th></tr></thead>
        <tbody>
          {r.lines.map((l) => (
            <tr key={l.salesOrderLineId}>
              <td>{l.sku} · {l.productName}</td><td className="num">{l.sold}</td><td className="num">{l.returned}</td>
              <td>
                <input type="number" min={0} max={l.returnable} disabled={l.returnable === 0} value={qty[l.salesOrderLineId] ?? 0}
                  onChange={(e) => setQty({ ...qty, [l.salesOrderLineId]: Math.min(l.returnable, Math.max(0, Number(e.target.value))) })} />
              </td>
              <td>
                <label className="checkbox inline">
                  <input type="checkbox" checked={restock[l.salesOrderLineId] ?? true} onChange={(e) => setRestock({ ...restock, [l.salesOrderLineId]: e.target.checked })} />
                  {(restock[l.salesOrderLineId] ?? true) ? 'Resellable' : 'Damaged (loss)'}
                </label>
              </td>
              <td className="num">{money((qty[l.salesOrderLineId] ?? 0) * l.unitRefund)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="form-grid">
        {paid ? (
          <Field label="Refund the customer by">
            <select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              <option value="CASH">Cash</option>
              <option value="CARD">Card</option>
              {r.customerId && <option value="STORE_CREDIT">Store credit</option>}
            </select>
          </Field>
        ) : <p className="muted">This invoice isn't paid yet: the return lowers what the customer owes; no money is given back.</p>}
        <Field label="Reason (optional)"><input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. wrong size, faulty" /></Field>
      </div>
      <div className="modal-actions">
        <strong>Credit ≈ {money(estimate)}</strong>
        <span className="spacer" />
        <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
        <button className="btn btn-primary" disabled={nothing || save.isPending}>Create return</button>
      </div>
    </form>
  )
}

export function ReturnDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const ret = useQuery({ queryKey: ['returns', id], queryFn: () => api.get<SalesReturn>(`/api/returns/${id}`) })
  if (ret.isLoading) return <Loading />
  if (!ret.data) return <ErrorBox error={ret.error} />
  const r = ret.data
  return (
    <>
      <PageHeader title={`Credit note ${r.number}`} actions={<button className="btn" onClick={() => window.print()}>Print</button>} />
      <div className="card invoice">
        <div className="invoice-head">
          <div><h2>{user?.tenantName}</h2><small className="muted">Credit note {r.number}</small></div>
          <div className="right"><div>{date(r.returnDate)}</div><div className="muted">Created {dateTime(r.createdAt)}</div></div>
        </div>
        <div className="bill-to">
          <small>Customer</small>
          <div>{r.customerName ?? 'Walk-in customer'}</div>
          <div className="muted">For sale <Link to={`/sales-orders/${r.salesOrderId}`}>{r.salesOrderNumber}</Link> · invoice <Link to={`/invoices/${r.invoiceId}`}>{r.invoiceNumber}</Link></div>
          {r.reason && <div className="muted">Reason: {r.reason}</div>}
        </div>
        <table className="table">
          <thead><tr><th>Product</th><th className="num">Qty</th><th>Stock</th><th className="num">Amount</th></tr></thead>
          <tbody>
            {r.lines.map((l, i) => (
              <tr key={i}><td>{l.sku} · {l.productName}</td><td className="num">{l.quantity}</td><td>{l.restock ? 'Back in stock' : 'Written off'}</td><td className="num">{money(l.lineSubtotal)}</td></tr>
            ))}
          </tbody>
          <tfoot>
            <tr><td colSpan={3} className="num">Subtotal</td><td className="num">{money(r.subtotal)}</td></tr>
            {r.discountTotal > 0 && <tr><td colSpan={3} className="num">Discount</td><td className="num">−{money(r.discountTotal)}</td></tr>}
            <tr><td colSpan={3} className="num">Tax</td><td className="num">{money(r.taxTotal)}</td></tr>
            <tr><td colSpan={3} className="num"><strong>Total credited</strong></td><td className="num"><strong>{money(r.total)}</strong></td></tr>
            <tr><td colSpan={3} className="num">Refunded{r.refundMethod ? ` (${label(r.refundMethod)})` : ''}</td><td className="num">{money(r.refundAmount)}</td></tr>
          </tfoot>
        </table>
      </div>
    </>
  )
}

/** Shown on a sale's page: its returns, and a button to make one. */
export function SaleReturns({ salesOrderId, canReturn }: { salesOrderId: string; canReturn: boolean }) {
  const returns = useQuery({
    queryKey: ['returns', 'by-sale', salesOrderId],
    queryFn: () => api.get<ReturnSummary[]>(`/api/returns/by-sales-order/${salesOrderId}`),
  })
  if (!returns.data) return null
  if (returns.data.length === 0 && !canReturn) return null
  return (
    <div className="card">
      <div className="page-header">
        <h2>Returns</h2>
        {canReturn && <Link className="btn" to={`/returns/new?salesOrderId=${salesOrderId}`}>Return items</Link>}
      </div>
      {returns.data.length === 0 ? <p className="muted">No returns for this sale.</p> : (
        <table className="table">
          <tbody>
            {returns.data.map((r) => (
              <tr key={r.id}>
                <td><Link to={`/returns/${r.id}`}>{r.number}</Link></td><td>{date(r.returnDate)}</td>
                <td className="num">{money(r.total)}</td><td>{r.refundMethod ? `Refunded ${money(r.refundAmount)} (${label(r.refundMethod)})` : 'Credited'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
