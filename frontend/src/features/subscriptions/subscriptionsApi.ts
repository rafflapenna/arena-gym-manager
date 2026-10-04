import apiClient from '../../services/apiClient';
import type { SubscriptionRequest, SubscriptionResponse } from '../../types';

export const subscriptionsApi = {
  getAll: (status?: string) =>
    apiClient
      .get<SubscriptionResponse[]>('/subscriptions', {
        params: status ? { status } : undefined,
      })
      .then((r) => r.data),

  getExpiring: (days: number) =>
    apiClient
      .get<SubscriptionResponse[]>('/subscriptions/expiring', { params: { days } })
      .then((r) => r.data),

  getByMember: (memberId: number) =>
    apiClient
      .get<SubscriptionResponse[]>(`/members/${memberId}/subscriptions`)
      .then((r) => r.data),

  create: (memberId: number, data: SubscriptionRequest) =>
    apiClient
      .post<SubscriptionResponse>(`/members/${memberId}/subscriptions`, data)
      .then((r) => r.data),

  cancel: (id: number) =>
    apiClient.patch<SubscriptionResponse>(`/subscriptions/${id}/cancel`).then((r) => r.data),
};
