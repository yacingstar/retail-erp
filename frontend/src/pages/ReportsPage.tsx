import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api, query } from '../api/client'
import type { ProductReport, ReportPeriod, SalesReport, StockValueReport, Warehouse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { downloadCsv } from '../components/csv'
import { iso, preset } from '../components/dates'
import { date, money } from '../components/format'
import { Empty, ErrorBox, Loading, PageHeader } from '../components/ui'

type Tab = 'sales' | 'products' | 'stock'

export function ReportsPage() {
  const [tab, setTab] = useState<Tab>('sales')
  const [range, setRange] = useState<[string, string]>(() => preset('thisMonth'))
  const { user } = useAuth()

  return (
    <>
      <PageHeader title="Reports" actions={<button className="btn" onClick={() => window.print()}>Print / PDF</button>} />
      <div className="print-only report-print-header">
        <strong>{user?.tenantName}</strong> · {tab === 'stock' ? `Stock value on ${date(new Date().toISOString())}` : `${date(range[0])} – ${date(range[1])}`}
      </div>
      <div className="toolbar no-print">
        <div className="tabs">
          <button className={tab === 'sales' ? 'active' : ''} onClick={() => setTab('sales')}>Sales by period</button>
          <button className={tab === 'products' ? 'active' : ''} onClick={() => setTab('products')}>Products &amp; profit</button>
          <button className={tab === 'stock' ? 'active' : ''} onClick={() => setTab('stock')}>Stock value</button>
        </div>
        {tab !== 'stock' && (
          <div className="actions">
            <select onChange={(e) => setRange(preset(e.target.value))} defaultValue="thisMonth" aria-label="Period">
              <option value="thisMonth">This month</option>
              <option value="last30">Last 30 days</option>
              <option value="lastMonth">Last month</option>
              <option value="thisYear">This year</option>
            </select>
            <input type="date" value={range[0]} onChange={(e) => setRange([e.target.value, range[1]])} aria-label="From" />
            <input type="date" value={range[1]} onChange={(e) => setRange([range[0], e.target.value])} aria-label="To" />
          </div>
        )}
      </div>
      {tab === 'sales' && <SalesTab from={range[0]} to={range[1]} />}
      {tab === 'products' && <ProductsTab from={range[0]} to={range[1]} />}
      {tab === 'stock' && <StockTab />}
    </>
  )
}

function SalesTab({ from, to }: { from: string; to: string }) {
  const [groupBy, setGroupBy] = useState<ReportPeriod>('DAY')
  const report = useQuery({
    queryKey: ['reports', 'sales', from, to, groupBy],
    queryFn: () => api.get<SalesReport>(`/api/reports/sales${query({ from, to, groupBy })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  const r = report.data
  const exportCsv = () => r && downloadCsv(`sales_${from}_${to}.csv`,
    ['Period', 'Orders', 'Gross sales', 'Discounts', 'Returns', 'Net sales', 'Tax', 'Total', 'Average sale'],
    r.rows.map((x) => [x.period, x.orders, x.grossSales, x.discounts, x.returns, x.netSales, x.tax, x.total, x.averageSale]))
  const periodLabel = (p: string | null) => {
    if (!p) return ''
    const d = new Date(`${p}T00:00:00`)
    if (groupBy === 'MONTH') return d.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
    if (groupBy === 'WEEK') return `Week of ${d.toLocaleDateString()}`
    return d.toLocaleDateString()
  }

  return (
    <div className="card">
      <div className="toolbar no-print">
        <div className="tabs">
          {(['DAY', 'WEEK', 'MONTH'] as ReportPeriod[]).map((g) => (
            <button key={g} className={groupBy === g ? 'active' : ''} onClick={() => setGroupBy(g)}>{g === 'DAY' ? 'Daily' : g === 'WEEK' ? 'Weekly' : 'Monthly'}</button>
          ))}
        </div>
        <button className="btn" onClick={exportCsv} disabled={!r?.rows.length}>Export CSV</button>
      </div>
      <ErrorBox error={report.error} />
      {report.isLoading ? <Loading /> : !r || r.rows.length === 0 ? <Empty>No confirmed sales in this period.</Empty> : (
        <table className="table">
          <thead><tr><th>Period</th><th className="num">Orders</th><th className="num">Gross sales</th><th className="num">Discounts</th><th className="num">Returns</th><th className="num">Net sales</th><th className="num">Tax</th><th className="num">Total</th><th className="num">Avg. sale</th></tr></thead>
          <tbody>
            {r.rows.map((x) => (
              <tr key={x.period}>
                <td>{periodLabel(x.period)}</td><td className="num">{x.orders}</td><td className="num">{money(x.grossSales)}</td>
                <td className="num">{money(x.discounts)}</td><td className="num">{money(x.returns)}</td><td className="num">{money(x.netSales)}</td><td className="num">{money(x.tax)}</td>
                <td className="num">{money(x.total)}</td><td className="num">{money(x.averageSale)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="total-row">
              <td>Total</td><td className="num">{r.totals.orders}</td><td className="num">{money(r.totals.grossSales)}</td>
              <td className="num">{money(r.totals.discounts)}</td><td className="num">{money(r.totals.returns)}</td><td className="num">{money(r.totals.netSales)}</td><td className="num">{money(r.totals.tax)}</td>
              <td className="num">{money(r.totals.total)}</td><td className="num">{money(r.totals.averageSale)}</td>
            </tr>
          </tfoot>
        </table>
      )}
      <p className="muted small-note">Net sales = gross − discounts − returns, before tax. Returns count on the day they happen. Cancelled sales are not counted.</p>
    </div>
  )
}

function ProductsTab({ from, to }: { from: string; to: string }) {
  const [sortBy, setSortBy] = useState<'QUANTITY' | 'REVENUE' | 'PROFIT'>('QUANTITY')
  const report = useQuery({
    queryKey: ['reports', 'products', from, to, sortBy],
    queryFn: () => api.get<ProductReport>(`/api/reports/products${query({ from, to, sortBy })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  const r = report.data
  const exportCsv = () => r && downloadCsv(`products_${from}_${to}.csv`,
    ['SKU', 'Product', 'Category', 'Quantity sold', 'Net sales', 'Cost', 'Gross profit', 'Margin %'],
    r.rows.map((x) => [x.sku, x.name, x.category, x.quantity, x.netSales, x.cost, x.grossProfit, x.marginPercent]))

  return (
    <div className="card">
      <div className="toolbar no-print">
        <div className="tabs">
          <button className={sortBy === 'QUANTITY' ? 'active' : ''} onClick={() => setSortBy('QUANTITY')}>Best sellers</button>
          <button className={sortBy === 'REVENUE' ? 'active' : ''} onClick={() => setSortBy('REVENUE')}>By revenue</button>
          <button className={sortBy === 'PROFIT' ? 'active' : ''} onClick={() => setSortBy('PROFIT')}>By profit</button>
        </div>
        <button className="btn" onClick={exportCsv} disabled={!r?.rows.length}>Export CSV</button>
      </div>
      {r && r.rows.length > 0 && (
        <div className="stats report-stats">
          <div className="stat"><small>Net sales</small><strong>{money(r.totals.netSales)}</strong></div>
          <div className="stat"><small>Cost of goods sold</small><strong>{money(r.totals.cost)}</strong></div>
          <div className="stat"><small>Gross profit</small><strong>{money(r.totals.grossProfit)}</strong></div>
          <div className="stat"><small>Margin</small><strong>{r.totals.marginPercent}%</strong></div>
        </div>
      )}
      <ErrorBox error={report.error} />
      {report.isLoading ? <Loading /> : !r || r.rows.length === 0 ? <Empty>No products sold in this period.</Empty> : (
        <table className="table">
          <thead><tr><th>#</th><th>Product</th><th>Category</th><th className="num">Qty sold</th><th className="num">Net sales</th><th className="num">Cost</th><th className="num">Gross profit</th><th className="num">Margin</th></tr></thead>
          <tbody>
            {r.rows.map((x, i) => (
              <tr key={x.productId}>
                <td>{i + 1}</td><td>{x.sku} · {x.name}</td><td>{x.category}</td><td className="num">{x.quantity}</td>
                <td className="num">{money(x.netSales)}</td><td className="num">{money(x.cost)}</td>
                <td className={`num ${x.grossProfit < 0 ? 'neg' : ''}`}>{money(x.grossProfit)}</td><td className="num">{x.marginPercent}%</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="total-row">
              <td /><td>Total</td><td /><td className="num">{r.totals.quantity}</td><td className="num">{money(r.totals.netSales)}</td>
              <td className="num">{money(r.totals.cost)}</td><td className="num">{money(r.totals.grossProfit)}</td><td className="num">{r.totals.marginPercent}%</td>
            </tr>
          </tfoot>
        </table>
      )}
      <p className="muted small-note">Net of returns. Cost uses each product's cost price at the time of the sale (returned items put back in stock reverse their cost). Margin = gross profit ÷ net sales.</p>
    </div>
  )
}

function StockTab() {
  const [warehouseId, setWarehouseId] = useState('')
  const warehouses = useQuery({ queryKey: ['warehouses'], queryFn: () => api.get<Warehouse[]>('/api/warehouses') })
  const report = useQuery({
    queryKey: ['reports', 'stock-value', warehouseId],
    queryFn: () => api.get<StockValueReport>(`/api/reports/stock-value${query({ warehouseId })}`),
    placeholderData: keepPreviousData,
  })
  const r = report.data
  const exportCsv = () => r && downloadCsv(`stock_value_${iso(new Date())}.csv`,
    ['SKU', 'Product', 'Category', 'Warehouse', 'Quantity', 'Unit cost', 'Value'],
    r.rows.map((x) => [x.sku, x.name, x.category, x.warehouseCode, x.quantity, x.unitCost, x.value]))

  return (
    <>
      <div className="toolbar no-print">
        <select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} aria-label="Warehouse">
          <option value="">All warehouses</option>
          {warehouses.data?.map((w) => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}
        </select>
        <button className="btn" onClick={exportCsv} disabled={!r?.rows.length}>Export CSV</button>
      </div>
      <ErrorBox error={report.error} />
      {report.isLoading ? <Loading /> : !r || r.rows.length === 0 ? <Empty>No stock on hand.</Empty> : (
        <>
          <div className="grid-2">
            <GroupTable title="By warehouse" rows={r.byWarehouse} />
            <GroupTable title="By category" rows={r.byCategory} />
          </div>
          <div className="card">
            <h2>Total stock value: {money(r.totalValue)}</h2>
            <table className="table">
              <thead><tr><th>Product</th><th>Category</th><th>Warehouse</th><th className="num">Qty</th><th className="num">Unit cost</th><th className="num">Value</th></tr></thead>
              <tbody>
                {r.rows.map((x) => (
                  <tr key={x.productId + x.warehouseId}>
                    <td>{x.sku} · {x.name}</td><td>{x.category}</td><td>{x.warehouseCode}</td><td className="num">{x.quantity}</td>
                    <td className="num">{money(x.unitCost)}</td><td className="num">{money(x.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="muted small-note">Valued at each product's current cost price.</p>
          </div>
        </>
      )}
    </>
  )
}

function GroupTable({ title, rows }: { title: string; rows: { name: string; quantity: number; value: number }[] }) {
  return (
    <div className="card">
      <h2>{title}</h2>
      <table className="table">
        <thead><tr><th>{title.replace('By ', '').replace(/^\w/, (c) => c.toUpperCase())}</th><th className="num">Units</th><th className="num">Value</th></tr></thead>
        <tbody>{rows.map((g) => <tr key={g.name}><td>{g.name}</td><td className="num">{g.quantity}</td><td className="num">{money(g.value)}</td></tr>)}</tbody>
      </table>
    </div>
  )
}
