import { useEffect, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { label } from './format'

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <h1>{title}</h1>
      <div className="actions">{actions}</div>
    </div>
  )
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  const planIssue = error instanceof ApiError && (error.code === 'MODULE_NOT_IN_PLAN' || error.code === 'PLAN_LIMIT_REACHED')
  return <div className="error-box">{message}{planIssue && <> <a href="/plan">See plans</a></>}</div>
}

export function fieldError(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.fieldErrors[field] : undefined
}

export function Field({ label: text, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{text}</span>
      {children}
      {error && <small className="field-error">{error}</small>}
    </label>
  )
}

const BADGE_TONES: Record<string, string> = {
  DRAFT: 'grey', OPEN: 'blue', ORDERED: 'blue', RECEIVED: 'green', CANCELLED: 'red', CONFIRMED: 'green',
  ISSUED: 'blue', PARTIALLY_PAID: 'amber', PAID: 'green', CREDITED: 'purple', VOID: 'red',
  ADMIN: 'purple', MANAGER: 'blue', CASHIER: 'grey', true: 'green', false: 'red',
}

export function Badge({ value }: { value: string | boolean }) {
  const key = String(value)
  const text = typeof value === 'boolean' ? (value ? 'Active' : 'Inactive') : label(value)
  return <span className={`badge badge-${BADGE_TONES[key] ?? 'grey'}`}>{text}</span>
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="btn-icon" onClick={onClose} aria-label="Close">×</button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="pagination">
      <button className="btn" disabled={page === 0} onClick={() => onChange(page - 1)}>Previous</button>
      <span>Page {page + 1} of {totalPages}</span>
      <button className="btn" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  )
}

export function Loading() {
  return <div className="muted">Loading…</div>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

/** Returns `value` only after it stopped changing for `delay` ms (so we don't query on every keystroke). */
export function useDebounced<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}

export function SearchBox({ value, onChange, placeholder = 'Search…' }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return <input className="search" type="search" value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
}
