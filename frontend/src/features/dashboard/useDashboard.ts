import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from './dashboardApi';

const KEYS = {
  stats: ['dashboard', 'stats'] as const,
};

export const useDashboardStats = () =>
  useQuery({
    queryKey: KEYS.stats,
    queryFn: () => dashboardApi.getStats(),
    staleTime: 60_000,
  });
