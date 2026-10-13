import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../api/types'

const NAV: { to: string; label: string; roles?: Role[] }[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/sales-orders', label: 'Sales' },
  { to: '/invoices', label: 'Invoices' },
  { to: '/customers', label: 'Customers' },
  { to: '/products', label: 'Products' },
  { to: '/stock', label: 'Stock' },
  { to: '/purchase-orders', label: 'Purchasing', roles: ['ADMIN', 'MANAGER'] },
  { to: '/suppliers', label: 'Suppliers', roles: ['ADMIN', 'MANAGER'] },
  { to: '/warehouses', label: 'Warehouses' },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
]

export function Layout() {
  const { user, logout, hasRole } = useAuth()
  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <strong>Retail ERP</strong>
          <small>{user?.tenantName}</small>
        </div>
        <nav>
          {NAV.filter((item) => !item.roles || hasRole(...item.roles)).map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div>{user?.fullName}</div>
          <small className="muted">{user?.role}</small>
          <button className="btn btn-small" onClick={logout}>Log out</button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
