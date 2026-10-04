import apiClient from './apiClient';
import { downloadBlob } from '../utils/download';
import type {
  CheckInResponse,
  CourseResponse,
  MemberDocumentResponse,
  MemberFederationCardResponse,
  MemberResponse,
  MyLeadRequest,
  MyLeadResponse,
  PaymentResponse,
  SubscriptionResponse,
} from '../types';

export const meService = {
  getProfile: (): Promise<MemberResponse> => apiClient.get('/me').then(r => r.data),
  getFederationCards: (): Promise<MemberFederationCardResponse[]> =>
    apiClient.get('/me/federation-cards').then(r => r.data),
  getSubscriptions: (): Promise<SubscriptionResponse[]> =>
    apiClient.get('/me/subscriptions').then(r => r.data),
  getPayments: (): Promise<PaymentResponse[]> => apiClient.get('/me/payments').then(r => r.data),
  /** Active course catalog of the member's own gym, read-only. */
  getCourses: (): Promise<CourseResponse[]> => apiClient.get('/me/courses').then(r => r.data),
  createCheckIn: (): Promise<CheckInResponse> =>
    apiClient.post('/me/check-ins').then(r => r.data),
  getCheckIns: (): Promise<CheckInResponse[]> =>
    apiClient.get('/me/check-ins').then(r => r.data),
  getDocuments: (): Promise<MemberDocumentResponse[]> =>
    apiClient.get('/me/documents').then(r => r.data),
  /** "I miei amici" — prove gratuite segnalate dal socio loggato (porta un amico). */
  getLeads: (): Promise<MyLeadResponse[]> => apiClient.get('/me/leads').then(r => r.data),
  createLead: (data: MyLeadRequest): Promise<MyLeadResponse> =>
    apiClient.post('/me/leads', data).then(r => r.data),
  downloadDocument: async (doc: MemberDocumentResponse): Promise<void> => {
    const res = await apiClient.get(`/me/documents/${doc.id}/download`, { responseType: 'blob' });
    downloadBlob(res.data, doc.originalFilename);
  },
};
