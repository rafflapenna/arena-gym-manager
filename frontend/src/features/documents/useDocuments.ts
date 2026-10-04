import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { documentsApi, type UploadDocumentParams } from './documentsApi';
import type { MemberDocumentResponse } from '../../types';

const KEYS = {
  byMember: (memberId: number) => ['documents', 'member', memberId] as const,
  expiring: ['documents', 'expiring'] as const,
  expiringDays: (days: number) => ['documents', 'expiring', days] as const,
};

export const useMemberDocuments = (memberId: number) =>
  useQuery({
    queryKey: KEYS.byMember(memberId),
    queryFn: () => documentsApi.getByMember(memberId),
    enabled: memberId > 0,
  });

export const useExpiringDocuments = (days: number) =>
  useQuery({
    queryKey: KEYS.expiringDays(days),
    queryFn: () => documentsApi.getExpiring(days),
  });

export const useUploadDocument = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (params: UploadDocumentParams) => documentsApi.upload(memberId, params),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.byMember(memberId) });
      qc.invalidateQueries({ queryKey: KEYS.expiring });
    },
  });
};

export const useDeleteDocument = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (documentId: number) => documentsApi.delete(memberId, documentId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.byMember(memberId) });
      qc.invalidateQueries({ queryKey: KEYS.expiring });
    },
  });
};

export const useDownloadDocument = (memberId: number) =>
  useMutation({
    mutationFn: (doc: MemberDocumentResponse) => documentsApi.download(memberId, doc),
  });
