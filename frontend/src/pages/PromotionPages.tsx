import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { Page, PriceList, PriceListSummary, Product, Promotion, PromotionScope, PromotionType } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { date, money } from '../components/format'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, Modal, PageHeader } from '../components/ui'

const TYPES: { value: PromotionType; label: string }[] = [
  { value: 'PERCENT_OFF', label: '% off items' },
  { value: 'AMOUNT_OFF', label: 'Amount off each item' },
  { value: 'BUY_X_GET_Y', label: 'Buy X get Y free' },
  { value: 'ORDER_PERCENT_OFF', label: '% off the whole order (above an amount)' },
]

/** A short human description, e.g. "20% off · Drinks · coupon SUMMER". */
function describe(p: Promotion): string {
  const what = p.type === 'PERCENT_OFF' ? `${p.value}% off`
    : p.type === 'AMOUNT_OFF' ? `${money(p.value)} off each`
    : p.type === 'BUY_X_GET_Y' ? `Buy ${p.buyQty} get ${p.getQty} free`
    : `${p.value}% off orders from ${money(p.minOrderAmount ?? 0)}`
  const scope = p.scope === 'CATEGORY' ? p.category : p.scope === 'PRODUCT' ? p.productName : p.type === 'ORDER_PERCENT_OFF' ? null : 'all products'
  return [what, scope].filter(Boolean).join(' · ')
}

