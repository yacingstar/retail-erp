import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Invoice, InvoiceStatus, InvoiceSummary, Page, PaymentMethod } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { date, dateTime, label, money } from '../components/format'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

const STATUSES: InvoiceStatus[] = ['ISSUED', 'PARTIALLY_PAID', 'PAID', 'VOID']
const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'BANK_TRANSFER', 'OTHER']

export function InvoicesPage() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const invoices = useQuery({
    queryKey: ['invoices', status, search, page],
    queryFn: () => api.get<Page<InvoiceSummary>>(`/api/invoices${query({ status, q: search, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Invoices" />
      <p className="muted">Invoices are issued automatically when a sale is confirmed.</p>
      <div className="toolbar">
        <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Number or customer…" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{label(s)}</option>)}
        </select>
      </div>
      <ErrorBox error={invoices.error} />
      {invoices.isLoading ? <Loading /> : invoices.data?.content.length === 0 ? <Empty>No invoices.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Number</th><th>Customer</th><th>Issued</th><th>Due</th><th>Status</th><th className="num">Total</th><th className="num">Balance</th></tr></thead>
            <tbody>
              {invoices.data?.content.map((i) => (
                <tr key={i.id}>
                  <td><Link to={`/invoices/${i.id}`}>{i.number}</Link></td><td>{i.customerName ?? 'Walk-in'}</td>
                  <td>{date(i.issueDate)}</td><td>{date(i.dueDate)}</td><td><Badge value={i.status} /></td>
                  <td className="num">{money(i.total)}</td><td className="num">{money(i.balanceDue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={invoices.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  )
}

export function InvoiceDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const invoice = useQuery({ queryKey: ['invoices', id], queryFn: () => api.get<Invoice>(`/api/invoices/${id}`) })
  if (invoice.isLoading) return <Loading />
  if (!invoice.data) return <ErrorBox error={invoice.error} />
  const inv = invoice.data
  const payable = inv.status === 'ISSUED' || inv.status === 'PARTIALLY_PAID'

  return (
    <>
      <PageHeader title={`Invoice ${inv.number}`} actions={<button className="btn" onClick={() => window.print()}>Print</button>} />
      <div className="card invoice">
        <div className="invoice-head">
          <div><h2>{user?.tenantName}</h2><small className="muted">Invoice {inv.number}</small></div>
          <div className="right"><Badge value={inv.status} /><div>Issued {date(inv.issueDate)}</div><div>Due {date(inv.dueDate)}</div></div>
        </div>
        <div className="bill-to">
          <small>Bill to</small>
          <div>{inv.customerName ?? 'Walk-in customer'}</div>
          {inv.customerEmail && <div className="muted">{inv.customerEmail}</div>}
          {inv.customerAddress && <div className="muted pre">{inv.customerAddress}</div>}
          <div className="muted">Order <Link to={`/sales-orders/${inv.salesOrderId}`}>{inv.salesOrderNumber}</Link></div>
        </div>
        <table className="table">
          <thead><tr><th>Product</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Tax %</th><th className="num">Total</th></tr></thead>
          <tbody>
            {inv.lines.map((l) => <tr key={l.lineNo}><td>{l.sku} · {l.productName}</td><td className="num">{l.quantity}</td><td className="num">{money(l.unitPrice)}</td><td className="num">{l.taxRate}</td><td className="num">{money(l.lineTotal)}</td></tr>)}
          </tbody>
          <tfoot>
            <tr><td colSpan={4} className="num">Subtotal</td><td className="num">{money(inv.subtotal)}</td></tr>
            {inv.discountTotal > 0 && <tr><td colSpan={4} className="num">Discount</td><td className="num">−{money(inv.discountTotal)}</td></tr>}
            <tr><td colSpan={4} className="num">Tax</td><td className="num">{money(inv.taxTotal)}</td></tr>
            <tr><td colSpan={4} className="num"><strong>Total</strong></td><td className="num"><strong>{money(inv.total)}</strong></td></tr>
            <tr><td colSpan={4} className="num">Paid</td><td className="num">{money(inv.amountPaid)}</td></tr>
            <tr><td colSpan={4} className="num"><strong>Balance due</strong></td><td className="num"><strong>{money(inv.balanceDue)}</strong></td></tr>
          </tfoot>
        </table>
      </div>
      <div className="grid-2 no-print">
        <div className="card">
          <h2>Payments</h2>
          {inv.payments.length === 0 ? <Empty>No payments yet.</Empty> : (
            <table className="table">
              <thead><tr><th>When</th><th>Method</th><th>Reference</th><th className="num">Amount</th></tr></thead>
              <tbody>{inv.payments.map((p) => <tr key={p.id}><td>{dateTime(p.paidAt)}</td><td>{label(p.method)}</td><td>{p.reference}</td><td className="num">{money(p.amount)}</td></tr>)}</tbody>
            </table>
          )}
        </div>
        {payable && <PaymentForm invoice={inv} />}
      </div>
    </>
  )
}

function PaymentForm({ invoice }: { invoice: Invoice }) {
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(String(invoice.balanceDue))
  const [method, setMethod] = useState<PaymentMethod>('CASH')
  const [reference, setReference] = useState('')
  const pay = useMutation({
    mutationFn: () => api.post<Invoice>(`/api/invoices/${invoice.id}/payments`, { amount: Number(amount), method, reference: reference || null }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['invoices', invoice.id], updated)
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setAmount(String(updated.balanceDue))
      setReference('')
    },
  })
  return (
    <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); pay.mutate() }}>
      <h2>Record payment</h2>
      <ErrorBox error={pay.error} />
      <div className="form-grid">
        <Field label="Amount" error={fieldError(pay.error, 'amount')}><input type="number" step="0.01" min="0.01" max={invoice.balanceDue} value={amount} onChange={(e) => setAmount(e.target.value)} required /></Field>
        <Field label="Method">
          <select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>{METHODS.map((m) => <option key={m} value={m}>{label(m)}</option>)}</select>
        </Field>
      </div>
      <Field label="Reference"><input value={reference} onChange={(e) => setReference(e.target.value)} placeholder="Receipt / transaction no." /></Field>
      <button className="btn btn-primary" disabled={pay.isPending}>Record payment</button>
    </form>
  )
}
