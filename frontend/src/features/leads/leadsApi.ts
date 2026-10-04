import apiClient from '../../services/apiClient';
import type { LeadOrigin, LeadRequest, LeadResponse, LeadConvertResponse } from '../../types';

export type LeadsParams = { status?: string; q?: string; origin?: LeadOrigin };

export const leadsApi = {
  getAll: (params: LeadsParams = {}) =>
    apiClient
      .get<LeadResponse[]>('/leads', {
        params: {
          status: params.status || undefined,
          q: params.q || undefined,
          origin: params.origin || undefined,
        },
      })
      .then((r) => r.data),

  getById: (id: number) => apiClient.get<LeadResponse>(`/leads/${id}`).then((r) => r.data),

  create: (data: LeadRequest) => apiClient.post<LeadResponse>('/leads', data).then((r) => r.data),

  update: (id: number, data: LeadRequest) =>
    apiClient.put<LeadResponse>(`/leads/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/leads/${id}`).then(() => undefined),

  /** Body is `{ email, birthDate? }` — birthDate only when the lead itself has none (design §4.1). */
  convert: (id: number, email: string, birthDate?: string) =>
    apiClient
      .post<LeadConvertResponse>(`/leads/${id}/convert`, { email, birthDate })
      .then((r) => r.data),

  /** REQUESTED -> TRIAL_SCHEDULED; the reception agrees on a trial date for a member-submitted lead. */
  confirm: (id: number, trialDate: string) =>
    apiClient.post<LeadResponse>(`/leads/${id}/confirm`, { trialDate }).then((r) => r.data),

  /** REQUESTED -> LOST; the reception turns down a member-submitted lead. */
  decline: (id: number) => apiClient.post<LeadResponse>(`/leads/${id}/decline`).then((r) => r.data),
};
