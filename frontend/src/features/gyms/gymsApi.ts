import apiClient from '../../services/apiClient';
import type { GymRequest, GymResponse } from '../../types/gym';

export const gymsApi = {
  getAll: () =>
    apiClient.get<GymResponse[]>('/gyms').then((r) => r.data),

  getById: (id: number) =>
    apiClient.get<GymResponse>(`/gyms/${id}`).then((r) => r.data),

  create: (data: GymRequest) =>
    apiClient.post<GymResponse>('/gyms', data).then((r) => r.data),

  update: (id: number, data: GymRequest) =>
    apiClient.put<GymResponse>(`/gyms/${id}`, data).then((r) => r.data),

  delete: (id: number) =>
    apiClient.delete(`/gyms/${id}`),

  // Current user's own gym (INSTRUCTOR/STAFF read, INSTRUCTOR update).
  getMine: () =>
    apiClient.get<GymResponse>('/gyms/me').then((r) => r.data),

  updateMine: (data: GymRequest) =>
    apiClient.put<GymResponse>('/gyms/me', data).then((r) => r.data),
};
