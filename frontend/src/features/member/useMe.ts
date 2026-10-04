import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { meService } from '../../services/meService';
import type { MemberDocumentResponse, MyLeadRequest } from '../../types';

export const useMyProfile = () =>
  useQuery({
    queryKey: ['me'],
    queryFn: meService.getProfile,
  });

export const useMyFederationCards = () =>
  useQuery({
    queryKey: ['me', 'federation-cards'],
    queryFn: meService.getFederationCards,
  });

export const useMySubscriptions = () =>
  useQuery({
    queryKey: ['me', 'subscriptions'],
    queryFn: meService.getSubscriptions,
  });

export const useMyPayments = () =>
  useQuery({
    queryKey: ['me', 'payments'],
    queryFn: meService.getPayments,
  });

export const useMyCourses = () =>
  useQuery({
    queryKey: ['me', 'courses'],
    queryFn: meService.getCourses,
  });

export const useMyDocuments = () =>
  useQuery({
    queryKey: ['me', 'documents'],
    queryFn: meService.getDocuments,
  });

export const useDownloadMyDocument = () =>
  useMutation({
    mutationFn: (doc: MemberDocumentResponse) => meService.downloadDocument(doc),
  });

export const useMyLeads = () =>
  useQuery({
    queryKey: ['me', 'leads'],
    queryFn: meService.getLeads,
  });

export const useCreateMyLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: MyLeadRequest) => meService.createLead(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me', 'leads'] }),
  });
};
