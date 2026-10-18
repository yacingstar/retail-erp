import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, query } from '../api/client'
import type { Movement, Page, Product, StockLevel, Warehouse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { dateTime, label } from '../components/format'
import { Empty, ErrorBox, Field, fieldError, Loading, Modal, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

export function StockPage() {
  const { hasRole } = useAuth()
  const canAdjust = hasRole('ADMIN', 'MANAGER')
  const [tab, setTab] = useState<'levels' | 'movements'>('levels')
  const [warehouseId, setWarehouseId] = useState('')
  const [dialog, setDialog] = useState<'adjust' | 'transfer' | null>(null)
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })

  return (
    <>
      <PageHeader title="Stock" actions={canAdjust && <>
        <button className="btn" onClick={() => setDialog('transfer')}>Transfer</button>
        <button className="btn btn-primary" onClick={() => setDialog('adjust')}>Adjust stock</button>
      </>} />
      <p className="muted">Stock is never edited directly: every change is a movement, and the level is the sum of all movements.</p>
      <div className="toolbar">
        <div className="tabs">
          <button className={tab === 'levels' ? 'active' : ''} onClick={() => setTab('levels')}>Levels</button>
          <button className={tab === 'movements' ? 'active' : ''} onClick={() => setTab('movements')}>Movements</button>
        </div>
        <select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
          <option value="">All warehouses</option>
          {warehouses.data?.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
        </select>
      </div>
      {tab === 'levels' ? <Levels warehouseId={warehouseId} /> : <Movements warehouseId={warehouseId} />}
      {dialog && <StockDialog mode={dialog} warehouses={warehouses.data ?? []} onClose={() => setDialog(null)} />}
    </>
  )
}

function Levels({ warehouseId }: { warehouseId: string }) {
  const [q, setQ] = useState('')
  const search = useDebounced(q)
  const levels = useQuery({
    queryKey: ['stock', 'levels', warehouseId, search],
    queryFn: () => api.get<StockLevel[]>(`/api/stock/levels${query({ warehouseId, q: search })}`),
  })
  return (
    <>
      <SearchBox value={q} onChange={setQ} placeholder="Filter by product…" />
      <ErrorBox error={levels.error} />
      {levels.isLoading ? <Loading /> : levels.data?.length === 0 ? <Empty>No stock yet. Receive a purchase order or adjust stock.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>SKU</th><th>Product</th><th>Warehouse</th><th className="num">On hand</th><th className="num">Reorder at</th></tr></thead>
            <tbody>
              {levels.data?.map((l) => (
                <tr key={l.productId + l.warehouseId}>
                  <td>{l.sku}</td><td>{l.productName}</td><td>{l.warehouseCode}</td>
                  <td className={`num ${l.quantity <= l.reorderLevel ? 'warn' : ''}`}>{l.quantity} {l.unit}</td>
                  <td className="num">{l.reorderLevel}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

function Movements({ warehouseId }: { warehouseId: string }) {
  const [page, setPage] = useState(0)
  const movements = useQuery({
    queryKey: ['stock', 'movements', warehouseId, page],
    queryFn: () => api.get<Page<Movement>>(`/api/stock/movements${query({ warehouseId, page, size: 50 })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <ErrorBox error={movements.error} />
      {movements.isLoading ? <Loading /> : movements.data?.content.length === 0 ? <Empty>No movements yet.</Empty> : (
        <div className="card">
          <MovementsTable movements={movements.data?.content ?? []} />
          <Pagination page={page} totalPages={movements.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  )
}

export function MovementsTable({ movements }: { movements: Movement[] }) {
  return (
    <table className="table">
      <thead><tr><th>When</th><th>Product</th><th>Warehouse</th><th>Type</th><th className="num">Qty</th><th>Note</th></tr></thead>
      <tbody>
        {movements.map((m) => (
          <tr key={m.id}>
            <td>{dateTime(m.createdAt)}</td><td>{m.sku} · {m.productName}</td><td>{m.warehouseCode}</td><td>{label(m.type)}</td>
            <td className={`num ${m.quantity < 0 ? 'neg' : 'pos'}`}>{m.quantity > 0 ? `+${m.quantity}` : m.quantity}</td><td>{m.note}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function StockDialog({ mode, warehouses, onClose }: { mode: 'adjust' | 'transfer'; warehouses: Warehouse[]; onClose: () => void }) {
  const queryClient = useQueryClient()
  const products = useQuery({ queryKey: ['products', 'all-active'], queryFn: () => api.get<Page<Product>>('/api/products?activeOnly=true&size=1000&sort=name,asc') })
  const active = warehouses.filter((w) => w.active)
  const [form, setForm] = useState({ productId: '', warehouseId: '', toWarehouseId: '', quantity: '', note: '' })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [key]: e.target.value })

  const save = useMutation({
    mutationFn: () => mode === 'adjust'
      ? api.post('/api/stock/adjustments', { productId: form.productId, warehouseId: form.warehouseId, quantity: Number(form.quantity), note: form.note })
      : api.post('/api/stock/transfers', { productId: form.productId, fromWarehouseId: form.warehouseId, toWarehouseId: form.toWarehouseId, quantity: Number(form.quantity), note: form.note }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['stock'] }); queryClient.invalidateQueries({ queryKey: ['dashboard'] }); onClose() },
  })

  return (
    <Modal title={mode === 'adjust' ? 'Adjust stock' : 'Transfer stock'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <Field label="Product">
          <select value={form.productId} onChange={set('productId')} required>
            <option value="">Select…</option>
            {products.data?.content.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}
          </select>
        </Field>
        <div className="form-grid">
          <Field label={mode === 'adjust' ? 'Warehouse' : 'From'}>
            <select value={form.warehouseId} onChange={set('warehouseId')} required>
              <option value="">Select…</option>
              {active.map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
            </select>
          </Field>
          {mode === 'transfer' && (
            <Field label="To">
              <select value={form.toWarehouseId} onChange={set('toWarehouseId')} required>
                <option value="">Select…</option>
                {active.filter((w) => w.id !== form.warehouseId).map((w) => <option key={w.id} value={w.id}>{w.code}</option>)}
              </select>
            </Field>
          )}
          <Field label={mode === 'adjust' ? 'Quantity (+ add, − remove)' : 'Quantity'} error={fieldError(save.error, 'quantity')}>
            <input type="number" min={mode === 'transfer' ? 1 : undefined} value={form.quantity} onChange={set('quantity')} required />
          </Field>
        </div>
        <Field label={mode === 'adjust' ? 'Reason (required)' : 'Note'} error={fieldError(save.error, 'note')}>
          <input value={form.note} onChange={set('note')} required={mode === 'adjust'} placeholder={mode === 'adjust' ? 'e.g. opening stock, damaged, stock count' : ''} />
        </Field>
        <div className="modal-actions"><span className="spacer" /><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={save.isPending}>Save</button></div>
      </form>
    </Modal>
  )
}
