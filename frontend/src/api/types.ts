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
  priceListId?: string | null
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
  promoDiscount: number
  promotionName: string | null
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
  couponCode: string | null
  taxTotal: number
  lines: SalesOrderLine[]
  invoiceId: string | null
  invoiceNumber: string | null
  movements: Movement[]
}

export type InvoiceStatus = 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'CREDITED' | 'VOID'
export type PaymentMethod = 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'OTHER' | 'STORE_CREDIT'

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
  amountCredited: number
  amountRefunded: number
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
  outstandingPayables: number
  lowStock: LowStock[]
  dailySales: DailySales[]
  recentSales: SalesOrderSummary[]
}

export type Module = 'INVENTORY' | 'SALES' | 'INVOICING' | 'PURCHASING' | 'POINT_OF_SALE' | 'REPORTS' | 'RETURNS' | 'SUPPLIER_BILLS' | 'PROMOTIONS' | 'ACCOUNTING' | 'HR_PAYROLL'

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
  cashRefunds: number
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
  lines: { name: string; quantity: number; unitPrice: number; amount: number; promotionName: string | null; promoDiscount: number }[]
  subtotal: number
  discountTotal: number
  taxTotal: number
  total: number
  paymentMethod: PaymentMethod
  cashReceived: number | null
  change: number
}

export type ReportPeriod = 'DAY' | 'WEEK' | 'MONTH'

export interface SalesReportRow {
  period: string | null
  orders: number
  grossSales: number
  discounts: number
  returns: number
  netSales: number
  tax: number
  total: number
  averageSale: number
}

export interface SalesReport {
  from: string
  to: string
  groupBy: ReportPeriod
  rows: SalesReportRow[]
  totals: SalesReportRow
}

export interface ProductReportRow {
  productId: string | null
  sku: string | null
  name: string
  category: string | null
  quantity: number
  netSales: number
  cost: number
  grossProfit: number
  marginPercent: number
}

export interface ProductReport {
  from: string
  to: string
  rows: ProductReportRow[]
  totals: ProductReportRow
}

export interface StockValueReport {
  rows: { productId: string; sku: string; name: string; category: string | null; warehouseId: string; warehouseCode: string; quantity: number; unitCost: number; value: number }[]
  byWarehouse: { name: string; quantity: number; value: number }[]
  byCategory: { name: string; quantity: number; value: number }[]
  totalValue: number
}

export interface ReturnableLine {
  salesOrderLineId: string
  lineNo: number
  productId: string
  sku: string
  productName: string
  sold: number
  returned: number
  returnable: number
  unitPrice: number
  unitRefund: number
}

export interface Returnable {
  salesOrderId: string
  salesOrderNumber: string
  customerId: string | null
  customerName: string | null
  invoiceNumber: string
  invoiceTotal: number
  amountPaid: number
  lines: ReturnableLine[]
}

export interface ReturnSummary {
  id: string
  number: string
  returnDate: string
  salesOrderId: string
  salesOrderNumber: string | null
  customerName: string | null
  total: number
  refundAmount: number
  refundMethod: PaymentMethod | null
}

export interface SalesReturn {
  id: string
  number: string
  returnDate: string
  salesOrderId: string
  salesOrderNumber: string
  invoiceId: string
  invoiceNumber: string
  customerId: string | null
  customerName: string | null
  warehouseCode: string
  reason: string | null
  subtotal: number
  discountTotal: number
  taxTotal: number
  total: number
  refundAmount: number
  refundMethod: PaymentMethod | null
  lines: { productId: string; sku: string; productName: string; quantity: number; restock: boolean; lineSubtotal: number; lineDiscount: number; lineTax: number; lineTotal: number }[]
  createdAt: string
}

export interface StoreCredit {
  customerId: string
  balance: number
  entries: { amount: number; reason: 'RETURN_REFUND' | 'PAYMENT'; referenceId: string | null; note: string | null; createdAt: string }[]
}

export type BillStatus = 'DRAFT' | 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'VOID'
export type SupplierPaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'CHEQUE' | 'CARD' | 'OTHER'

export interface BillSummary {
  id: string
  number: string
  supplierReference: string | null
  supplierId: string
  supplierName: string
  status: BillStatus
  billDate: string
  dueDate: string | null
  total: number
  amountPaid: number
  balanceDue: number
  purchaseOrderNumber: string | null
}

export interface Bill extends Omit<BillSummary, 'purchaseOrderNumber'> {
  description: string | null
  purchaseOrderId: string | null
  purchaseOrderNumber: string | null
  approvedAt: string | null
  expenseAccount: string | null
  subtotal: number
  taxTotal: number
  lines: { lineNo: number; description: string; quantity: number; unitPrice: number; lineTotal: number; taxRate: number; lineTax: number }[]
  payments: { id: string; amount: number; method: SupplierPaymentMethod; reference: string | null; paidAt: string }[]
}

