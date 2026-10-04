import apiClient from '../../services/apiClient';
import type {
  FederationCardTypeRequest,
  FederationCardTypeResponse,
  FederationRequest,
  FederationResponse,
  MemberFederationCardResponse,
  MemberFeeRequest,
  MemberFeeResponse,
} from '../../types';

export const federationsApi = {
  getAll: () => apiClient.get<FederationResponse[]>('/federations').then((r) => r.data),

  create: (data: FederationRequest) =>
    apiClient.post<FederationResponse>('/federations', data).then((r) => r.data),

  update: (id: number, data: FederationRequest) =>
    apiClient.put<FederationResponse>(`/federations/${id}`, data).then((r) => r.data),

  delete: (id: number) => apiClient.delete(`/federations/${id}`).then(() => undefined),

  addCardType: (federationId: number, data: FederationCardTypeRequest) =>
    apiClient
      .post<FederationCardTypeResponse>(`/federations/${federationId}/card-types`, data)
      .then((r) => r.data),

  updateCardType: (cardTypeId: number, data: FederationCardTypeRequest) =>
    apiClient
      .put<FederationCardTypeResponse>(`/federations/card-types/${cardTypeId}`, data)
      .then((r) => r.data),

  deleteCardType: (cardTypeId: number) =>
    apiClient.delete(`/federations/card-types/${cardTypeId}`).then(() => undefined),
};

export const memberFeesApi = {
  getByMember: (memberId: number) =>
    apiClient.get<MemberFeeResponse[]>(`/members/${memberId}/fees`).then((r) => r.data),

  charge: (memberId: number, data: MemberFeeRequest) =>
    apiClient.post<MemberFeeResponse>(`/members/${memberId}/fees`, data).then((r) => r.data),

  delete: (feeId: number) => apiClient.delete(`/fees/${feeId}`).then(() => undefined),

  getCards: (memberId: number) =>
    apiClient
      .get<MemberFederationCardResponse[]>(`/members/${memberId}/federation-cards`)
      .then((r) => r.data),

  saveCard: (memberId: number, federationId: number, cardNumber: string) =>
    apiClient
      .put<MemberFederationCardResponse>(`/members/${memberId}/federation-cards/${federationId}`, {
        cardNumber,
      })
      .then((r) => r.data),

  deleteCard: (memberId: number, federationId: number) =>
    apiClient.delete(`/members/${memberId}/federation-cards/${federationId}`).then(() => undefined),

  expiring: (days = 30) =>
    apiClient.get<MemberFeeResponse[]>('/fees/expiring', { params: { days } }).then((r) => r.data),
};
