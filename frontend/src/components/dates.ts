/** yyyy-mm-dd in the browser's local time (toISOString would use UTC and can shift the day). */
export const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Common date ranges, computed in the browser's local time. */
export function preset(name: string): [string, string] {
  const today = new Date()
  const y = today.getFullYear(), m = today.getMonth()
  switch (name) {
    case 'last30': return [iso(new Date(y, m, today.getDate() - 29)), iso(today)]
    case 'lastMonth': return [iso(new Date(y, m - 1, 1)), iso(new Date(y, m, 0))]
    case 'thisYear': return [iso(new Date(y, 0, 1)), iso(today)]
    default: return [iso(new Date(y, m, 1)), iso(today)] // this month
  }
}
