// TypeScript mirrors of the backend DTOs (the Java records in *Dtos.java).

export type Role = 'ADMIN' | 'MANAGER' | 'CASHIER'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Me {
  id: string
  email: string
  fullName: string
  role: Role
  tenantId: string
  tenantName: string
}

export interface AuthResponse {
  token: string
  expiresAt: string
  user: Me
}

export interface User {
  id: string
  email: string
  fullName: string
  role: Role
  enabled: boolean
  createdAt: string
}

export interface Product {
  id: string
  sku: string
  name: string
  description: string | null
  category: string | null
  unit: string
  salePrice: number
  costPrice: number
  taxRate: number
  reorderLevel: number
  active: boolean
}

export interface Warehouse {
  id: string
  code: string
  name: string
  address: string | null
  active: boolean
}

export interface Party {
  id: string
  name: string
  email: string | null
  phone: string | null
  address: string | null
  notes: string | null
  active: boolean
}

export type MovementType = 'PURCHASE_RECEIPT' | 'SALE' | 'SALE_RETURN' | 'ADJUSTMENT' | 'TRANSFER_IN' | 'TRANSFER_OUT'

export interface StockLevel {
  productId: string
  sku: string
  productName: string
  unit: string
  warehouseId: string
  warehouseCode: string
  warehouseName: string
  quantity: number
  reorderLevel: number
}

export interface LowStock {
  productId: string
  sku: string
  productName: string
  unit: string
  onHand: number
  reorderLevel: number
}

export interface Movement {
  id: string
  productId: string
  sku: string
  productName: string
  warehouseId: string
  warehouseCode: string
  quantity: number
  type: MovementType
  referenceType: string | null
  referenceId: string | null
  note: string | null
  createdAt: string
}

export type PurchaseOrderStatus = 'DRAFT' | 'ORDERED' | 'RECEIVED' | 'CANCELLED'

export interface PurchaseOrderSummary {
  id: string
  number: string
  supplierId: string
  supplierName: string
  warehouseId: string
  warehouseCode: string
  status: PurchaseOrderStatus
  orderDate: string
  expectedDate: string | null
  total: number
}

export interface PurchaseOrderLine {
  lineNo: number
  productId: string
  sku: string
  productName: string
  quantity: number
  unitCost: number
  lineTotal: number
}

export interface PurchaseOrder extends PurchaseOrderSummary {
  warehouseName: string
  receivedAt: string | null
  notes: string | null
  lines: PurchaseOrderLine[]
  movements: Movement[]
}

export type SalesOrderStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'

export interface SalesOrderSummary {
  id: string
  number: string
  customerId: string | null
  customerName: string | null
  warehouseId: string
  warehouseCode: string
  status: SalesOrderStatus
  orderDate: string
  total: number
}

export interface SalesOrderLine {
  lineNo: number
  productId: string
  sku: string
  productName: string
  quantity: number
  unitPrice: number
  taxRate: number
  lineSubtotal: number
  lineDiscount: number
  lineTax: number
  lineTotal: number
}

export interface SalesOrder extends SalesOrderSummary {
  warehouseName: string
  confirmedAt: string | null
  notes: string | null
  subtotal: number
  discountPercent: number
  discountTotal: number
  taxTotal: number
  lines: SalesOrderLine[]
  invoiceId: string | null
  invoiceNumber: string | null
  movements: Movement[]
}

export type InvoiceStatus = 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOID'
export type PaymentMethod = 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'OTHER'

export interface InvoiceSummary {
  id: string
  number: string
  salesOrderId: string
  salesOrderNumber: string
  customerId: string | null
  customerName: string | null
  status: InvoiceStatus
  issueDate: string
  dueDate: string
  total: number
  amountPaid: number
  balanceDue: number
}

export interface Payment {
  id: string
  amount: number
  method: PaymentMethod
  reference: string | null
  paidAt: string
}

export interface Invoice extends InvoiceSummary {
  customerEmail: string | null
  customerAddress: string | null
  subtotal: number
  discountTotal: number
  taxTotal: number
  lines: SalesOrderLine[]
  payments: Payment[]
}

export interface DailySales {
  date: string
  total: number
  orders: number
}

export interface Dashboard {
  activeProducts: number
  activeCustomers: number
  activeSuppliers: number
  openPurchaseOrders: number
  salesToday: number
  salesLast30Days: number
  outstandingReceivables: number
  lowStock: LowStock[]
  dailySales: DailySales[]
  recentSales: SalesOrderSummary[]
}

export type Module = 'INVENTORY' | 'SALES' | 'INVOICING' | 'PURCHASING' | 'POINT_OF_SALE'

export interface PlanInfo {
  code: string
  name: string
  maxUsers: number | null
  maxWarehouses: number | null
  modules: Module[]
}

export interface Subscription {
  planCode: string
  planName: string
  basePlanCode: string
  trialActive: boolean
  trialEndsAt: string | null
  modules: Module[]
  addons: Module[]
  maxUsers: number | null
  maxWarehouses: number | null
  activeUsers: number
  activeWarehouses: number
  plans: PlanInfo[]
  allModules: { code: Module; name: string }[]
}

export interface PlatformTenant {
  id: string
  name: string
  createdAt: string
  planCode: string
  trialPlanCode: string | null
  trialEndsAt: string | null
  trialActive: boolean
  addons: Module[]
  activeUsers: number
  activeWarehouses: number
}

export interface Shift {
  id: string
  status: 'OPEN' | 'CLOSED'
  warehouseId: string
  warehouseCode: string
  warehouseName: string
  openedBy: string
  openedByName: string | null
  openedAt: string
  openingCash: number
  closedAt: string | null
  salesCount: number
  salesTotal: number
  payments: { method: PaymentMethod; amount: number }[]
  expectedCash: number
  countedCash: number | null
  difference: number | null
  notes: string | null
}

export interface Receipt {
  salesOrderId: string
  orderNumber: string
  invoiceId: string
  invoiceNumber: string
  shopName: string
  warehouseName: string
  cashierName: string | null
  customerName: string | null
  soldAt: string
  lines: { name: string; quantity: number; unitPrice: number; amount: number }[]
  subtotal: number
  discountTotal: number
  taxTotal: number
  total: number
  paymentMethod: PaymentMethod
  cashReceived: number | null
  change: number
}
