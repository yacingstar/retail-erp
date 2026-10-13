import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Role, User } from '../api/types'
import { date } from '../components/format'
import { Badge, ErrorBox, Field, fieldError, Loading, Modal, PageHeader } from '../components/ui'
import { useSubscription } from '../auth/useSubscription'
import { Usage } from './PlanPage'

const ROLES: Role[] = ['ADMIN', 'MANAGER', 'CASHIER']

export function UsersPage() {
  const [editing, setEditing] = useState<User | 'new' | null>(null)
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<User[]>('/api/users') })
  const { data: sub } = useSubscription()
  const atLimit = sub ? sub.maxUsers !== null && sub.activeUsers >= sub.maxUsers : false
  return (
    <>
      <PageHeader title="Users" actions={<>
        {sub && <span className="muted">Active users: <Usage used={sub.activeUsers} max={sub.maxUsers} /></span>}
        <button className="btn btn-primary" disabled={atLimit} title={atLimit ? 'Plan limit reached' : ''} onClick={() => setEditing('new')}>New user</button>
      </>} />
      <p className="muted">ADMIN: everything. MANAGER: everything except users. CASHIER: sales, invoices, customers; read-only catalog and stock.</p>
      <ErrorBox error={users.error} />
      {users.isLoading ? <Loading /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Since</th></tr></thead>
            <tbody>
              {users.data?.map((u) => (
                <tr key={u.id} className="clickable" onClick={() => setEditing(u)}>
                  <td>{u.fullName}</td><td>{u.email}</td><td><Badge value={u.role} /></td><td><Badge value={u.enabled} /></td><td>{date(u.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <UserForm user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}

function UserForm({ user, onClose }: { user: User | null; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState({ email: user?.email ?? '', fullName: user?.fullName ?? '', role: user?.role ?? 'CASHIER' as Role, enabled: user?.enabled ?? true, password: '' })
  const save = useMutation({
    mutationFn: async () => {
      if (user) {
        await api.put(`/api/users/${user.id}`, { fullName: form.fullName, role: form.role, enabled: form.enabled })
        if (form.password) await api.post(`/api/users/${user.id}/password`, { password: form.password })
      } else {
        await api.post('/api/users', { email: form.email, fullName: form.fullName, role: form.role, password: form.password })
      }
    },
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['users'] }); queryClient.invalidateQueries({ queryKey: ['subscription'] }); onClose() },
  })
  return (
    <Modal title={user ? `Edit ${user.fullName}` : 'New user'} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Email" error={fieldError(save.error, 'email')}><input type="email" value={form.email} disabled={!!user} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></Field>
          <Field label="Full name" error={fieldError(save.error, 'fullName')}><input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required /></Field>
          <Field label="Role"><select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>{ROLES.map((r) => <option key={r}>{r}</option>)}</select></Field>
          <Field label={user ? 'New password (optional)' : 'Password'} error={fieldError(save.error, 'password')}>
            <input type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required={!user} />
          </Field>
        </div>
        {user && <label className="checkbox"><input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} /> Enabled (can log in)</label>}
        <div className="modal-actions"><span className="spacer" /><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={save.isPending}>Save</button></div>
      </form>
    </Modal>
  )
}
