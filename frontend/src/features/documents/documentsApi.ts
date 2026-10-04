import apiClient from '../../services/apiClient';
import { downloadBlob } from '../../utils/download';
import type { DocumentType, MedicalCertExpiring, MemberDocumentResponse } from '../../types';

export interface UploadDocumentParams {
  file: File;
  type: DocumentType;
  /** Required for OTHER, optional for MEDICAL_CERT. */
  title?: string | null;
  /** Only meaningful for MEDICAL_CERT. */
  expiryDate?: string | null;
}

export const documentsApi = {
  getByMember: (memberId: number) =>
    apiClient.get<MemberDocumentResponse[]>(`/members/${memberId}/documents`).then((r) => r.data),

  upload: (memberId: number, params: UploadDocumentParams) => {
    const formData = new FormData();
    formData.append('file', params.file);
    formData.append('type', params.type);
    if (params.title) formData.append('title', params.title);
    if (params.expiryDate) formData.append('expiryDate', params.expiryDate);
    // No explicit Content-Type: the browser sets the multipart boundary, the axios
    // interceptor adds the Bearer token.
    return apiClient
      .post<MemberDocumentResponse>(`/members/${memberId}/documents`, formData)
      .then((r) => r.data);
  },

  delete: (memberId: number, documentId: number) =>
    apiClient.delete(`/members/${memberId}/documents/${documentId}`),

  download: async (memberId: number, doc: MemberDocumentResponse) => {
    const res = await apiClient.get(`/members/${memberId}/documents/${doc.id}/download`, {
      responseType: 'blob',
    });
    downloadBlob(res.data, doc.originalFilename);
  },

  getExpiring: (days: number) =>
    apiClient
      .get<MedicalCertExpiring[]>('/documents/expiring', { params: { days } })
      .then((r) => r.data),
};
