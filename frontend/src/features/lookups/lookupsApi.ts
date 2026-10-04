import apiClient from '../../services/apiClient';
import type { Lookup, BeltGradeRequest, PaymentMethodRequest, SubscriptionTypeRequest } from '../../types';

export const lookupsApi = {
  getSubscriptionTypes: () =>
    apiClient.get<Lookup[]>('/subscription-types').then((r) => r.data),

  getSubscriptionStatuses: () =>
    apiClient.get<Lookup[]>('/subscription-statuses').then((r) => r.data),

  getPaymentMethods: () =>
    apiClient.get<Lookup[]>('/payment-methods').then((r) => r.data),

  getBeltGrades: () =>
    apiClient.get<Lookup[]>('/belt-grades').then((r) => r.data),

  createSubscriptionType: (data: SubscriptionTypeRequest) =>
    apiClient.post<Lookup>('/subscription-types', data).then((r) => r.data),

  updateSubscriptionType: (id: number, data: SubscriptionTypeRequest) =>
    apiClient.put<Lookup>(`/subscription-types/${id}`, data).then((r) => r.data),

  createPaymentMethod: (data: PaymentMethodRequest) =>
    apiClient.post<Lookup>('/payment-methods', data).then((r) => r.data),

  createBeltGrade: (data: BeltGradeRequest) =>
    apiClient.post<Lookup>('/belt-grades', data).then((r) => r.data),
};
