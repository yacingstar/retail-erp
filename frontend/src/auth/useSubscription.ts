import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { Module, Subscription } from '../api/types'

/** The tenant's plan, modules and limits (cached; refetched when invalidated with key ['subscription']). */
export function useSubscription() {
  const query = useQuery({ queryKey: ['subscription'], queryFn: () => api.get<Subscription>('/api/subscription'), staleTime: 60_000 })
  const has = (module: Module) => query.data?.modules.includes(module) ?? false
  return { ...query, has }
}

export function daysLeft(iso: string | null): number {
  if (!iso) return 0
  return Math.max(0, Math.ceil((new Date(iso).getTime() - Date.now()) / 86_400_000))
}
