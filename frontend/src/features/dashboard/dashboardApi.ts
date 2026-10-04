import apiClient from '../../services/apiClient';
import type { DashboardStats } from '../../types';

export const dashboardApi = {
  getStats: () =>
    apiClient.get<DashboardStats>('/dashboard/stats').then((r) => r.data),
};
