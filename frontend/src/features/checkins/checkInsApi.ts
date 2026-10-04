import apiClient from '../../services/apiClient';
import type { CheckInResponse } from '../../types';

export const checkInsApi = {
  /** Operator check-in (RECEPTION). 201 if new, 200 if already checked in today. */
  create: (memberId: number) =>
    apiClient.post<CheckInResponse>(`/members/${memberId}/check-ins`).then((r) => r.data),

  getByMember: (memberId: number) =>
    apiClient.get<CheckInResponse[]>(`/members/${memberId}/check-ins`).then((r) => r.data),

  getToday: (date?: string) =>
    apiClient
      .get<CheckInResponse[]>('/check-ins', { params: date ? { date } : undefined })
      .then((r) => r.data),
};
