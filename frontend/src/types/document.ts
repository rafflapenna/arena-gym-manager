export type DocumentType = 'MEDICAL_CERT' | 'OTHER';

export interface MemberDocumentResponse {
  id: number;
  memberId: number;
  type: DocumentType;
  title: string | null;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  /** Set only for MEDICAL_CERT documents. */
  expiryDate: string | null;
  uploadedAt: string;
}

/** Row from `/documents/expiring` — note `documentId`, not `id`. */
export interface MedicalCertExpiring {
  documentId: number;
  memberId: number;
  memberFullName: string;
  expiryDate: string;
  daysUntilExpiry: number;
}
