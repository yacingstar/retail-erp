import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router-dom'
import { platformApi } from '../api/client'
import type { Module, PlanInfo, PlatformTenant } from '../api/types'
import { date } from '../components/format'
import { ErrorBox, Field, Loading, Modal, PageHeader, SearchBox } from '../components/ui'

const MODULE_NAMES: Record<Module, string> = { INVENTORY: 'Inventory', SALES: 'Sales', INVOICING: 'Invoicing', PURCHASING: 'Purchasing', POINT_OF_SALE: 'Point of Sale', REPORTS: 'Reports', RETURNS: 'Returns' }

/** Login for the people who run the platform. Separate token from shop users. */
export function PlatformLoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const login = useMutation({
    mutationFn: () => platformApi.post<{ token: string }>('/api/platform/auth/login', { email, password }),
    onSuccess: (response) => { platformApi.setToken(response.token); navigate('/platform') },
  })
  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={(e: FormEvent) => { e.preventDefault(); login.mutate() }}>
        <h1>Platform admin</h1>
        <p className="muted">Manage shops and their plans</p>
        <ErrorBox error={login.error} />
        <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus /></Field>
        <Field label="Password"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
        <button className="btn btn-primary" disabled={login.isPending}>Sign in</button>
      </form>
    </div>
  )
}

export function PlatformTenantsPage() {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [editing, setEditing] = useState<PlatformTenant | null>(null)
  const loggedIn = platformApi.getToken() !== null
  const logout = () => { platformApi.setToken(null); navigate('/platform/login') }
  platformApi.setUnauthorizedHandler(logout)

  const tenants = useQuery({ queryKey: ['platform', 'tenants'], queryFn: () => platformApi.get<PlatformTenant[]>('/api/platform/tenants'), enabled: loggedIn })
  const plans = useQuery({ queryKey: ['platform', 'plans'], queryFn: () => platformApi.get<PlanInfo[]>('/api/platform/plans'), enabled: loggedIn })
  if (!loggedIn) return <Navigate to="/platform/login" replace />

  const planName = (code: string | null) => plans.data?.find((p) => p.code === code)?.name ?? code
  const rows = (tenants.data ?? []).filter((t) => t.name.toLowerCase().includes(q.toLowerCase()))

  return (
    <div className="platform">
      <PageHeader title="Shops" actions={<button className="btn" onClick={logout}>Log out</button>} />
      <SearchBox value={q} onChange={setQ} placeholder="Search shops…" />
      <ErrorBox error={tenants.error} />
      {tenants.isLoading ? <Loading /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Shop</th><th>Since</th><th>Plan</th><th>Trial</th><th>Add-ons</th><th className="num">Users</th><th className="num">Warehouses</th></tr></thead>
            <tbody>
              {rows.map((t) => (
                <tr key={t.id} className="clickable" onClick={() => setEditing(t)}>
                  <td>{t.name}</td>
                  <td>{date(t.createdAt)}</td>
                  <td>{planName(t.planCode)}</td>
                  <td>{t.trialActive ? `${planName(t.trialPlanCode)} until ${date(t.trialEndsAt)}` : '—'}</td>
                  <td>{t.addons.map((a) => MODULE_NAMES[a] ?? a).join(', ') || '—'}</td>
                  <td className="num">{t.activeUsers}</td>
                  <td className="num">{t.activeWarehouses}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && plans.data && <SubscriptionForm tenant={editing} plans={plans.data} onClose={() => setEditing(null)} />}
    </div>
  )
}

function SubscriptionForm({ tenant, plans, onClose }: { tenant: PlatformTenant; plans: PlanInfo[]; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [planCode, setPlanCode] = useState(tenant.planCode)
  const [trialEnds, setTrialEnds] = useState(tenant.trialActive && tenant.trialEndsAt ? tenant.trialEndsAt.slice(0, 10) : '')
  const [addons, setAddons] = useState<Module[]>(tenant.addons)
  const plan = plans.find((p) => p.code === planCode)
  const allModules = Object.keys(MODULE_NAMES) as Module[]
  const toggle = (m: Module) => setAddons(addons.includes(m) ? addons.filter((a) => a !== m) : [...addons, m])

  const save = useMutation({
    mutationFn: () => platformApi.put(`/api/platform/tenants/${tenant.id}/subscription`, {
      planCode,
      trialEndsAt: trialEnds ? new Date(`${trialEnds}T23:59:59Z`).toISOString() : null,
      addons: addons.filter((a) => !plan?.modules.includes(a)),
    }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['platform', 'tenants'] }); onClose() },
  })

  return (
    <Modal title={tenant.name} onClose={onClose}>
      <form onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="form-grid">
          <Field label="Plan">
            <select value={planCode} onChange={(e) => setPlanCode(e.target.value)}>
              {plans.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
            </select>
          </Field>
          <Field label="Pro trial until (empty = no trial)">
            <input type="date" value={trialEnds} onChange={(e) => setTrialEnds(e.target.value)} />
          </Field>
        </div>
        <Field label="Add-ons (modules on top of the plan)">
          <div className="checks">
            {allModules.map((m) => {
              const inPlan = plan?.modules.includes(m) ?? false
              return (
                <label key={m} className="checkbox">
                  <input type="checkbox" disabled={inPlan} checked={inPlan || addons.includes(m)} onChange={() => toggle(m)} />
                  {MODULE_NAMES[m]}{inPlan ? ' (in plan)' : ''}
                </label>
              )
            })}
          </div>
        </Field>
        <p className="muted">Limits on {plan?.name}: {plan?.maxUsers ?? 'unlimited'} users, {plan?.maxWarehouses ?? 'unlimited'} warehouses.</p>
        <div className="modal-actions"><span className="spacer" /><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={save.isPending}>Save</button></div>
      </form>
    </Modal>
  )
}
