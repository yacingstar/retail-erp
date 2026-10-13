import { useMemo, useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Page, Party, Product, Receipt, Shift, StockLevel, Warehouse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { dateTime, label, money } from '../components/format'
import { ErrorBox, Field, Loading, Modal, PageHeader, useDebounced } from '../components/ui'

interface CartLine {
  product: Product
  quantity: number
  /** null = list price */
  unitPrice: number | null
}

const round2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100

/** Same arithmetic as the backend (SalesOrderLine); the server's numbers are the ones that count. */
function totals(lines: CartLine[], discountPercent: number) {
  let subtotal = 0, discount = 0, tax = 0
  for (const l of lines) {
    const sub = round2((l.unitPrice ?? l.product.salePrice) * l.quantity)
    const disc = round2((sub * discountPercent) / 100)
    subtotal += sub
    discount += disc
    tax += round2(((sub - disc) * l.product.taxRate) / 100)
  }
  return { subtotal: round2(subtotal), discount: round2(discount), tax: round2(tax), total: round2(subtotal - discount + tax) }
}

export function PosPage() {
  const shift = useQuery({
    queryKey: ['pos', 'shift'],
    queryFn: () => api.get<Shift | undefined>('/api/pos/shifts/current').then((s) => s ?? null),
  })
  if (shift.isLoading) return <Loading />
  if (shift.error) return <ErrorBox error={shift.error} />
  return shift.data ? <Till shift={shift.data} /> : <OpenShift />
}

function OpenShift() {
  const queryClient = useQueryClient()
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })
  const active = warehouses.data?.filter((w) => w.active) ?? []
  const [warehouseId, setWarehouseId] = useState('')
  const [openingCash, setOpeningCash] = useState('0')
  const effective = warehouseId || (active.length === 1 ? active[0].id : '')
  const open = useMutation({
    mutationFn: () => api.post<Shift>('/api/pos/shifts', { warehouseId: effective, openingCash: Number(openingCash) }),
    onSuccess: (s) => queryClient.setQueryData(['pos', 'shift'], s),
  })
  return (
    <>
      <PageHeader title="Point of Sale" />
      <form className="card open-shift" onSubmit={(e: FormEvent) => { e.preventDefault(); open.mutate() }}>
        <h2>Open your shift</h2>
        <p className="muted">Count the cash in the drawer before you start. At the end of the shift you'll count it again.</p>
        <ErrorBox error={open.error} />
        <Field label="Store / warehouse">
          <select value={effective} onChange={(e) => setWarehouseId(e.target.value)} required>
            <option value="">Select…</option>
            {active.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
          </select>
        </Field>
        <Field label="Cash in drawer (opening float)">
          <input type="number" min="0" step="0.01" value={openingCash} onChange={(e) => setOpeningCash(e.target.value)} required />
        </Field>
        <button className="btn btn-primary" disabled={open.isPending}>Open shift</button>
      </form>
    </>
  )
}

