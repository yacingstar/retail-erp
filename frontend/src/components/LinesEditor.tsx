import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Page, Product } from '../api/types'
import { money } from './format'

export interface EditableLine {
  productId: string
  quantity: number
  price: string // unit cost (purchasing) or unit price (sales); '' = use the product default
}

/** Order lines editor shared by purchase and sales orders. */
export function LinesEditor({ lines, onChange, priceLabel, defaultPrice }: {
  lines: EditableLine[]
  onChange: (lines: EditableLine[]) => void
  priceLabel: string
  defaultPrice: (product: Product) => number
}) {
  const products = useQuery({
    queryKey: ['products', 'all-active'],
    queryFn: () => api.get<Page<Product>>('/api/products?activeOnly=true&size=1000&sort=name,asc'),
  })
  const byId = new Map((products.data?.content ?? []).map((p) => [p.id, p]))

  const update = (index: number, patch: Partial<EditableLine>) =>
    onChange(lines.map((line, i) => (i === index ? { ...line, ...patch } : line)))

  return (
    <div>
      <table className="table">
        <thead>
          <tr><th>Product</th><th style={{ width: 100 }}>Qty</th><th style={{ width: 140 }}>{priceLabel}</th><th className="num">Line</th><th /></tr>
        </thead>
        <tbody>
          {lines.map((line, i) => {
            const product = byId.get(line.productId)
            const price = line.price !== '' ? Number(line.price) : product ? defaultPrice(product) : 0
            return (
              <tr key={i}>
                <td>
                  <select value={line.productId} onChange={(e) => update(i, { productId: e.target.value, price: '' })} required>
                    <option value="">Select a product…</option>
                    {products.data?.content.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}
                  </select>
                </td>
                <td><input type="number" min={1} value={line.quantity} onChange={(e) => update(i, { quantity: Number(e.target.value) })} required /></td>
                <td><input type="number" min={0} step="0.01" value={line.price} placeholder={product ? money(defaultPrice(product)) : ''} onChange={(e) => update(i, { price: e.target.value })} /></td>
                <td className="num">{money(price * (line.quantity || 0))}</td>
                <td><button type="button" className="btn-icon" onClick={() => onChange(lines.filter((_, j) => j !== i))} aria-label="Remove line">×</button></td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <button type="button" className="btn btn-small" onClick={() => onChange([...lines, { productId: '', quantity: 1, price: '' }])}>+ Add line</button>
    </div>
  )
}
