// A tiny fetch wrapper: adds the JWT, parses JSON, and turns RFC 9457 problem details into ApiError.
// Two independent clients: `api` for shop users, `platformApi` for platform admins (separate tokens).

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>
  /** Machine-readable reason from the backend, e.g. MODULE_NOT_IN_PLAN or PLAN_LIMIT_REACHED. */
  code?: string

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, code?: string) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.code = code
  }
}

function createClient(tokenKey: string) {
  let onUnauthorized: () => void = () => {}

  const getToken = () => localStorage.getItem(tokenKey)
  const setToken = (token: string | null) => (token ? localStorage.setItem(tokenKey, token) : localStorage.removeItem(tokenKey))

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = {}
    const token = getToken()
    if (token) headers['Authorization'] = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    const response = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined })

    if (response.status === 204) return undefined as T
    const text = await response.text()
    const data = text ? JSON.parse(text) : undefined

    if (!response.ok) {
      if (response.status === 401 && token) onUnauthorized()
      const message = data?.detail || data?.title || `Request failed (${response.status})`
      throw new ApiError(response.status, message, data?.errors ?? {}, data?.code)
    }
    return data as T
  }

  return {
    getToken,
    setToken,
    /** Called on any 401 so the app can drop the session and show the login page. */
    setUnauthorizedHandler: (handler: () => void) => { onUnauthorized = handler },
    get: <T>(path: string) => request<T>('GET', path),
    post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
    put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
    delete: (path: string) => request<void>('DELETE', path),
  }
}

export const api = createClient('retail-erp.token')
export const platformApi = createClient('retail-erp.platform-token')

/** Builds "?a=1&b=2", skipping empty values. */
export function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}
