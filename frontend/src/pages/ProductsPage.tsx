import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, query } from '../api/client'
import type { Page, Product } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { money } from '../components/format'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, Modal, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

export function ProductsPage() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN', 'MANAGER')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const search = useDebounced(q)

  const products = useQuery({
    queryKey: ['products', search, page],
    queryFn: () => api.get<Page<Product>>(`/api/products${query({ q: search, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title="Products" actions={canEdit && <button className="btn btn-primary" onClick={() => setEditing('new')}>New product</button>} />
      <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} placeholder="Search by name or SKU…" />
      <ErrorBox error={products.error} />
      {products.isLoading ? <Loading /> : products.data?.content.length === 0 ? <Empty>No products found.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>SKU</th><th>Name</th><th>Unit</th><th className="num">Price</th><th className="num">Cost</th><th className="num">Tax %</th><th className="num">Reorder at</th><th>Status</th></tr></thead>
            <tbody>
              {products.data?.content.map((p) => (
                <tr key={p.id} className={canEdit ? 'clickable' : ''} onClick={() => canEdit && setEditing(p)}>
                  <td>{p.sku}</td><td>{p.name}</td><td>{p.unit}</td><td className="num">{money(p.salePrice)}</td>
                  <td className="num">{money(p.costPrice)}</td><td className="num">{p.taxRate}</td><td className="num">{p.reorderLevel}</td>
                  <td><Badge value={p.active} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={products.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
      {editing && <ProductForm product={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function ProductForm({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    sku: product?.sku ?? '', name: product?.name ?? '', description: product?.description ?? '', unit: product?.unit ?? 'pcs',
    salePrice: String(product?.salePrice ?? ''), costPrice: String(product?.costPrice ?? ''), taxRate: String(product?.taxRate ?? '0'),
    reorderLevel: String(product?.reorderLevel ?? '0'), active: product?.active ?? true,
  })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value })

  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, salePrice: Number(form.salePrice), costPrice: Number(form.costPrice), taxRate: Number(form.taxRate), reorderLevel: Number(form.reorderLevel) }
      return product ? api.put(`/api/products/${product.id}`, body) : api.post('/api/products', body)
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['products'] }); onClose() },
  })
  const remove = useMutation({
    mutationFn: () => api.delete(`/api/products/${product!.id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['products'] }); onClose() },
  })
  const error = save.error ?? remove.error

  return (
    <Modal title={product ? `Edit ${product.sku}` : 'New product'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={error} />
        <div className="form-grid">
          <Field label="SKU" error={fieldError(error, 'sku')}><input value={form.sku} onChange={set('sku')} required /></Field>
          <Field label="Name" error={fieldError(error, 'name')}><input value={form.name} onChange={set('name')} required /></Field>
          <Field label="Sale price" error={fieldError(error, 'salePrice')}><input type="number" step="0.01" min="0" value={form.salePrice} onChange={set('salePrice')} required /></Field>
          <Field label="Cost price" error={fieldError(error, 'costPrice')}><input type="number" step="0.01" min="0" value={form.costPrice} onChange={set('costPrice')} required /></Field>
          <Field label="Tax rate (%)" error={fieldError(error, 'taxRate')}><input type="number" step="0.01" min="0" max="100" value={form.taxRate} onChange={set('taxRate')} /></Field>
          <Field label="Unit"><input value={form.unit} onChange={set('unit')} /></Field>
          <Field label="Reorder level" error={fieldError(error, 'reorderLevel')}><input type="number" min="0" value={form.reorderLevel} onChange={set('reorderLevel')} /></Field>
          <label className="checkbox"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
        </div>
        <Field label="Description"><textarea value={form.description} onChange={set('description')} rows={3} /></Field>
        <div className="modal-actions">
          {product && <button type="button" className="btn btn-danger" onClick={() => confirm('Delete this product? Products with history can only be deactivated.') && remove.mutate()}>Delete</button>}
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </Modal>
  )
}
