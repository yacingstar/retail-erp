import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Warehouse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Badge, Empty, ErrorBox, Field, fieldError, Loading, Modal, PageHeader } from '../components/ui'
import { useSubscription } from '../auth/useSubscription'
import { Usage } from './PlanPage'

export function WarehousesPage() {
  const { hasRole } = useAuth()
  const canEdit = hasRole('ADMIN', 'MANAGER')
  const [editing, setEditing] = useState<Warehouse | 'new' | null>(null)
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })
  const { data: sub } = useSubscription()
  const atLimit = sub ? sub.maxWarehouses !== null && sub.activeWarehouses >= sub.maxWarehouses : false

  return (
    <>
      <PageHeader title="Warehouses" actions={<>
        {sub && <span className="muted">Active: <Usage used={sub.activeWarehouses} max={sub.maxWarehouses} /></span>}
        {canEdit && <button className="btn btn-primary" disabled={atLimit} title={atLimit ? 'Plan limit reached' : ''} onClick={() => setEditing('new')}>New warehouse</button>}
      </>} />
      <ErrorBox error={warehouses.error} />
      {warehouses.isLoading ? <Loading /> : warehouses.data?.length === 0 ? <Empty>No warehouses yet. Create one to start tracking stock.</Empty> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Code</th><th>Name</th><th>Address</th><th>Status</th></tr></thead>
            <tbody>
              {warehouses.data?.map((w) => (
                <tr key={w.id} className={canEdit ? 'clickable' : ''} onClick={() => canEdit && setEditing(w)}>
                  <td>{w.code}</td><td>{w.name}</td><td>{w.address}</td><td><Badge value={w.active} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <WarehouseForm warehouse={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function WarehouseForm({ warehouse, onClose }: { warehouse: Warehouse | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ code: warehouse?.code ?? '', name: warehouse?.name ?? '', address: warehouse?.address ?? '', active: warehouse?.active ?? true })
  const save = useMutation({
    mutationFn: () => (warehouse ? api.put(`/api/warehouses/${warehouse.id}`, form) : api.post('/api/warehouses', form)),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['warehouses'] }); queryClient.invalidateQueries({ queryKey: ['subscription'] }); onClose() },
  })
  return (
    <Modal title={warehouse ? `Edit ${warehouse.code}` : 'New warehouse'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Code" error={fieldError(save.error, 'code')}><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} required /></Field>
          <Field label="Name" error={fieldError(save.error, 'name')}><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
        </div>
        <Field label="Address"><textarea rows={2} value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></Field>
        <label className="checkbox"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
        <div className="modal-actions"><span className="spacer" /><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={save.isPending}>Save</button></div>
      </form>
    </Modal>
  )
}