export function PromotionsPage() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN', 'MANAGER')
  const [editing, setEditing] = useState<Promotion | 'new' | null>(null)
  const promotions = useQuery({ queryKey: ['promotions'], queryFn: () => api.get<Promotion[]>('/api/promotions') })
  return (
    <>
      <PageHeader title="Promotions" actions={canEdit && <button className="btn btn-primary" onClick={() => setEditing('new')}>New promotion</button>} />
      <p className="muted">Promotions apply automatically at checkout (or when their coupon code is entered). If several could apply, the customer gets the single best one.</p>
      <ErrorBox error={promotions.error} />
      {promotions.isLoading ? <Loading /> : promotions.data?.length === 0 ? <Empty>No promotions yet.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Name</th><th>Offer</th><th>Coupon</th><th>Dates</th><th>Status</th></tr></thead>
            <tbody>
              {promotions.data?.map((p) => (
                <tr key={p.id} className={canEdit ? 'clickable' : ''} onClick={() => canEdit && setEditing(p)}>
                  <td>{p.name}</td><td>{describe(p)}</td><td>{p.couponCode ? <span className="chip-small">{p.couponCode}</span> : 'Automatic'}</td>
                  <td>{p.startsOn || p.endsOn ? `${p.startsOn ? date(p.startsOn) : '…'} – ${p.endsOn ? date(p.endsOn) : '…'}` : 'Always'}</td>
                  <td>{!p.active ? <Badge value={false} /> : p.runningToday ? <span className="badge badge-green">Running</span> : <span className="badge badge-grey">Scheduled / ended</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <PromotionForm promotion={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function PromotionForm({ promotion, onClose }: { promotion: Promotion | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const products = useQuery({ queryKey: ['products', 'all-active'], queryFn: () => api.get<Page<Product>>('/api/products?activeOnly=true&size=1000&sort=name,asc') })
  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: () => api.get<string[]>('/api/products/categories') })
  const [form, setForm] = useState({
    name: promotion?.name ?? '', type: promotion?.type ?? 'PERCENT_OFF' as PromotionType, value: String(promotion?.value ?? ''),
    buyQty: String(promotion?.buyQty ?? 2), getQty: String(promotion?.getQty ?? 1), minOrderAmount: String(promotion?.minOrderAmount ?? ''),
    scope: promotion?.scope ?? 'ALL' as PromotionScope, category: promotion?.category ?? '', productId: promotion?.productId ?? '',
    couponCode: promotion?.couponCode ?? '', startsOn: promotion?.startsOn ?? '', endsOn: promotion?.endsOn ?? '', active: promotion?.active ?? true,
  })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm({ ...form, [key]: e.target.value })
  const isOrder = form.type === 'ORDER_PERCENT_OFF'
  const isBxgy = form.type === 'BUY_X_GET_Y'
  const done = () => { queryClient.invalidateQueries({ queryKey: ['promotions'] }); onClose() }

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.name, type: form.type, value: isBxgy ? 0 : Number(form.value),
        buyQty: isBxgy ? Number(form.buyQty) : null, getQty: isBxgy ? Number(form.getQty) : null,
        minOrderAmount: isOrder ? Number(form.minOrderAmount || 0) : null,
        scope: isOrder ? 'ALL' : form.scope, category: form.scope === 'CATEGORY' ? form.category : null,
        productId: form.scope === 'PRODUCT' ? form.productId : null,
        couponCode: form.couponCode || null, startsOn: form.startsOn || null, endsOn: form.endsOn || null, active: form.active,
      }
      return promotion ? api.put(`/api/promotions/${promotion.id}`, body) : api.post('/api/promotions', body)
    },
    onSuccess: done,
  })
  const remove = useMutation({ mutationFn: () => api.delete(`/api/promotions/${promotion!.id}`), onSuccess: done })
  const error = save.error ?? remove.error

  return (
    <Modal title={promotion ? `Edit ${promotion.name}` : 'New promotion'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={error} />
        <Field label="Name (shown on receipts)" error={fieldError(error, 'name')}><input value={form.name} onChange={set('name')} required placeholder="e.g. Summer drinks −20%" /></Field>
        <Field label="Type">
          <select value={form.type} onChange={set('type')}>{TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}</select>
        </Field>
        <div className="form-grid">
          {isBxgy ? <>
            <Field label="Buy"><input type="number" min="1" value={form.buyQty} onChange={set('buyQty')} required /></Field>
            <Field label="Get free"><input type="number" min="1" value={form.getQty} onChange={set('getQty')} required /></Field>
          </> : (
            <Field label={form.type === 'AMOUNT_OFF' ? 'Amount off per item' : 'Percent off'} error={fieldError(error, 'value')}>
              <input type="number" min="0" max={form.type === 'AMOUNT_OFF' ? undefined : 100} step="0.01" value={form.value} onChange={set('value')} required />
            </Field>
          )}
          {isOrder && <Field label="Minimum order amount"><input type="number" min="0" step="0.01" value={form.minOrderAmount} onChange={set('minOrderAmount')} /></Field>}
          {!isOrder && (
            <Field label="Applies to">
              <select value={form.scope} onChange={set('scope')}>
                <option value="ALL">All products</option>
                <option value="CATEGORY">A category</option>
                <option value="PRODUCT">One product</option>
              </select>
            </Field>
          )}
          {!isOrder && form.scope === 'CATEGORY' && (
            <Field label="Category">
              <select value={form.category} onChange={set('category')} required>
                <option value="">Select…</option>
                {categories.data?.map((c) => <option key={c} value={c}>{c}</option>)}
              </select>
            </Field>
          )}
          {!isOrder && form.scope === 'PRODUCT' && (
            <Field label="Product">
              <select value={form.productId} onChange={set('productId')} required>
                <option value="">Select…</option>
                {products.data?.content.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}
              </select>
            </Field>
          )}
          <Field label="Coupon code (empty = automatic)" error={fieldError(error, 'couponCode')}><input value={form.couponCode} onChange={set('couponCode')} placeholder="e.g. SUMMER10" /></Field>
          <Field label="Starts on"><input type="date" value={form.startsOn} onChange={set('startsOn')} /></Field>
          <Field label="Ends on"><input type="date" value={form.endsOn} onChange={set('endsOn')} /></Field>
        </div>
        <label className="checkbox"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
        <div className="modal-actions">
          {promotion && <button type="button" className="btn btn-danger" onClick={() => confirm('Delete this promotion? Past sales keep their discount.') && remove.mutate()}>Delete</button>}
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </Modal>
  )
}

export function PriceListsPage() {
  const lists = useQuery({ queryKey: ['price-lists'], queryFn: () => api.get<PriceListSummary[]>('/api/price-lists') })
  return (
    <>
      <PageHeader title="Price lists" actions={<Link className="btn btn-primary" to="/price-lists/new">New price list</Link>} />
      <p className="muted">A price list gives a group of customers (e.g. wholesale buyers) their own prices. Assign it on the customer's card. Products not on the list keep their normal price.</p>
      <ErrorBox error={lists.error} />
      {lists.isLoading ? <Loading /> : lists.data?.length === 0 ? <Empty>No price lists yet.</Empty> : (
        <div className="card">
          <table className="table">
            <tbody>
              {lists.data?.map((l) => (
                <tr key={l.id}><td><Link to={`/price-lists/${l.id}`}>{l.name}</Link></td><td><Badge value={l.active} /></td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

export function PriceListEditPage() {
  const { id } = useParams()
  const existing = useQuery({ queryKey: ['price-lists', id], queryFn: () => api.get<PriceList>(`/api/price-lists/${id}`), enabled: !!id && id !== 'new' })
  if (id && id !== 'new' && existing.isLoading) return <Loading />
  if (id && id !== 'new' && !existing.data) return <ErrorBox error={existing.error} />
  return <PriceListForm list={existing.data ?? null} />
}

function PriceListForm({ list }: { list: PriceList | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const products = useQuery({ queryKey: ['products', 'all-active'], queryFn: () => api.get<Page<Product>>('/api/products?activeOnly=true&size=1000&sort=name,asc') })
  const [name, setName] = useState(list?.name ?? '')
  const [active, setActive] = useState(list?.active ?? true)
  const [prices, setPrices] = useState<Record<string, string>>(Object.fromEntries((list?.items ?? []).map((i) => [i.productId, String(i.price)])))

  const save = useMutation({
    mutationFn: () => {
      const body = { name, active, items: Object.entries(prices).filter(([, p]) => p !== '').map(([productId, price]) => ({ productId, price: Number(price) })) }
      return list ? api.put<PriceList>(`/api/price-lists/${list.id}`, body) : api.post<PriceList>('/api/price-lists', body)
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['price-lists'] }); navigate('/price-lists') },
  })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/price-lists/${list!.id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['price-lists'] }); navigate('/price-lists') },
  })

  return (
    <>
      <PageHeader title={list ? `Price list · ${list.name}` : 'New price list'} />
      <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error ?? remove.error} />
        <div className="form-grid">
          <Field label="Name" error={fieldError(save.error, 'name')}><input value={name} onChange={(e) => setName(e.target.value)} required placeholder="e.g. Wholesale" /></Field>
          <label className="checkbox"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} /> Active</label>
        </div>
        <table className="table">
          <thead><tr><th>Product</th><th className="num">Normal price</th><th style={{ width: 160 }}>List price (empty = normal)</th></tr></thead>
          <tbody>
            {products.data?.content.map((p) => (
              <tr key={p.id}>
                <td>{p.sku} · {p.name}</td><td className="num">{money(p.salePrice)}</td>
                <td><input type="number" min="0" step="0.01" value={prices[p.id] ?? ''} onChange={(e) => setPrices({ ...prices, [p.id]: e.target.value })} /></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="modal-actions">
          {list && <button type="button" className="btn btn-danger" onClick={() => confirm('Delete this price list? It must not be assigned to any customer.') && remove.mutate()}>Delete</button>}
          <span className="spacer" />
          <button type="button" className="btn" onClick={() => navigate('/price-lists')}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </>
  )
}
