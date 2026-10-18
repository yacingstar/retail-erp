export const money = (value: number | null | undefined) =>
  (value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export const date = (value: string | null | undefined) => (value ? new Date(value).toLocaleDateString() : '—')

export const dateTime = (value: string | null | undefined) => (value ? new Date(value).toLocaleString() : '—')

export const label = (value: string) => value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
