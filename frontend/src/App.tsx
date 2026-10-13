import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import type { Role } from './api/types'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { Loading } from './components/ui'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { DashboardPage } from './pages/DashboardPage'
import { InvoiceDetailPage, InvoicesPage } from './pages/InvoicePages'
import { PartiesPage } from './pages/PartiesPage'
import { ProductsPage } from './pages/ProductsPage'
import { PurchaseOrderDetailPage, PurchaseOrderFormPage, PurchaseOrdersPage } from './pages/PurchaseOrderPages'
import { SalesOrderDetailPage, SalesOrderFormPage, SalesOrdersPage } from './pages/SalesOrderPages'
import { StockPage } from './pages/StockPage'
import { UsersPage } from './pages/UsersPage'
import { WarehousesPage } from './pages/WarehousesPage'

/** Route guard: not logged in → login page; wrong role → dashboard. (The backend enforces this too; this is just UX.) */
function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading, hasRole } = useAuth()
  if (loading) return <Loading />
  if (!user) return <Navigate to="/login" replace />
  if (roles && !hasRole(...roles)) return <Navigate to="/" replace />
  return children
}

const MANAGE: Role[] = ['ADMIN', 'MANAGER']

export function App() {
  const { user } = useAuth()
  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/register" element={user ? <Navigate to="/" replace /> : <RegisterPage />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="warehouses" element={<WarehousesPage />} />
        <Route path="stock" element={<StockPage />} />
        <Route path="customers" element={<PartiesPage kind="customers" />} />
        <Route path="suppliers" element={<RequireAuth roles={MANAGE}><PartiesPage kind="suppliers" /></RequireAuth>} />
        <Route path="purchase-orders" element={<RequireAuth roles={MANAGE}><PurchaseOrdersPage /></RequireAuth>} />
        <Route path="purchase-orders/new" element={<RequireAuth roles={MANAGE}><PurchaseOrderFormPage /></RequireAuth>} />
        <Route path="purchase-orders/:id" element={<RequireAuth roles={MANAGE}><PurchaseOrderDetailPage /></RequireAuth>} />
        <Route path="purchase-orders/:id/edit" element={<RequireAuth roles={MANAGE}><PurchaseOrderFormPage /></RequireAuth>} />
        <Route path="sales-orders" element={<SalesOrdersPage />} />
        <Route path="sales-orders/new" element={<SalesOrderFormPage />} />
        <Route path="sales-orders/:id" element={<SalesOrderDetailPage />} />
        <Route path="sales-orders/:id/edit" element={<SalesOrderFormPage />} />
        <Route path="invoices" element={<InvoicesPage />} />
        <Route path="invoices/:id" element={<InvoiceDetailPage />} />
        <Route path="users" element={<RequireAuth roles={['ADMIN']}><UsersPage /></RequireAuth>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
