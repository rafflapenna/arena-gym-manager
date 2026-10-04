import apiClient from '../../services/apiClient';
import type { CourseRequest, CourseResponse } from '../../types';

export const coursesApi = {
  getAll: () => apiClient.get<CourseResponse[]>('/courses').then((r) => r.data),

  getToday: () => apiClient.get<CourseResponse[]>('/courses/today').then((r) => r.data),

  getById: (id: number) => apiClient.get<CourseResponse>(`/courses/${id}`).then((r) => r.data),

  create: (data: CourseRequest) =>
    apiClient.post<CourseResponse>('/courses', data).then((r) => r.data),

  update: (id: number, data: CourseRequest) =>
    apiClient.put<CourseResponse>(`/courses/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/courses/${id}`).then(() => undefined),
};
