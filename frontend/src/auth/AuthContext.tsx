import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type { AuthResponse, Me, Role } from '../api/types'

interface AuthState {
  user: Me | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (data: { companyName: string; fullName: string; email: string; password: string }) => Promise<void>
  logout: () => void
  hasRole: (...roles: Role[]) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

/** Keeps the logged-in user in React state; the JWT itself lives in localStorage (see api/client.ts). */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(() => api.getToken() !== null)
  const queryClient = useQueryClient()

  const logout = useCallback(() => {
    api.setToken(null)
    setUser(null)
    queryClient.clear() // never show one tenant's cached data to the next user
  }, [queryClient])

  useEffect(() => {
    api.setUnauthorizedHandler(logout)
    if (api.getToken()) {
      api.get<Me>('/api/auth/me')
        .then(setUser)
        .catch(() => logout())
        .finally(() => setLoading(false))
    }
  }, [logout])

  const accept = useCallback((response: AuthResponse) => {
    queryClient.clear()
    api.setToken(response.token)
    setUser(response.user)
  }, [queryClient])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    login: async (email, password) => accept(await api.post<AuthResponse>('/api/auth/login', { email, password })),
    register: async (data) => accept(await api.post<AuthResponse>('/api/auth/register', data)),
    logout,
    hasRole: (...roles) => user !== null && roles.includes(user.role),
  }), [user, loading, accept, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
