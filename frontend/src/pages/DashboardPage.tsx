import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { Dashboard } from '../api/types'
import { date, money } from '../components/format'
import { Badge, Empty, ErrorBox, Loading, PageHeader } from '../components/ui'

export function DashboardPage() {
  const { data, error, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/api/dashboard') })
  if (isLoading) return <Loading />
  if (error || !data) return <ErrorBox error={error} />

  const max = Math.max(1, ...data.dailySales.map((d) => d.total))
  return (
    <>
      <PageHeader title="Dashboard" actions={<Link className="btn btn-primary" to="/sales-orders/new">New sale</Link>} />
      <div className="stats">
        <Stat label="Sales today" value={money(data.salesToday)} />
        <Stat label="Sales (30 days)" value={money(data.salesLast30Days)} />
        <Stat label="Outstanding invoices" value={money(data.outstandingReceivables)} />
        <Stat label="Open purchase orders" value={String(data.openPurchaseOrders)} />
        <Stat label="Active products" value={String(data.activeProducts)} />
        <Stat label="Customers" value={String(data.activeCustomers)} />
      </div>

      <div className="grid-2">
        <section className="card">
          <h2>Sales, last 14 days</h2>
          {data.dailySales.length === 0 ? <Empty>No confirmed sales yet.</Empty> : (
            <div className="bars">
              {data.dailySales.map((d) => (
                <div key={d.date} className="bar-row">
                  <span>{date(d.date)}</span>
                  <div className="bar"><div style={{ width: `${(d.total / max) * 100}%` }} /></div>
                  <span className="num">{money(d.total)}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card">
          <h2>Low stock</h2>
          {data.lowStock.length === 0 ? <Empty>Nothing below its reorder level.</Empty> : (
            <table className="table">
              <thead><tr><th>Product</th><th className="num">On hand</th><th className="num">Reorder at</th></tr></thead>
              <tbody>
                {data.lowStock.map((p) => (
                  <tr key={p.productId}><td>{p.sku} · {p.productName}</td><td className="num warn">{p.onHand}</td><td className="num">{p.reorderLevel}</td></tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <section className="card">
        <h2>Recent sales</h2>
        {data.recentSales.length === 0 ? <Empty>No sales yet. <Link to="/sales-orders/new">Record the first one</Link>.</Empty> : (
          <table className="table">
            <thead><tr><th>Number</th><th>Customer</th><th>Date</th><th>Status</th><th className="num">Total</th></tr></thead>
            <tbody>
              {data.recentSales.map((s) => (
                <tr key={s.id}>
                  <td><Link to={`/sales-orders/${s.id}`}>{s.number}</Link></td>
                  <td>{s.customerName ?? 'Walk-in'}</td><td>{date(s.orderDate)}</td><td><Badge value={s.status} /></td>
                  <td className="num">{money(s.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return <div className="card stat"><small>{label}</small><strong>{value}</strong></div>
}
