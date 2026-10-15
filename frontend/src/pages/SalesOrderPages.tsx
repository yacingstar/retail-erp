import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Page, Party, SalesOrder, SalesOrderStatus, SalesOrderSummary, Warehouse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { date, dateTime, money } from '../components/format'
import { LinesEditor, type EditableLine } from '../components/LinesEditor'
import { Badge, Empty, ErrorBox, Field, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'
import { MovementsTable } from './StockPage'
import { SaleReturns } from './ReturnPages'
import { useSubscription } from '../auth/useSubscription'

const STATUSES: SalesOrderStatus[] = ['DRAFT', 'CONFIRMED', 'CANCELLED']

export function SalesOrdersPage() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const orders = useQuery({
    queryKey: ['sales-orders', status, search, page],
    queryFn: () => api.get<Page<SalesOrderSummary>>(`/api/sales-orders${query({ status, q: search, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Sales" actions={<Link className="btn btn-primary" to="/sales-orders/new">New sale</Link>} />
      <div className="toolbar">
        <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Number or customer…" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <ErrorBox error={orders.error} />
      {orders.isLoading ? <Loading /> : orders.data?.content.length === 0 ? <Empty>No sales orders.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Number</th><th>Customer</th><th>Warehouse</th><th>Date</th><th>Status</th><th className="num">Total</th></tr></thead>
            <tbody>
              {orders.data?.content.map((o) => (
                <tr key={o.id}>
                  <td><Link to={`/sales-orders/${o.id}`}>{o.number}</Link></td><td>{o.customerName ?? 'Walk-in'}</td><td>{o.warehouseCode}</td>
                  <td>{date(o.orderDate)}</td><td><Badge value={o.status} /></td><td className="num">{money(o.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={orders.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  )
}

export function SalesOrderFormPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['sales-orders', id], queryFn: () => api.get<SalesOrder>(`/api/sales-orders/${id}`), enabled: !!id })
  if (id && existing.isLoading) return <Loading />
  if (id && !existing.data) return <ErrorBox error={existing.error} />
  return <SalesOrderForm order={existing.data ?? null} />
}

function SalesOrderForm({ order }: { order: SalesOrder | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const customers = useQuery({ queryKey: ['customers', 'all'], queryFn: () => api.get<Page<Party>>('/api/customers?size=1000') })
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })
  const activeWarehouses = warehouses.data?.filter((w) => w.active) ?? []
  const [customerId, setCustomerId] = useState(order?.customerId ?? '')
  const [warehouseId, setWarehouseId] = useState(order?.warehouseId ?? '')
  const [notes, setNotes] = useState(order?.notes ?? '')
  const [lines, setLines] = useState<EditableLine[]>(order?.lines.map((l) => ({ productId: l.productId, quantity: l.quantity, price: String(l.unitPrice) })) ?? [{ productId: '', quantity: 1, price: '' }])
  const effectiveWarehouse = warehouseId || (activeWarehouses.length === 1 ? activeWarehouses[0].id : '')

  const save = useMutation({
    mutationFn: async (confirmNow: boolean) => {
      const body = {
        customerId: customerId || null, warehouseId: effectiveWarehouse, notes,
        lines: lines.map((l) => ({ productId: l.productId, quantity: l.quantity, unitPrice: l.price === '' ? null : Number(l.price) })),
      }
      const saved = order ? await api.put<SalesOrder>(`/api/sales-orders/${order.id}`, body) : await api.post<SalesOrder>('/api/sales-orders', body)
      if (!confirmNow) return saved
      try {
        return await api.post<SalesOrder>(`/api/sales-orders/${saved.id}/confirm`)
      } catch (e) {
        // Saved as draft but couldn't confirm (e.g. not enough stock): go to the draft and show why.
        navigate(`/sales-orders/${saved.id}`, { state: { error: (e as Error).message } })
        throw e
      }
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['sales-orders'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['stock'] })
      navigate(`/sales-orders/${saved.id}`)
    },
  })

  return (
    <>
      <PageHeader title={order ? `Edit ${order.number}` : 'New sale'} />
      <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate(true) }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Customer">
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
              <option value="">Walk-in customer</option>
              {customers.data?.content.filter((c) => c.active || c.id === customerId).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="From warehouse">
            <select value={effectiveWarehouse} onChange={(e) => setWarehouseId(e.target.value)} required>
              <option value="">Select…</option>
              {activeWarehouses.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
            </select>
          </Field>
        </div>
        <LinesEditor lines={lines} onChange={setLines} priceLabel="Unit price" defaultPrice={(p) => p.salePrice} />
        <p className="muted">Tax is added per product when the order is saved.</p>
        <Field label="Notes"><textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        <div className="modal-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
          <button type="button" className="btn" disabled={save.isPending || lines.length === 0} onClick={() => save.mutate(false)}>Save draft</button>
          <button className="btn btn-primary" disabled={save.isPending || lines.length === 0}>Confirm sale</button>
        </div>
      </form>
    </>
  )
}

export function SalesOrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { hasRole } = useAuth()
  const subscription = useSubscription()
  const location = useLocation()
  const initialError = (location.state as { error?: string } | null)?.error ?? null
  const order = useQuery({ queryKey: ['sales-orders', id], queryFn: () => api.get<SalesOrder>(`/api/sales-orders/${id}`) })
  const action = useMutation({
    mutationFn: (name: 'confirm' | 'cancel') => api.post<SalesOrder>(`/api/sales-orders/${id}/${name}`),
    onSuccess: (updated) => {
      queryClient.setQueryData(['sales-orders', id], updated)
      for (const key of ['sales-orders', 'invoices', 'stock', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })
  const remove = useMutation({ mutationFn: () => api.delete(`/api/sales-orders/${id}`), onSuccess: () => navigate('/sales-orders') })

  if (order.isLoading) return <Loading />
  if (!order.data) return <ErrorBox error={order.error} />
  const so = order.data
  const canCancel = so.status === 'DRAFT' || (so.status === 'CONFIRMED' && hasRole('ADMIN', 'MANAGER'))

  return (
    <>
      <PageHeader title={`Sale ${so.number}`} actions={<>
        {so.status === 'DRAFT' && <>
          <button className="btn btn-danger" onClick={() => confirm('Delete this draft?') && remove.mutate()}>Delete</button>
          <Link className="btn" to={`/sales-orders/${so.id}/edit`}>Edit</Link>
          <button className="btn btn-primary" onClick={() => action.mutate('confirm')}>Confirm sale</button>
        </>}
        {so.invoiceId && <Link className="btn" to={`/invoices/${so.invoiceId}`}>Invoice {so.invoiceNumber}</Link>}
        {canCancel && <button className="btn" onClick={() => confirm(so.status === 'CONFIRMED' ? 'Cancel this sale? Stock is returned and the invoice voided.' : 'Cancel this draft?') && action.mutate('cancel')}>Cancel sale</button>}
      </>} />
      <ErrorBox error={action.error ?? remove.error ?? (action.isIdle ? initialError : null)} />
      <div className="card details">
        <div><small>Status</small><Badge value={so.status} /></div>
        <div><small>Customer</small>{so.customerName ?? 'Walk-in'}</div>
        <div><small>Warehouse</small>{so.warehouseCode} · {so.warehouseName}</div>
        <div><small>Date</small>{date(so.orderDate)}</div>
        <div><small>Confirmed</small>{dateTime(so.confirmedAt)}</div>
      </div>
      <div className="card">
        <table className="table">
          <thead><tr><th>#</th><th>Product</th><th className="num">Qty</th><th className="num">Unit price</th><th className="num">Tax %</th><th className="num">Total</th></tr></thead>
          <tbody>
            {so.lines.map((l) => <tr key={l.lineNo}><td>{l.lineNo}</td><td>{l.sku} · {l.productName}</td><td className="num">{l.quantity}</td><td className="num">{money(l.unitPrice)}</td><td className="num">{l.taxRate}</td><td className="num">{money(l.lineTotal)}</td></tr>)}
          </tbody>
          <tfoot>
            <tr><td colSpan={5} className="num">Subtotal</td><td className="num">{money(so.subtotal)}</td></tr>
            {so.discountTotal > 0 && <tr><td colSpan={5} className="num">Discount ({so.discountPercent}%)</td><td className="num">−{money(so.discountTotal)}</td></tr>}
            <tr><td colSpan={5} className="num">Tax</td><td className="num">{money(so.taxTotal)}</td></tr>
            <tr><td colSpan={5} className="num"><strong>Total</strong></td><td className="num"><strong>{money(so.total)}</strong></td></tr>
          </tfoot>
        </table>
        {so.notes && <p className="muted">{so.notes}</p>}
      </div>
      {so.status === 'CONFIRMED' && subscription.has('RETURNS') && <SaleReturns salesOrderId={so.id} canReturn />}
      {so.movements.length > 0 && <div className="card"><h2>Stock movements</h2><MovementsTable movements={so.movements} /></div>}
    </>
  )
}