function Till({ shift }: { shift: Shift }) {
  const queryClient = useQueryClient()
  const { hasRole } = useAuth()
  const manager = hasRole('ADMIN', 'MANAGER')
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [cart, setCart] = useState<CartLine[]>([])
  const [customerId, setCustomerId] = useState('')
  const [discount, setDiscount] = useState('')
  const [method, setMethod] = useState<'CASH' | 'CARD'>('CASH')
  const [cashReceived, setCashReceived] = useState('')
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  const [closing, setClosing] = useState(false)
  const search = useDebounced(q, 200)

  const categories = useQuery({ queryKey: ['products', 'categories'], queryFn: () => api.get<string[]>('/api/products/categories') })
  const products = useQuery({
    queryKey: ['products', 'pos', search, category],
    queryFn: () => api.get<Page<Product>>(`/api/products${query({ q: search, category, activeOnly: true, size: 200, sort: 'name,asc' })}`),
    placeholderData: keepPreviousData,
  })
  const stock = useQuery({
    queryKey: ['stock', 'levels', shift.warehouseId, ''],
    queryFn: () => api.get<StockLevel[]>(`/api/stock/levels${query({ warehouseId: shift.warehouseId })}`),
  })
  const customers = useQuery({ queryKey: ['customers', 'all'], queryFn: () => api.get<Page<Party>>('/api/customers?size=1000') })
  const onHand = useMemo(() => new Map((stock.data ?? []).map((l) => [l.productId, l.quantity])), [stock.data])

  const discountPercent = Math.min(100, Math.max(0, Number(discount) || 0))
  const t = totals(cart, discountPercent)
  const cash = Number(cashReceived) || 0
  const change = method === 'CASH' && cashReceived !== '' ? round2(cash - t.total) : 0

  const add = (product: Product) => setCart((lines) => {
    const existing = lines.find((l) => l.product.id === product.id)
    return existing
      ? lines.map((l) => (l === existing ? { ...l, quantity: l.quantity + 1 } : l))
      : [...lines, { product, quantity: 1, unitPrice: null }]
  })
  const update = (id: string, patch: Partial<CartLine>) => setCart((lines) => lines.map((l) => (l.product.id === id ? { ...l, ...patch } : l)))
  const remove = (id: string) => setCart((lines) => lines.filter((l) => l.product.id !== id))
  const reset = () => { setCart([]); setCustomerId(''); setDiscount(''); setCashReceived(''); setMethod('CASH') }

  const checkout = useMutation({
    mutationFn: () => api.post<Receipt>('/api/pos/checkout', {
      customerId: customerId || null,
      lines: cart.map((l) => ({ productId: l.product.id, quantity: l.quantity, unitPrice: l.unitPrice })),
      discountPercent,
      paymentMethod: method,
      cashReceived: method === 'CASH' && cashReceived !== '' ? cash : null,
    }),
    onSuccess: (r) => {
      setReceipt(r)
      reset()
      for (const key of ['stock', 'pos', 'sales-orders', 'invoices', 'dashboard']) queryClient.invalidateQueries({ queryKey: [key] })
    },
  })

  const cannotPay = cart.length === 0 || checkout.isPending || (method === 'CASH' && cashReceived !== '' && cash < t.total)

  return (
    <>
      <PageHeader title="Point of Sale" actions={<>
        <span className="muted">{shift.warehouseCode} · shift opened {dateTime(shift.openedAt)} · {shift.salesCount} sales</span>
        {manager && <Link className="btn" to="/pos/shifts">Shift history</Link>}
        <button className="btn" onClick={() => setClosing(true)}>Close shift</button>
      </>} />
      <div className="pos-layout">
        <section className="pos-products">
          <input className="search pos-search" type="search" value={q} placeholder="Search products…" onChange={(e) => setQ(e.target.value)} autoFocus />
          <div className="chips">
            <button className={category === '' ? 'chip active' : 'chip'} onClick={() => setCategory('')}>All</button>
            {categories.data?.map((c) => <button key={c} className={category === c ? 'chip active' : 'chip'} onClick={() => setCategory(c)}>{c}</button>)}
          </div>
          <div className="tiles">
            {products.data?.content.map((p) => {
              const qty = onHand.get(p.id) ?? 0
              return (
                <button key={p.id} className={`tile ${qty <= 0 ? 'out' : ''}`} onClick={() => add(p)}>
                  <strong>{p.name}</strong>
                  <span>{money(p.salePrice)}</span>
                  <small>{qty > 0 ? `${qty} in stock` : 'Out of stock'}</small>
                </button>
              )
            })}
            {products.data?.content.length === 0 && <div className="muted">No products match.</div>}
          </div>
        </section>

        <section className="card pos-cart">
          <select value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
            <option value="">Walk-in customer</option>
            {customers.data?.content.filter((c) => c.active).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>

          <div className="cart-lines">
            {cart.length === 0 && <div className="muted cart-empty">Tap a product to add it.</div>}
            {cart.map((l) => (
              <div key={l.product.id} className="cart-line">
                <div className="cart-name">{l.product.name}</div>
                <div className="qty">
                  <button onClick={() => (l.quantity > 1 ? update(l.product.id, { quantity: l.quantity - 1 }) : remove(l.product.id))} aria-label="Less">−</button>
                  <span>{l.quantity}</span>
                  <button onClick={() => update(l.product.id, { quantity: l.quantity + 1 })} aria-label="More">+</button>
                </div>
                <input className="cart-price" type="number" min="0" step="0.01" title="Unit price"
                  value={l.unitPrice ?? l.product.salePrice}
                  onChange={(e) => update(l.product.id, { unitPrice: e.target.value === '' ? null : Number(e.target.value) })} />
                <div className="num">{money((l.unitPrice ?? l.product.salePrice) * l.quantity)}</div>
                <button className="btn-icon" onClick={() => remove(l.product.id)} aria-label="Remove">×</button>
              </div>
            ))}
          </div>

          <div className="cart-totals">
            <label className="discount">Discount % <input type="number" min="0" max="100" step="0.5" value={discount} onChange={(e) => setDiscount(e.target.value)} placeholder="0" /></label>
            {!manager && <small className="muted">Cashiers: up to 10%</small>}
            <div><span>Subtotal</span><span>{money(t.subtotal)}</span></div>
            {t.discount > 0 && <div><span>Discount</span><span>−{money(t.discount)}</span></div>}
            <div><span>Tax</span><span>{money(t.tax)}</span></div>
            <div className="grand"><span>Total</span><span>{money(t.total)}</span></div>
          </div>

          <div className="tabs pay-method">
            <button className={method === 'CASH' ? 'active' : ''} onClick={() => setMethod('CASH')}>Cash</button>
            <button className={method === 'CARD' ? 'active' : ''} onClick={() => setMethod('CARD')}>Card</button>
          </div>
          {method === 'CASH' && (
            <div className="cash">
              <label>Cash received <input type="number" min="0" step="0.01" value={cashReceived} onChange={(e) => setCashReceived(e.target.value)} placeholder={money(t.total)} /></label>
              <div className="quick-cash">
                {[t.total, Math.ceil(t.total / 5) * 5, Math.ceil(t.total / 10) * 10, Math.ceil(t.total / 50) * 50]
                  .filter((v, i, all) => v > 0 && all.indexOf(v) === i)
                  .map((v) => <button key={v} className="btn btn-small" onClick={() => setCashReceived(String(v))}>{money(v)}</button>)}
              </div>
              {cashReceived !== '' && (
                <div className={`change ${change < 0 ? 'neg' : ''}`}>{change < 0 ? `Missing ${money(-change)}` : `Change: ${money(change)}`}</div>
              )}
            </div>
          )}
          <ErrorBox error={checkout.error} />
          <button className="btn btn-primary pay" disabled={cannotPay} onClick={() => checkout.mutate()}>
            {checkout.isPending ? 'Processing…' : `Complete sale · ${money(t.total)}`}
          </button>
          {cart.length > 0 && <button className="btn btn-small" onClick={reset}>Clear</button>}
        </section>
      </div>

      {receipt && <ReceiptModal receipt={receipt} onClose={() => setReceipt(null)} />}
      {closing && <CloseShift shift={shift} onClose={() => setClosing(false)} />}
    </>
  )
}

export function ReceiptView({ receipt: r }: { receipt: Receipt }) {
  return (
    <div className="receipt">
      <div className="center"><strong>{r.shopName}</strong><br />{r.warehouseName}</div>
      <div className="center small">{dateTime(r.soldAt)}<br />Sale {r.orderNumber} · Invoice {r.invoiceNumber}</div>
      {r.customerName && <div className="small">Customer: {r.customerName}</div>}
      <hr />
      {r.lines.map((l, i) => (
        <div key={i} className="r-line">
          <div>{l.name}</div>
          <div className="r-row"><span>{l.quantity} × {money(l.unitPrice)}</span><span>{money(l.amount)}</span></div>
        </div>
      ))}
      <hr />
      <div className="r-row"><span>Subtotal</span><span>{money(r.subtotal)}</span></div>
      {r.discountTotal > 0 && <div className="r-row"><span>Discount</span><span>−{money(r.discountTotal)}</span></div>}
      <div className="r-row"><span>Tax</span><span>{money(r.taxTotal)}</span></div>
      <div className="r-row r-total"><span>TOTAL</span><span>{money(r.total)}</span></div>
      <div className="r-row"><span>{label(r.paymentMethod)}</span><span>{money(r.cashReceived ?? r.total)}</span></div>
      {r.paymentMethod === 'CASH' && <div className="r-row"><span>Change</span><span>{money(r.change)}</span></div>}
      <hr />
      <div className="center small">{r.cashierName ? `Served by ${r.cashierName}. ` : ''}Thank you!</div>
    </div>
  )
}

function ReceiptModal({ receipt, onClose }: { receipt: Receipt; onClose: () => void }) {
  // Printing: a body class makes the print CSS hide everything except the receipt.
  const print = () => {
    document.body.classList.add('print-receipt')
    window.addEventListener('afterprint', () => document.body.classList.remove('print-receipt'), { once: true })
    window.print()
  }
  return (
    <Modal title={receipt.paymentMethod === 'CASH' ? `Sale complete · change ${money(receipt.change)}` : 'Sale complete'} onClose={onClose}>
      <ReceiptView receipt={receipt} />
      <div className="modal-actions">
        <Link className="btn" to={`/invoices/${receipt.invoiceId}`}>Invoice</Link>
        <span className="spacer" />
        <button className="btn" onClick={print}>Print receipt</button>
        <button className="btn btn-primary" onClick={onClose} autoFocus>New sale</button>
      </div>
    </Modal>
  )
}

function CloseShift({ shift, onClose }: { shift: Shift; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [counted, setCounted] = useState('')
  const [notes, setNotes] = useState('')
  const [result, setResult] = useState<Shift | null>(null)
  const close = useMutation({
    mutationFn: () => api.post<Shift>(`/api/pos/shifts/${shift.id}/close`, { countedCash: Number(counted), notes: notes || null }),
    onSuccess: (s) => setResult(s),
  })
  const done = () => { queryClient.setQueryData(['pos', 'shift'], null); queryClient.invalidateQueries({ queryKey: ['pos'] }); onClose() }

  if (result) {
    return (
      <Modal title="Shift closed" onClose={done}>
        <ShiftSummary shift={result} />
        <div className="modal-actions"><span className="spacer" /><button className="btn btn-primary" onClick={done}>Done</button></div>
      </Modal>
    )
  }
  return (
    <Modal title="Close shift" onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); close.mutate() }}>
        <ErrorBox error={close.error} />
        <p className="muted">Count all the cash in the drawer and enter the amount. The difference with what's expected is recorded.</p>
        <Field label="Counted cash"><input type="number" min="0" step="0.01" value={counted} onChange={(e) => setCounted(e.target.value)} required autoFocus /></Field>
        <Field label="Notes (optional)"><input value={notes} onChange={(e) => setNotes(e.target.value)} /></Field>
        <div className="modal-actions"><span className="spacer" /><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={close.isPending}>Close shift</button></div>
      </form>
    </Modal>
  )
}