export interface SupplierStatement {
  supplierId: string
  supplierName: string
  totalBilled: number
  totalPaid: number
  balance: number
  entries: { date: string; type: 'BILL' | 'PAYMENT'; billId: string; document: string; reference: string | null; amount: number; balance: number }[]
}

export type PromotionType = 'PERCENT_OFF' | 'AMOUNT_OFF' | 'BUY_X_GET_Y' | 'ORDER_PERCENT_OFF'
export type PromotionScope = 'ALL' | 'CATEGORY' | 'PRODUCT'

export interface Promotion {
  id: string
  name: string
  type: PromotionType
  value: number
  buyQty: number | null
  getQty: number | null
  minOrderAmount: number | null
  scope: PromotionScope
  category: string | null
  productId: string | null
  productName: string | null
  couponCode: string | null
  startsOn: string | null
  endsOn: string | null
  active: boolean
  runningToday: boolean
}

export interface PriceListSummary {
  id: string
  name: string
  active: boolean
}

export interface PriceList extends PriceListSummary {
  items: { productId: string; sku: string; productName: string; normalPrice: number; price: number }[]
}

export type AccountType = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'

export interface Account {
  code: string
  name: string
  type: AccountType
  system: boolean
}

export interface JournalLine {
  lineNo: number
  accountCode: string
  accountName: string
  debit: number
  credit: number
  memo: string | null
}

export interface JournalEntry {
  id: string
  number: string
  entryDate: string
  description: string
  sourceType: string
  sourceId: string | null
  total: number
  lines: JournalLine[]
  createdAt: string
}

export interface AccountBalance {
  code: string
  name: string
  type: AccountType
  debit: number
  credit: number
  balance: number
}

export interface TrialBalance {
  from: string
  to: string
  accounts: AccountBalance[]
  totalDebit: number
  totalCredit: number
}

export interface Ledger {
  code: string
  name: string
  type: AccountType
  from: string
  to: string
  opening: number
  rows: { date: string; entryId: string; entryNumber: string; description: string; debit: number; credit: number; balance: number }[]
  closing: number
}

export interface ProfitAndLoss {
  from: string
  to: string
  income: AccountBalance[]
  totalIncome: number
  expenses: AccountBalance[]
  totalExpenses: number
  netResult: number
}

export interface BalanceSheet {
  date: string
  assets: AccountBalance[]
  totalAssets: number
  liabilities: AccountBalance[]
  totalLiabilities: number
  equity: AccountBalance[]
  currentResult: number
  totalEquity: number
  totalLiabilitiesAndEquity: number
}

export interface VatRow { rate: number; base: number; tax: number }

export interface VatReport {
  from: string
  to: string
  sales: VatRow[]
  collected: number
  purchases: VatRow[]
  deductible: number
  payable: number
}

export type ContractType = 'CDI' | 'CDD' | 'OTHER'
export type PayrollStatus = 'DRAFT' | 'APPROVED' | 'PAID'
export type SalaryPaymentMethod = 'CASH' | 'BANK_TRANSFER'

export interface Employee {
  id: string
  fullName: string
  jobTitle: string | null
  contractType: ContractType
  hireDate: string
  endDate: string | null
  nin: string | null
  nss: string | null
  baseSalary: number
  monthlyAllowance: number
  phone: string | null
  bankAccount: string | null
  notes: string | null
  active: boolean
  createdAt: string
}

export interface PayPreview {
  gross: number
  cnasEmployee: number
  taxableIncome: number
  irg: number
  netPay: number
  cnasEmployer: number
  employerCost: number
}

export interface Payslip {
  id: string
  employeeId: string
  employeeName: string
  jobTitle: string | null
  nss: string | null
  baseSalary: number
  bonus: number
  allowance: number
  gross: number
  cnasBase: number
  cnasEmployee: number
  taxableIncome: number
  irg: number
  netPay: number
  cnasEmployer: number
}

export interface PayrollRunSummary {
  id: string
  number: string
  year: number
  month: number
  status: PayrollStatus
  employees: number
  totalGross: number
  totalNet: number
  totalCnasEmployer: number
  approvedAt: string | null
  paidAt: string | null
}

export interface PayrollRun {
  id: string
  number: string
  year: number
  month: number
  periodEnd: string
  status: PayrollStatus
  paymentMethod: SalaryPaymentMethod | null
  totalGross: number
  totalCnasEmployee: number
  totalIrg: number
  totalNet: number
  totalCnasEmployer: number
  employerCost: number
  approvedAt: string | null
  paidAt: string | null
  payslips: Payslip[]
}

export interface EmployeePayslip {
  runId: string
  runNumber: string
  year: number
  month: number
  status: PayrollStatus
  gross: number
  netPay: number
}
