import apiClient from '../../services/apiClient';
import type { DisciplineRequest, DisciplineResponse } from '../../types';

export const disciplinesApi = {
  getAll: () => apiClient.get<DisciplineResponse[]>('/disciplines').then((r) => r.data),

  create: (data: DisciplineRequest) =>
    apiClient.post<DisciplineResponse>('/disciplines', data).then((r) => r.data),

  update: (id: number, data: DisciplineRequest) =>
    apiClient.put<DisciplineResponse>(`/disciplines/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/disciplines/${id}`).then(() => undefined),
};
