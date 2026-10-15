import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, query } from '../api/client'
import type { Page, Party, PriceListSummary, StoreCredit } from '../api/types'
import { Link } from 'react-router-dom'
import { useSubscription } from '../auth/useSubscription'
import { date, label, money } from '../components/format'
import { useAuth } from '../auth/AuthContext'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, Modal, PageHeader, Pagination, SearchBox, useDebounced } from '../components/ui'

/** Customers and suppliers have the same shape, so one page component serves both. */
export function PartiesPage({ kind }: { kind: 'customers' | 'suppliers' }) {
  const { hasRole } = useAuth()
  const title = kind === 'customers' ? 'Customers' : 'Suppliers'
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<Party | 'new' | null>(null)
  const search = useDebounced(q)
  const parties = useQuery({
    queryKey: [kind, search, page],
    queryFn: () => api.get<Page<Party>>(`/api/${kind}${query({ q: search, page, size: 20 })}`),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <PageHeader title={title} actions={<button className="btn btn-primary" onClick={() => setEditing('new')}>New {kind === 'customers' ? 'customer' : 'supplier'}</button>} />
      <SearchBox value={q} onChange={(v) => { setQ(v); setPage(0) }} />
      <ErrorBox error={parties.error} />
      {parties.isLoading ? <Loading /> : parties.data?.content.length === 0 ? <Empty>No {kind} found.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Status</th></tr></thead>
            <tbody>
              {parties.data?.content.map((p) => (
                <tr key={p.id} className="clickable" onClick={() => setEditing(p)}>
                  <td>{p.name}</td><td>{p.email}</td><td>{p.phone}</td><td><Badge value={p.active} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={parties.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
      {editing && <PartyForm kind={kind} party={editing === 'new' ? null : editing} canDelete={hasRole('ADMIN', 'MANAGER')} onClose={() => setEditing(null)} />}
    </>
  )
}

function PartyForm({ kind, party, canDelete, onClose }: { kind: string; party: Party | null; canDelete: boolean; onClose: () => void }) {
  const { has } = useSubscription()
  const priceLists = useQuery({
    queryKey: ['price-lists'],
    queryFn: () => api.get<PriceListSummary[]>('/api/price-lists'),
    enabled: kind === 'customers' && has('PROMOTIONS'),
  })
  const queryClient = useQueryClient()
  const [form, setForm] = useState({
    name: party?.name ?? '', email: party?.email ?? '', phone: party?.phone ?? '', address: party?.address ?? '', notes: party?.notes ?? '', active: party?.active ?? true, priceListId: party?.priceListId ?? '',
  })
  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => setForm({ ...form, [key]: e.target.value })
  const done = () => { queryClient.invalidateQueries({ queryKey: [kind] }); onClose() }
  const save = useMutation({
    mutationFn: () => {
      const body = { ...form, email: form.email || null, priceListId: form.priceListId || null }
      return party ? api.put(`/api/${kind}/${party.id}`, body) : api.post(`/api/${kind}`, body)
    },
    onSuccess: done,
  })
  const remove = useMutation({ mutationFn: () => api.delete(`/api/${kind}/${party!.id}`), onSuccess: done })
  const error = save.error ?? remove.error

  return (
    <Modal title={party ? `Edit ${party.name}` : 'New'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={error} />
        <div className="form-grid">
          <Field label="Name" error={fieldError(error, 'name')}><input value={form.name} onChange={set('name')} required /></Field>
          <Field label="Email" error={fieldError(error, 'email')}><input type="email" value={form.email} onChange={set('email')} /></Field>
          <Field label="Phone"><input value={form.phone} onChange={set('phone')} /></Field>
          {kind === 'customers' && has('PROMOTIONS') && (
            <Field label="Price list (customer group)">
              <select value={form.priceListId} onChange={(e) => setForm({ ...form, priceListId: e.target.value })}>
                <option value="">Normal prices</option>
                {priceLists.data?.filter((p) => p.active || p.id === form.priceListId).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </Field>
          )}
          <label className="checkbox"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
        </div>
        <Field label="Address"><textarea rows={2} value={form.address} onChange={set('address')} /></Field>
        <Field label="Notes"><textarea rows={2} value={form.notes} onChange={set('notes')} /></Field>
        {kind === 'customers' && party && <StoreCreditInfo customerId={party.id} />}
        {kind === 'suppliers' && party && has('SUPPLIER_BILLS') && (
          <p><Link to={`/suppliers/${party.id}/statement`}>View statement (bills &amp; payments)</Link></p>
        )}
        <div className="modal-actions">
          {party && canDelete && <button type="button" className="btn btn-danger" onClick={() => confirm('Delete? Records with orders can only be deactivated.') && remove.mutate()}>Delete</button>}
          <span className="spacer" />
          <button type="button" className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending}>Save</button>
        </div>
      </form>
    </Modal>
  )
}

function StoreCreditInfo({ customerId }: { customerId: string }) {
  const credit = useQuery({ queryKey: ['store-credit', customerId], queryFn: () => api.get<StoreCredit>(`/api/customers/${customerId}/store-credit`) })
  if (!credit.data) return null
  return (
    <div className="store-credit">
      <strong>Store credit: {money(credit.data.balance)}</strong>
      {credit.data.entries.slice(0, 5).map((e, i) => (
        <div key={i} className="muted">{date(e.createdAt)} · {e.note ?? label(e.reason)} · <span className={e.amount < 0 ? 'neg' : 'pos'}>{e.amount > 0 ? '+' : ''}{money(e.amount)}</span></div>
      ))}
    </div>
  )
}