export function ShiftSummary({ shift: s }: { shift: Shift }) {
  return (
    <div className="details">
      <div><small>Store</small>{s.warehouseCode} · {s.warehouseName}</div>
      <div><small>Cashier</small>{s.openedByName ?? '—'}</div>
      <div><small>Opened</small>{dateTime(s.openedAt)}</div>
      <div><small>Closed</small>{dateTime(s.closedAt)}</div>
      <div><small>Sales</small>{s.salesCount} · {money(s.salesTotal)}</div>
      <div><small>By method</small>{s.payments.map((p) => `${label(p.method)} ${money(p.amount)}`).join(', ') || '—'}</div>
      <div><small>Opening cash</small>{money(s.openingCash)}</div>
      <div><small>Expected cash</small>{money(s.expectedCash)}</div>
      <div><small>Counted cash</small>{s.countedCash === null ? '—' : money(s.countedCash)}</div>
      <div><small>Difference</small>{s.difference === null ? '—' : <span className={s.difference < 0 ? 'neg' : s.difference > 0 ? 'pos' : ''}>{money(s.difference)}</span>}</div>
    </div>
  )
}

export function ShiftsPage() {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Shift | null>(null)
  const shifts = useQuery({
    queryKey: ['pos', 'shifts', page],
    queryFn: () => api.get<Page<Shift>>(`/api/pos/shifts${query({ page })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <>
      <PageHeader title="Shift history" actions={<Link className="btn" to="/pos">Back to till</Link>} />
      <ErrorBox error={shifts.error} />
      {shifts.isLoading ? <Loading /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Opened</th><th>Cashier</th><th>Store</th><th>Status</th><th className="num">Sales</th><th className="num">Expected cash</th><th className="num">Counted</th><th className="num">Difference</th></tr></thead>
            <tbody>
              {shifts.data?.content.map((s) => (
                <tr key={s.id} className="clickable" onClick={() => setSelected(s)}>
                  <td>{dateTime(s.openedAt)}</td><td>{s.openedByName}</td><td>{s.warehouseCode}</td><td>{label(s.status)}</td>
                  <td className="num">{money(s.salesTotal)}</td><td className="num">{money(s.expectedCash)}</td>
                  <td className="num">{s.countedCash === null ? '—' : money(s.countedCash)}</td>
                  <td className={`num ${s.difference !== null && s.difference < 0 ? 'neg' : ''}`}>{s.difference === null ? '—' : money(s.difference)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {shifts.data && shifts.data.totalPages > 1 && (
            <div className="pagination">
              <button className="btn" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
              <span>Page {page + 1} of {shifts.data.totalPages}</span>
              <button className="btn" disabled={page + 1 >= shifts.data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          )}
        </div>
      )}
      {selected && <Modal title="Shift" onClose={() => setSelected(null)}><ShiftSummary shift={selected} />{selected.notes && <p className="muted">{selected.notes}</p>}</Modal>}
    </>
  )
}
