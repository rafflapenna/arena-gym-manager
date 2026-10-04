import apiClient from '../../services/apiClient';
import type { PackageRequest, PackageResponse } from '../../types';

export const packagesApi = {
  getAll: () => apiClient.get<PackageResponse[]>('/packages').then((r) => r.data),

  getById: (id: number) => apiClient.get<PackageResponse>(`/packages/${id}`).then((r) => r.data),

  create: (data: PackageRequest) =>
    apiClient.post<PackageResponse>('/packages', data).then((r) => r.data),

  update: (id: number, data: PackageRequest) =>
    apiClient.put<PackageResponse>(`/packages/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/packages/${id}`).then(() => undefined),
};
