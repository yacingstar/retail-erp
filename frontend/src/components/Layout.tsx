import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { daysLeft, useSubscription } from '../auth/useSubscription'
import type { Module, Role } from '../api/types'

const NAV: { to: string; label: string; roles?: Role[]; module?: Module }[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/pos', label: 'Point of Sale', module: 'POINT_OF_SALE' },
  { to: '/sales-orders', label: 'Sales', module: 'SALES' },
  { to: '/invoices', label: 'Invoices', module: 'INVOICING' },
  { to: '/returns', label: 'Returns', module: 'RETURNS' },
  { to: '/customers', label: 'Customers', module: 'SALES' },
  { to: '/products', label: 'Products', module: 'INVENTORY' },
  { to: '/stock', label: 'Stock', module: 'INVENTORY' },
  { to: '/purchase-orders', label: 'Purchasing', roles: ['ADMIN', 'MANAGER'], module: 'PURCHASING' },
  { to: '/suppliers', label: 'Suppliers', roles: ['ADMIN', 'MANAGER'], module: 'PURCHASING' },
  { to: '/bills', label: 'Supplier bills', roles: ['ADMIN', 'MANAGER'], module: 'SUPPLIER_BILLS' },
  { to: '/promotions', label: 'Promotions', module: 'PROMOTIONS' },
  { to: '/price-lists', label: 'Price lists', roles: ['ADMIN', 'MANAGER'], module: 'PROMOTIONS' },
  { to: '/warehouses', label: 'Warehouses', module: 'INVENTORY' },
  { to: '/reports', label: 'Reports', roles: ['ADMIN', 'MANAGER'], module: 'REPORTS' },
  { to: '/accounting', label: 'Accounting', roles: ['ADMIN', 'MANAGER'], module: 'ACCOUNTING' },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
  { to: '/plan', label: 'Plan' },
]

export function Layout() {
  const { user, logout, hasRole } = useAuth()
  const subscription = useSubscription()
  const sub = subscription.data
  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <strong>Retail ERP</strong>
          <small>{user?.tenantName}</small>
        </div>
        <nav>
          {NAV.filter((item) => !item.roles || hasRole(...item.roles))
            .filter((item) => !item.module || !sub || subscription.has(item.module))
            .map((item) => (
              <NavLink key={item.to} to={item.to} end={item.to === '/'}>
                {item.label}
              </NavLink>
            ))}
        </nav>
        <div className="sidebar-footer">
          {sub && <Link to="/plan" className="plan-chip">{sub.planName}{sub.trialActive ? ' trial' : ''}</Link>}
          <div>{user?.fullName}</div>
          <small className="muted">{user?.role}</small>
          <button className="btn btn-small" onClick={logout}>Log out</button>
        </div>
      </aside>
      <main className="content">
        {sub?.trialActive && (
          <div className="trial-banner">
            You're on a free <strong>{sub.planName}</strong> trial: {daysLeft(sub.trialEndsAt)} days left.
            After that you'll move to the {sub.plans.find((p) => p.code === sub.basePlanCode)?.name ?? sub.basePlanCode} plan. <Link to="/plan">See plans</Link>
          </div>
        )}
        <Outlet />
      </main>
    </div>
  )
}
