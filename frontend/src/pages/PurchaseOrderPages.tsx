import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Page, Party, PurchaseOrder, PurchaseOrderStatus, PurchaseOrderSummary, Warehouse } from '../api/types'
import { date, dateTime, money } from '../components/format'
import { LinesEditor, type EditableLine } from '../components/LinesEditor'
import { Badge, Empty, ErrorBox, Field, Loading, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'
import { MovementsTable } from './StockPage'

const STATUSES: PurchaseOrderStatus[] = ['DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED']

export function PurchaseOrdersPage() {
  const [q, setQ] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const search = useDebounced(q)
  const orders = useQuery({
    queryKey: ['purchase-orders', status, search, page],
    queryFn: () => api.get<Page<PurchaseOrderSummary>>(`/api/purchase-orders${query({ status, q: search, page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Purchase orders" actions={<Link className="btn btn-primary" to="/purchase-orders/new">New purchase order</Link>} />
      <div className="toolbar">
        <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Number or supplier…" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <ErrorBox error={orders.error} />
      {orders.isLoading ? <Loading /> : orders.data?.content.length === 0 ? <Empty>No purchase orders.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Number</th><th>Supplier</th><th>Warehouse</th><th>Order date</th><th>Expected</th><th>Status</th><th className="num">Total</th></tr></thead>
            <tbody>
              {orders.data?.content.map((o) => (
                <tr key={o.id}>
                  <td><Link to={`/purchase-orders/${o.id}`}>{o.number}</Link></td><td>{o.supplierName}</td><td>{o.warehouseCode}</td>
                  <td>{date(o.orderDate)}</td><td>{date(o.expectedDate)}</td><td><Badge value={o.status} /></td><td className="num">{money(o.total)}</td>
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

export function PurchaseOrderFormPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['purchase-orders', id], queryFn: () => api.get<PurchaseOrder>(`/api/purchase-orders/${id}`), enabled: !!id })
  if (id && existing.isLoading) return <Loading />
  if (id && !existing.data) return <ErrorBox error={existing.error} />
  return <PurchaseOrderForm order={existing.data ?? null} />
}

function PurchaseOrderForm({ order }: { order: PurchaseOrder | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const suppliers = useQuery({ queryKey: ['suppliers', 'all'], queryFn: () => api.get<Page<Party>>('/api/suppliers?size=1000') })
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })
  const [supplierId, setSupplierId] = useState(order?.supplierId ?? '')
  const [warehouseId, setWarehouseId] = useState(order?.warehouseId ?? '')
  const [expectedDate, setExpectedDate] = useState(order?.expectedDate ?? '')
  const [notes, setNotes] = useState(order?.notes ?? '')
  const [lines, setLines] = useState<EditableLine[]>(order?.lines.map((l) => ({ productId: l.productId, quantity: l.quantity, price: String(l.unitCost) })) ?? [{ productId: '', quantity: 1, price: '' }])

  const save = useMutation({
    mutationFn: () => {
      const body = {
        supplierId, warehouseId, expectedDate: expectedDate || null, notes,
        lines: lines.map((l) => ({ productId: l.productId, quantity: l.quantity, unitCost: l.price === '' ? null : Number(l.price) })),
      }
      return order ? api.put<PurchaseOrder>(`/api/purchase-orders/${order.id}`, body) : api.post<PurchaseOrder>('/api/purchase-orders', body)
    },
    onSuccess: (saved) => { queryClient.invalidateQueries({ queryKey: ['purchase-orders'] }); navigate(`/purchase-orders/${saved.id}`) },
  })

  return (
    <>
      <PageHeader title={order ? `Edit ${order.number}` : 'New purchase order'} />
      <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Supplier">
            <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} required>
              <option value="">Select…</option>
              {suppliers.data?.content.filter((s) => s.active || s.id === supplierId).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Deliver to warehouse">
            <select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} required>
              <option value="">Select…</option>
              {warehouses.data?.filter((w) => w.active).map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
            </select>
          </Field>
          <Field label="Expected date"><input type="date" value={expectedDate} onChange={(e) => setExpectedDate(e.target.value)} /></Field>
        </div>
        <LinesEditor lines={lines} onChange={setLines} priceLabel="Unit cost" defaultPrice={(p) => p.costPrice} />
        <Field label="Notes"><textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        <div className="modal-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending || lines.length === 0}>Save draft</button>
        </div>
      </form>
    </>
  )
}

export function PurchaseOrderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const order = useQuery({ queryKey: ['purchase-orders', id], queryFn: () => api.get<PurchaseOrder>(`/api/purchase-orders/${id}`) })
  const action = useMutation({
    mutationFn: (name: 'order' | 'receive' | 'cancel') => api.post<PurchaseOrder>(`/api/purchase-orders/${id}/${name}`),
    onSuccess: (updated) => {
      queryClient.setQueryData(['purchase-orders', id], updated)
      queryClient.invalidateQueries({ queryKey: ['purchase-orders'] })
      queryClient.invalidateQueries({ queryKey: ['stock'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const remove = useMutation({ mutationFn: () => api.delete(`/api/purchase-orders/${id}`), onSuccess: () => navigate('/purchase-orders') })

  if (order.isLoading) return <Loading />
  if (!order.data) return <ErrorBox error={order.error} />
  const po = order.data

  return (
    <>
      <PageHeader title={`Purchase order ${po.number}`} actions={<>
        {po.status === 'DRAFT' && <>
          <button className="btn btn-danger" onClick={() => confirm('Delete this draft?') && remove.mutate()}>Delete</button>
          <Link className="btn" to={`/purchase-orders/${po.id}/edit`}>Edit</Link>
          <button className="btn btn-primary" onClick={() => action.mutate('order')}>Mark as ordered</button>
        </>}
        {po.status === 'ORDERED' && <button className="btn btn-primary" onClick={() => confirm('Receive all goods into stock?') && action.mutate('receive')}>Receive goods</button>}
        {(po.status === 'DRAFT' || po.status === 'ORDERED') && <button className="btn" onClick={() => confirm('Cancel this purchase order?') && action.mutate('cancel')}>Cancel order</button>}
      </>} />
      <ErrorBox error={action.error ?? remove.error} />
      <div className="card details">
        <div><small>Status</small><Badge value={po.status} /></div>
        <div><small>Supplier</small>{po.supplierName}</div>
        <div><small>Warehouse</small>{po.warehouseCode} · {po.warehouseName}</div>
        <div><small>Order date</small>{date(po.orderDate)}</div>
        <div><small>Expected</small>{date(po.expectedDate)}</div>
        <div><small>Received</small>{dateTime(po.receivedAt)}</div>
      </div>
      <div className="card">
        <table className="table">
          <thead><tr><th>#</th><th>Product</th><th className="num">Qty</th><th className="num">Unit cost</th><th className="num">Total</th></tr></thead>
          <tbody>
            {po.lines.map((l) => <tr key={l.lineNo}><td>{l.lineNo}</td><td>{l.sku} · {l.productName}</td><td className="num">{l.quantity}</td><td className="num">{money(l.unitCost)}</td><td className="num">{money(l.lineTotal)}</td></tr>)}
          </tbody>
          <tfoot><tr><td colSpan={4} className="num"><strong>Total</strong></td><td className="num"><strong>{money(po.total)}</strong></td></tr></tfoot>
        </table>
        {po.notes && <p className="muted">{po.notes}</p>}
      </div>
      {po.movements.length > 0 && <div className="card"><h2>Stock movements</h2><MovementsTable movements={po.movements} /></div>}
    </>
  )
}
