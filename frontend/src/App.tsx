import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import type { Module, Role } from './api/types'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import { Loading } from './components/ui'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { DashboardPage } from './pages/DashboardPage'
import { InvoiceDetailPage, InvoicesPage } from './pages/InvoicePages'
import { NotInPlan, PlanPage } from './pages/PlanPage'
import { ReportsPage } from './pages/ReportsPage'
import { NewReturnPage, ReturnDetailPage, ReturnsPage } from './pages/ReturnPages'
import { BillDetailPage, BillFormPage, BillsPage, SupplierStatementPage } from './pages/BillPages'
import { PriceListEditPage, PriceListsPage, PromotionsPage } from './pages/PromotionPages'
import { PlatformLoginPage, PlatformTenantsPage } from './pages/PlatformPages'
import { useSubscription } from './auth/useSubscription'
import { PartiesPage } from './pages/PartiesPage'
import { PosPage, ShiftsPage } from './pages/PosPage'
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

/** Shows an upgrade message instead of a page whose module isn't in the plan. (The API enforces it too.) */
function RequireModule({ module, name, children }: { module: Module; name: string; children: ReactNode }) {
  const { data, has, isLoading } = useSubscription()
  if (isLoading) return <Loading />
  if (data && !has(module)) return <NotInPlan module={name} />
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
        <Route path="suppliers" element={<RequireAuth roles={MANAGE}><RequireModule module="PURCHASING" name="Purchasing"><PartiesPage kind="suppliers" /></RequireModule></RequireAuth>} />
        <Route path="purchase-orders" element={<RequireAuth roles={MANAGE}><RequireModule module="PURCHASING" name="Purchasing"><PurchaseOrdersPage /></RequireModule></RequireAuth>} />
        <Route path="purchase-orders/new" element={<RequireAuth roles={MANAGE}><RequireModule module="PURCHASING" name="Purchasing"><PurchaseOrderFormPage /></RequireModule></RequireAuth>} />
        <Route path="purchase-orders/:id" element={<RequireAuth roles={MANAGE}><RequireModule module="PURCHASING" name="Purchasing"><PurchaseOrderDetailPage /></RequireModule></RequireAuth>} />
        <Route path="purchase-orders/:id/edit" element={<RequireAuth roles={MANAGE}><RequireModule module="PURCHASING" name="Purchasing"><PurchaseOrderFormPage /></RequireModule></RequireAuth>} />
        <Route path="sales-orders" element={<SalesOrdersPage />} />
        <Route path="sales-orders/new" element={<SalesOrderFormPage />} />
        <Route path="sales-orders/:id" element={<SalesOrderDetailPage />} />
        <Route path="sales-orders/:id/edit" element={<SalesOrderFormPage />} />
        <Route path="invoices" element={<InvoicesPage />} />
        <Route path="invoices/:id" element={<InvoiceDetailPage />} />
        <Route path="users" element={<RequireAuth roles={['ADMIN']}><UsersPage /></RequireAuth>} />
        <Route path="plan" element={<PlanPage />} />
        <Route path="promotions" element={<RequireModule module="PROMOTIONS" name="Promotions"><PromotionsPage /></RequireModule>} />
        <Route path="price-lists" element={<RequireAuth roles={MANAGE}><RequireModule module="PROMOTIONS" name="Promotions"><PriceListsPage /></RequireModule></RequireAuth>} />
        <Route path="price-lists/:id" element={<RequireAuth roles={MANAGE}><RequireModule module="PROMOTIONS" name="Promotions"><PriceListEditPage /></RequireModule></RequireAuth>} />
        <Route path="bills" element={<RequireAuth roles={MANAGE}><RequireModule module="SUPPLIER_BILLS" name="Supplier bills"><BillsPage /></RequireModule></RequireAuth>} />
        <Route path="bills/new" element={<RequireAuth roles={MANAGE}><RequireModule module="SUPPLIER_BILLS" name="Supplier bills"><BillFormPage /></RequireModule></RequireAuth>} />
        <Route path="bills/:id" element={<RequireAuth roles={MANAGE}><RequireModule module="SUPPLIER_BILLS" name="Supplier bills"><BillDetailPage /></RequireModule></RequireAuth>} />
        <Route path="bills/:id/edit" element={<RequireAuth roles={MANAGE}><RequireModule module="SUPPLIER_BILLS" name="Supplier bills"><BillFormPage /></RequireModule></RequireAuth>} />
        <Route path="suppliers/:id/statement" element={<RequireAuth roles={MANAGE}><RequireModule module="SUPPLIER_BILLS" name="Supplier bills"><SupplierStatementPage /></RequireModule></RequireAuth>} />
        <Route path="returns" element={<RequireModule module="RETURNS" name="Returns"><ReturnsPage /></RequireModule>} />
        <Route path="returns/new" element={<RequireModule module="RETURNS" name="Returns"><NewReturnPage /></RequireModule>} />
        <Route path="returns/:id" element={<RequireModule module="RETURNS" name="Returns"><ReturnDetailPage /></RequireModule>} />
        <Route path="reports" element={<RequireAuth roles={MANAGE}><RequireModule module="REPORTS" name="Reports"><ReportsPage /></RequireModule></RequireAuth>} />
        <Route path="pos" element={<RequireModule module="POINT_OF_SALE" name="Point of Sale"><PosPage /></RequireModule>} />
        <Route path="pos/shifts" element={<RequireAuth roles={MANAGE}><RequireModule module="POINT_OF_SALE" name="Point of Sale"><ShiftsPage /></RequireModule></RequireAuth>} />
      </Route>
      <Route path="/platform/login" element={<PlatformLoginPage />} />
      <Route path="/platform" element={<PlatformTenantsPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
