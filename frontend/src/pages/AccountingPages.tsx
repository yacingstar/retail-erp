import { useState, type FormEvent } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, query } from '../api/client'
import type { Account, AccountBalance, AccountType, BalanceSheet, JournalEntry, Ledger, Page, ProfitAndLoss, TrialBalance, VatReport, VatRow } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { downloadCsv } from '../components/csv'
import { iso, preset } from '../components/dates'
import { date, money } from '../components/format'
import { Empty, ErrorBox, Field, fieldError, Loading, PageHeader, Pagination } from '../components/ui'

type Tab = 'journal' | 'accounts' | 'trial' | 'pl' | 'balance' | 'vat'

const TABS: { key: Tab; label: string }[] = [
  { key: 'journal', label: 'Journal' },
  { key: 'accounts', label: 'Chart of accounts' },
  { key: 'trial', label: 'Trial balance' },
  { key: 'pl', label: 'Profit & loss' },
  { key: 'balance', label: 'Balance sheet' },
  { key: 'vat', label: 'TVA' },
]

const TYPE_NAMES: Record<AccountType, string> = { ASSET: 'Assets', LIABILITY: 'Liabilities', EQUITY: 'Equity', INCOME: 'Income', EXPENSE: 'Expenses' }

/** Human names for the automatic entries' sources, and where their document lives. */
const SOURCES: Record<string, { name: string; path?: string }> = {
  SALE: { name: 'Sale', path: '/sales-orders' },
  SALE_COST: { name: 'Cost of sale', path: '/sales-orders' },
  SALE_CANCEL: { name: 'Sale cancelled', path: '/sales-orders' },
  SALE_CANCEL_COST: { name: 'Sale cancelled (cost)', path: '/sales-orders' },
  CUSTOMER_PAYMENT: { name: 'Customer payment' },
  RETURN: { name: 'Return', path: '/returns' },
  RETURN_COST: { name: 'Return (restock)', path: '/returns' },
  REFUND: { name: 'Refund', path: '/returns' },
  GOODS_RECEIPT: { name: 'Goods received', path: '/purchase-orders' },
  SUPPLIER_BILL: { name: 'Supplier bill', path: '/bills' },
  SUPPLIER_BILL_VOID: { name: 'Bill voided', path: '/bills' },
  SUPPLIER_PAYMENT: { name: 'Supplier payment' },
  STOCK_ADJUSTMENT: { name: 'Stock adjustment' },
  PAYROLL: { name: 'Payroll', path: '/payroll' },
  PAYROLL_PAYMENT: { name: 'Salaries paid', path: '/payroll' },
  MANUAL: { name: 'Manual entry' },
}

const sourceName = (type: string) => SOURCES[type]?.name ?? type

/** Debit/credit cell: blank instead of 0.00, so the side that matters stands out. */
const amount = (value: number) => (Number(value) === 0 ? '' : money(value))

export function AccountingPage() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') as Tab) || 'journal'
  const [range, setRange] = useState<[string, string]>(() => preset('thisMonth'))
  const { user, hasRole } = useAuth()
  const dated = tab !== 'accounts'

  return (
    <>
      <PageHeader title="Accounting" actions={<>
        {hasRole('ADMIN') && <Link className="btn" to="/accounting/journal/new">New journal entry</Link>}
        <button className="btn" onClick={() => window.print()}>Print / PDF</button>
      </>} />
      <div className="print-only report-print-header">
        <strong>{user?.tenantName}</strong> · {TABS.find((t) => t.key === tab)?.label} ·{' '}
        {tab === 'balance' ? `on ${date(range[1])}` : `${date(range[0])} – ${date(range[1])}`}
      </div>
      <div className="toolbar no-print">
        <div className="tabs">
          {TABS.map((t) => <button key={t.key} className={tab === t.key ? 'active' : ''} onClick={() => setParams({ tab: t.key }, { replace: true })}>{t.label}</button>)}
        </div>
        {dated && <PeriodPicker range={range} onChange={setRange} singleDate={tab === 'balance'} />}
      </div>
      {tab === 'journal' && <JournalTab from={range[0]} to={range[1]} />}
      {tab === 'accounts' && <AccountsTab />}
      {tab === 'trial' && <TrialBalanceTab from={range[0]} to={range[1]} />}
      {tab === 'pl' && <ProfitAndLossTab from={range[0]} to={range[1]} />}
      {tab === 'balance' && <BalanceSheetTab date={range[1]} />}
      {tab === 'vat' && <VatTab from={range[0]} to={range[1]} />}
    </>
  )
}

