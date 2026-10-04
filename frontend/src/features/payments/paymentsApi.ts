import apiClient from '../../services/apiClient';
import type { PaymentRequest, PaymentResponse } from '../../types';

export const paymentsApi = {
  getByMember: (memberId: number) =>
    apiClient
      .get<PaymentResponse[]>(`/members/${memberId}/payments`)
      .then((r) => r.data),

  getBySubscription: (subscriptionId: number) =>
    apiClient
      .get<PaymentResponse[]>(`/subscriptions/${subscriptionId}/payments`)
      .then((r) => r.data),

  create: (subscriptionId: number, data: PaymentRequest) =>
    apiClient
      .post<PaymentResponse>(`/subscriptions/${subscriptionId}/payments`, data)
      .then((r) => r.data),
};
