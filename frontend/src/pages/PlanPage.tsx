import { useAuth } from '../auth/AuthContext'
import { daysLeft, useSubscription } from '../auth/useSubscription'
import { date } from '../components/format'
import { ErrorBox, Loading, PageHeader } from '../components/ui'

const limit = (value: number | null) => (value === null ? 'Unlimited' : String(value))

export function PlanPage() {
  const { data: sub, error, isLoading } = useSubscription()
  const { hasRole } = useAuth()
  if (isLoading) return <Loading />
  if (!sub) return <ErrorBox error={error} />

  return (
    <>
      <PageHeader title="Your plan" />
      <div className="card details">
        <div><small>Current plan</small><strong>{sub.planName}{sub.trialActive ? ' (trial)' : ''}</strong></div>
        {sub.trialActive && <div><small>Trial ends</small>{date(sub.trialEndsAt)} · {daysLeft(sub.trialEndsAt)} days left</div>}
        {sub.trialActive && <div><small>After the trial</small>{sub.plans.find((p) => p.code === sub.basePlanCode)?.name}</div>}
        <div><small>Users</small><Usage used={sub.activeUsers} max={sub.maxUsers} /></div>
        <div><small>Warehouses</small><Usage used={sub.activeWarehouses} max={sub.maxWarehouses} /></div>
        {sub.addons.length > 0 && <div><small>Add-ons</small>{sub.addons.map((a) => sub.allModules.find((m) => m.code === a)?.name).join(', ')}</div>}
      </div>

      <div className="card">
        <h2>Compare plans</h2>
        <table className="table plan-table">
          <thead>
            <tr><th />{sub.plans.map((p) => <th key={p.code} className={p.code === sub.planCode ? 'current' : ''}>{p.name}{p.code === sub.planCode ? ' ✓' : ''}</th>)}</tr>
          </thead>
          <tbody>
            {sub.allModules.map((m) => (
              <tr key={m.code}>
                <td>{m.name}</td>
                {sub.plans.map((p) => <td key={p.code} className={p.code === sub.planCode ? 'current' : ''}>{p.modules.includes(m.code) ? '✓' : '—'}</td>)}
              </tr>
            ))}
            <tr><td>Users</td>{sub.plans.map((p) => <td key={p.code} className={p.code === sub.planCode ? 'current' : ''}>{limit(p.maxUsers)}</td>)}</tr>
            <tr><td>Warehouses</td>{sub.plans.map((p) => <td key={p.code} className={p.code === sub.planCode ? 'current' : ''}>{limit(p.maxWarehouses)}</td>)}</tr>
          </tbody>
        </table>
        <p className="muted">
          {hasRole('ADMIN')
            ? 'To change your plan or add a single module, contact us. Online payment is coming soon.'
            : 'Ask your administrator to change the plan.'}
        </p>
      </div>
    </>
  )
}

export function Usage({ used, max }: { used: number; max: number | null }) {
  const full = max !== null && used >= max
  return <span className={full ? 'warn' : ''}>{used} / {limit(max)}{full ? ' (limit reached)' : ''}</span>
}

/** Shown instead of a page whose module isn't in the plan. */
export function NotInPlan({ module }: { module: string }) {
  return (
    <div className="empty">
      <h2>{module} isn't included in your plan</h2>
      <p>Upgrade your plan or add {module} as an add-on to use it. <a href="/plan">See plans</a></p>
    </div>
  )
}