function PeriodPicker({ range, onChange, singleDate }: { range: [string, string]; onChange: (r: [string, string]) => void; singleDate?: boolean }) {
  if (singleDate) {
    return (
      <div className="actions">
        <label className="muted">On</label>
        <input type="date" value={range[1]} onChange={(e) => onChange([range[0], e.target.value])} aria-label="Date" />
      </div>
    )
  }
  return (
    <div className="actions">
      <select onChange={(e) => onChange(preset(e.target.value))} defaultValue="thisMonth" aria-label="Period">
        <option value="thisMonth">This month</option>
        <option value="last30">Last 30 days</option>
        <option value="lastMonth">Last month</option>
        <option value="thisYear">This year</option>
      </select>
      <input type="date" value={range[0]} onChange={(e) => onChange([e.target.value, range[1]])} aria-label="From" />
      <input type="date" value={range[1]} onChange={(e) => onChange([range[0], e.target.value])} aria-label="To" />
    </div>
  )
}

function JournalTab({ from, to }: { from: string; to: string }) {
  const [source, setSource] = useState('')
  const [page, setPage] = useState(0)
  const journal = useQuery({
    queryKey: ['accounting', 'journal', from, to, source, page],
    queryFn: () => api.get<Page<JournalEntry>>(`/api/accounting/journal${query({ from, to, source, page, size: 50 })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  return (
    <div className="card">
      <div className="toolbar no-print">
        <select value={source} onChange={(e) => { setSource(e.target.value); setPage(0) }} aria-label="Source">
          <option value="">All entries</option>
          {Object.entries(SOURCES).map(([key, s]) => <option key={key} value={key}>{s.name}</option>)}
        </select>
      </div>
      <ErrorBox error={journal.error} />
      {journal.isLoading ? <Loading /> : !journal.data || journal.data.content.length === 0 ? <Empty>No journal entries in this period.</Empty> : (
        <>
          <table className="table">
            <thead><tr><th>Date</th><th>Number</th><th>Description</th><th>Source</th><th>Accounts</th><th className="num">Amount</th></tr></thead>
            <tbody>
              {journal.data.content.map((e) => (
                <tr key={e.id}>
                  <td>{date(e.entryDate)}</td>
                  <td><Link to={`/accounting/journal/${e.id}`}>{e.number}</Link></td>
                  <td>{e.description}</td>
                  <td>{sourceName(e.sourceType)}</td>
                  <td className="muted">{e.lines.map((l) => l.accountCode).join(' · ')}</td>
                  <td className="num">{money(e.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={journal.data.totalPages} onChange={setPage} />
        </>
      )}
      <p className="muted small-note">Entries are written automatically by sales, payments, returns, goods receipts, supplier bills and stock adjustments. They can't be edited or deleted: mistakes are fixed with a correcting entry.</p>
    </div>
  )
}

function AccountsTab() {
  const { hasRole } = useAuth()
  const accounts = useQuery({ queryKey: ['accounting', 'accounts'], queryFn: () => api.get<Account[]>('/api/accounting/accounts') })
  return (
    <div className="grid-2">
      <div className="card">
        <ErrorBox error={accounts.error} />
        {accounts.isLoading ? <Loading /> : (
          <table className="table">
            <thead><tr><th>Code</th><th>Name</th><th>Type</th></tr></thead>
            <tbody>
              {accounts.data?.map((a) => (
                <tr key={a.code}>
                  <td><Link to={`/accounting/ledger/${a.code}`}>{a.code}</Link></td>
                  <td>{a.name}{a.system && <span className="muted"> · used automatically</span>}</td>
                  <td>{TYPE_NAMES[a.type]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted small-note">Based on the Algerian Système Comptable Financier (SCF). Click a code to see its ledger.</p>
      </div>
      {hasRole('ADMIN') && <AddAccount />}
    </div>
  )
}

function AddAccount() {
  const queryClient = useQueryClient()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [type, setType] = useState<AccountType>('EXPENSE')
  const add = useMutation({
    mutationFn: () => api.post<Account>('/api/accounting/accounts', { code, name, type }),
    onSuccess: () => { setCode(''); setName(''); queryClient.invalidateQueries({ queryKey: ['accounting', 'accounts'] }) },
  })
  return (
    <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); add.mutate() }}>
      <h2>Add an account</h2>
      <ErrorBox error={add.error} />
      <Field label="Code" error={fieldError(add.error, 'code')}><input value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. 6131" required pattern="[0-9]{2,10}" /></Field>
      <Field label="Name" error={fieldError(add.error, 'name')}><input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Loyer magasin" required /></Field>
      <Field label="Type">
        <select value={type} onChange={(e) => setType(e.target.value as AccountType)}>
          {(Object.keys(TYPE_NAMES) as AccountType[]).map((t) => <option key={t} value={t}>{TYPE_NAMES[t]}</option>)}
        </select>
      </Field>
      <p className="muted small-note">SCF classes: 1 equity &amp; long-term debt, 2 fixed assets, 3 stock, 4 third parties, 5 cash &amp; bank, 6 expenses, 7 income.</p>
      <div className="modal-actions"><span className="spacer" /><button className="btn btn-primary" disabled={add.isPending}>Add account</button></div>
    </form>
  )
}

function TrialBalanceTab({ from, to }: { from: string; to: string }) {
  const tb = useQuery({
    queryKey: ['accounting', 'trial', from, to],
    queryFn: () => api.get<TrialBalance>(`/api/accounting/trial-balance${query({ from, to })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  const rows = tb.data?.accounts.filter((a) => Number(a.debit) !== 0 || Number(a.credit) !== 0) ?? []
  const exportCsv = () => tb.data && downloadCsv(`trial_balance_${from}_${to}.csv`, ['Code', 'Account', 'Debit', 'Credit', 'Balance'],
    rows.map((a) => [a.code, a.name, a.debit, a.credit, a.balance]))
  const balanced = tb.data && Number(tb.data.totalDebit) === Number(tb.data.totalCredit)
  return (
    <div className="card">
      <div className="toolbar no-print">
        <span className={balanced ? 'pos' : 'neg'}>{tb.data && (balanced ? '✓ Debits equal credits' : '✗ Debits and credits differ')}</span>
        <button className="btn" onClick={exportCsv} disabled={!rows.length}>Export CSV</button>
      </div>
      <ErrorBox error={tb.error} />
      {tb.isLoading ? <Loading /> : rows.length === 0 ? <Empty>No entries in this period.</Empty> : (
        <table className="table">
          <thead><tr><th>Code</th><th>Account</th><th className="num">Debit</th><th className="num">Credit</th><th className="num">Balance</th></tr></thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.code}>
                <td><Link to={`/accounting/ledger/${a.code}`}>{a.code}</Link></td><td>{a.name}</td>
                <td className="num">{amount(a.debit)}</td><td className="num">{amount(a.credit)}</td>
                <td className={`num ${a.balance < 0 ? 'neg' : ''}`}>{money(a.balance)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot><tr className="total-row"><td colSpan={2}>Total</td><td className="num">{money(tb.data!.totalDebit)}</td><td className="num">{money(tb.data!.totalCredit)}</td><td /></tr></tfoot>
        </table>
      )}
      <p className="muted small-note">Movements in the period. Balance is shown in the account's normal direction (debit for assets and expenses, credit for the rest); a negative balance is unusual, e.g. an overdrawn bank account.</p>
    </div>
  )
}

/** A titled list of accounts with a total, used by the P&amp;L and the balance sheet. */
function Section({ title, rows, total, extra }: { title: string; rows: AccountBalance[]; total: number; extra?: { label: string; value: number } }) {
  const shown = rows.filter((a) => Number(a.balance) !== 0)
  return (
    <table className="table statement-table">
      <thead><tr><th colSpan={2}>{title}</th><th className="num" /></tr></thead>
      <tbody>
        {shown.map((a) => <tr key={a.code}><td style={{ width: 80 }}><Link to={`/accounting/ledger/${a.code}`}>{a.code}</Link></td><td>{a.name}</td><td className="num">{money(a.balance)}</td></tr>)}
        {extra && <tr><td /><td>{extra.label}</td><td className={`num ${extra.value < 0 ? 'neg' : ''}`}>{money(extra.value)}</td></tr>}
        {shown.length === 0 && !extra && <tr><td colSpan={3} className="muted">Nothing yet.</td></tr>}
      </tbody>
      <tfoot><tr className="total-row"><td colSpan={2}>Total {title.toLowerCase()}</td><td className="num">{money(total)}</td></tr></tfoot>
    </table>
  )
}

function ProfitAndLossTab({ from, to }: { from: string; to: string }) {
  const pl = useQuery({
    queryKey: ['accounting', 'pl', from, to],
    queryFn: () => api.get<ProfitAndLoss>(`/api/accounting/profit-and-loss${query({ from, to })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  if (pl.isLoading) return <Loading />
  if (!pl.data) return <ErrorBox error={pl.error} />
  const r = pl.data
  return (
    <div className="card">
      <Section title="Income" rows={r.income} total={r.totalIncome} />
      <Section title="Expenses" rows={r.expenses} total={r.totalExpenses} />
      <div className="statement-result">
        <span>{r.netResult >= 0 ? 'Net profit' : 'Net loss'}</span>
        <strong className={r.netResult < 0 ? 'neg' : 'pos'}>{money(r.netResult)}</strong>
      </div>
      <p className="muted small-note">Before income tax (IBS). Sales are counted when the order is confirmed, not when it's paid.</p>
    </div>
  )
}

function BalanceSheetTab({ date: on }: { date: string }) {
  const bs = useQuery({
    queryKey: ['accounting', 'balance', on],
    queryFn: () => api.get<BalanceSheet>(`/api/accounting/balance-sheet${query({ date: on })}`),
    enabled: !!on,
    placeholderData: keepPreviousData,
  })
  if (bs.isLoading) return <Loading />
  if (!bs.data) return <ErrorBox error={bs.error} />
  const r = bs.data
  const balanced = Number(r.totalAssets) === Number(r.totalLiabilitiesAndEquity)
  return (
    <>
      <div className="grid-2">
        <div className="card"><Section title="Assets" rows={r.assets} total={r.totalAssets} /></div>
        <div className="card">
          <Section title="Liabilities" rows={r.liabilities} total={r.totalLiabilities} />
          <Section title="Equity" rows={r.equity} total={r.totalEquity} extra={{ label: 'Result of the year so far', value: r.currentResult }} />
          <div className="statement-result"><span>Liabilities + equity</span><strong>{money(r.totalLiabilitiesAndEquity)}</strong></div>
        </div>
      </div>
      <p className={`small-note ${balanced ? 'muted' : 'neg'}`}>
        {balanced ? 'Assets = liabilities + equity, as it always should.' : 'Assets and liabilities + equity differ: please report this.'}
        {' '}Everything up to and including {date(on)}.
      </p>
    </>
  )
}

function VatTab({ from, to }: { from: string; to: string }) {
  const vat = useQuery({
    queryKey: ['accounting', 'vat', from, to],
    queryFn: () => api.get<VatReport>(`/api/accounting/vat${query({ from, to })}`),
    enabled: !!from && !!to,
    placeholderData: keepPreviousData,
  })
  if (vat.isLoading) return <Loading />
  if (!vat.data) return <ErrorBox error={vat.error} />
  const r = vat.data
  const exportCsv = () => downloadCsv(`tva_${from}_${to}.csv`, ['Side', 'Rate %', 'Base (HT)', 'TVA'], [
    ...r.sales.map((x) => ['Collected (sales)', x.rate, x.base, x.tax]),
    ...r.purchases.map((x) => ['Deductible (purchases)', x.rate, x.base, x.tax]),
    ['Payable', '', '', r.payable],
  ])
  const table = (title: string, rows: VatRow[], total: number) => (
    <div className="card">
      <h2>{title}</h2>
      {rows.length === 0 ? <Empty>Nothing in this period.</Empty> : (
        <table className="table">
          <thead><tr><th>Rate</th><th className="num">Base (HT)</th><th className="num">TVA</th></tr></thead>
          <tbody>{rows.map((x) => <tr key={x.rate}><td>{Number(x.rate)}%</td><td className="num">{money(x.base)}</td><td className="num">{money(x.tax)}</td></tr>)}</tbody>
          <tfoot><tr className="total-row"><td colSpan={2}>Total</td><td className="num">{money(total)}</td></tr></tfoot>
        </table>
      )}
    </div>
  )
  return (
    <>
      <div className="toolbar no-print"><span /><button className="btn" onClick={exportCsv}>Export CSV</button></div>
      <div className="grid-2">
        {table('TVA collected (sales − returns)', r.sales, r.collected)}
        {table('TVA deductible (supplier bills)', r.purchases, r.deductible)}
      </div>
      <div className="card statement-result">
        <span>{r.payable >= 0 ? 'TVA to pay for the period' : 'TVA credit to carry forward'}</span>
        <strong className={r.payable > 0 ? 'neg' : 'pos'}>{money(Math.abs(r.payable))}</strong>
      </div>
      <p className="muted small-note">Collected TVA counts on confirmed sales, minus returns. Deductible TVA counts on approved supplier bills, by bill date. Use it to prepare your G50 declaration; check it with your accountant.</p>
    </>
  )
}

export function JournalEntryPage() {
  const { id } = useParams()
  const entry = useQuery({ queryKey: ['accounting', 'entry', id], queryFn: () => api.get<JournalEntry>(`/api/accounting/journal/${id}`) })
  if (entry.isLoading) return <Loading />
  if (!entry.data) return <ErrorBox error={entry.error} />
  const e = entry.data
  const source = SOURCES[e.sourceType]
  return (
    <>
      <PageHeader title={`Journal entry ${e.number}`} actions={<Link className="btn" to="/accounting">Back to journal</Link>} />
      <div className="card details">
        <div><small>Date</small>{date(e.entryDate)}</div>
        <div><small>Description</small>{e.description}</div>
        <div><small>Source</small>{source?.path && e.sourceId ? <Link to={`${source.path}/${e.sourceId}`}>{source.name}</Link> : sourceName(e.sourceType)}</div>
        <div><small>Amount</small>{money(e.total)}</div>
      </div>
      <div className="card">
        <table className="table">
          <thead><tr><th>Account</th><th>Memo</th><th className="num">Debit</th><th className="num">Credit</th></tr></thead>
          <tbody>
            {e.lines.map((l) => (
              <tr key={l.lineNo}>
                <td><Link to={`/accounting/ledger/${l.accountCode}`}>{l.accountCode}</Link> · {l.accountName}</td>
                <td className="muted">{l.memo}</td>
                <td className="num">{amount(l.debit)}</td><td className="num">{amount(l.credit)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot><tr className="total-row"><td colSpan={2}>Total</td><td className="num">{money(e.total)}</td><td className="num">{money(e.total)}</td></tr></tfoot>
        </table>
      </div>
    </>
  )
}

interface EditableLine { accountCode: string; debit: string; credit: string; memo: string }
const NEW_LINE: EditableLine = { accountCode: '', debit: '', credit: '', memo: '' }
const cents = (v: string) => Math.round((Number(v) || 0) * 100)

export function ManualEntryPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: ['accounting', 'accounts'], queryFn: () => api.get<Account[]>('/api/accounting/accounts') })
  const [entryDate, setEntryDate] = useState(iso(new Date()))
  const [description, setDescription] = useState('')
  const [lines, setLines] = useState<EditableLine[]>([{ ...NEW_LINE }, { ...NEW_LINE }])
  const update = (i: number, patch: Partial<EditableLine>) => setLines(lines.map((l, j) => (j === i ? { ...l, ...patch } : l)))
  // Work in cents so 0.1 + 0.2 doesn't make a balanced entry look unbalanced.
  const debit = lines.reduce((s, l) => s + cents(l.debit), 0)
  const credit = lines.reduce((s, l) => s + cents(l.credit), 0)
  const balanced = debit > 0 && debit === credit

  const save = useMutation({
    mutationFn: () => api.post<JournalEntry>('/api/accounting/journal', {
      entryDate, description,
      lines: lines.map((l) => ({ accountCode: l.accountCode, debit: Number(l.debit) || 0, credit: Number(l.credit) || 0, memo: l.memo || null })),
    }),
    onSuccess: (saved) => { queryClient.invalidateQueries({ queryKey: ['accounting'] }); navigate(`/accounting/journal/${saved.id}`) },
  })

  return (
    <>
      <PageHeader title="New journal entry" />
      <form className="card" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <ErrorBox error={save.error} />
        <div className="trial-banner">For things the app doesn't record by itself: the owner's capital, a bank loan, buying equipment, paying salaries or taxes, depositing cash at the bank… Once saved, an entry can't be changed: fix mistakes with a reversing entry.</div>
        <div className="form-grid">
          <Field label="Date"><input type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} required /></Field>
          <Field label="Description" error={fieldError(save.error, 'description')}><input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="e.g. Cash deposited at the bank" required /></Field>
        </div>
        <table className="table">
          <thead><tr><th>Account</th><th style={{ width: 140 }}>Debit</th><th style={{ width: 140 }}>Credit</th><th>Memo</th><th /></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td>
                  <select value={l.accountCode} onChange={(e) => update(i, { accountCode: e.target.value })} required aria-label="Account">
                    <option value="">Select…</option>
                    {accounts.data?.map((a) => <option key={a.code} value={a.code}>{a.code} · {a.name}</option>)}
                  </select>
                </td>
                <td><input type="number" min="0" step="0.01" value={l.debit} onChange={(e) => update(i, { debit: e.target.value, credit: e.target.value ? '' : l.credit })} aria-label="Debit" /></td>
                <td><input type="number" min="0" step="0.01" value={l.credit} onChange={(e) => update(i, { credit: e.target.value, debit: e.target.value ? '' : l.debit })} aria-label="Credit" /></td>
                <td><input value={l.memo} onChange={(e) => update(i, { memo: e.target.value })} aria-label="Memo" /></td>
                <td>{lines.length > 2 && <button type="button" className="btn-icon" onClick={() => setLines(lines.filter((_, j) => j !== i))} aria-label="Remove line">×</button>}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="total-row"><td>Total</td><td className="num">{money(debit / 100)}</td><td className="num">{money(credit / 100)}</td><td colSpan={2} className={balanced ? 'pos' : 'neg'}>
              {balanced ? '✓ Balanced' : debit === credit ? 'Enter the amounts' : `Difference: ${money(Math.abs(debit - credit) / 100)}`}
            </td></tr>
          </tfoot>
        </table>
        <button type="button" className="btn btn-small" onClick={() => setLines([...lines, { ...NEW_LINE }])}>+ Add line</button>
        <div className="modal-actions">
          <span className="spacer" />
          <button type="button" className="btn" onClick={() => navigate(-1)}>Cancel</button>
          <button className="btn btn-primary" disabled={save.isPending || !balanced}>Post entry</button>
        </div>
      </form>
    </>
  )
}

export function LedgerPage() {
  const { code } = useParams()
  const [range, setRange] = useState<[string, string]>(() => preset('thisYear'))
  const ledger = useQuery({
    queryKey: ['accounting', 'ledger', code, range],
    queryFn: () => api.get<Ledger>(`/api/accounting/ledger/${code}${query({ from: range[0], to: range[1] })}`),
    enabled: !!range[0] && !!range[1],
    placeholderData: keepPreviousData,
  })
  const l = ledger.data
  return (
    <>
      <PageHeader title={l ? `Ledger ${l.code} · ${l.name}` : 'Ledger'} actions={<Link className="btn" to="/accounting?tab=accounts">Chart of accounts</Link>} />
      <div className="toolbar no-print"><span /><PeriodPicker range={range} onChange={setRange} /></div>
      <ErrorBox error={ledger.error} />
      {ledger.isLoading ? <Loading /> : l && (
        <div className="card">
          <table className="table">
            <thead><tr><th>Date</th><th>Entry</th><th>Description</th><th className="num">Debit</th><th className="num">Credit</th><th className="num">Balance</th></tr></thead>
            <tbody>
              <tr className="muted"><td>{date(l.from)}</td><td /><td>Opening balance</td><td /><td /><td className="num">{money(l.opening)}</td></tr>
              {l.rows.map((r, i) => (
                <tr key={`${r.entryId}-${i}`}>
                  <td>{date(r.date)}</td><td><Link to={`/accounting/journal/${r.entryId}`}>{r.entryNumber}</Link></td><td>{r.description}</td>
                  <td className="num">{amount(r.debit)}</td><td className="num">{amount(r.credit)}</td>
                  <td className={`num ${r.balance < 0 ? 'neg' : ''}`}>{money(r.balance)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr className="total-row"><td colSpan={5}>Closing balance</td><td className="num">{money(l.closing)}</td></tr></tfoot>
          </table>
          {l.rows.length === 0 && <Empty>No movements in this period.</Empty>}
        </div>
      )}
    </>
  )
}
