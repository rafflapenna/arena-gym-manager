import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { lookupsApi } from './lookupsApi';
import type { SubscriptionTypeRequest } from '../../types';

export const useBeltGrades = () =>
  useQuery({ queryKey: ['belt-grades'], queryFn: lookupsApi.getBeltGrades, staleTime: Infinity });

export const useSubscriptionTypes = () =>
  useQuery({ queryKey: ['subscription-types'], queryFn: lookupsApi.getSubscriptionTypes, staleTime: Infinity });

export const usePaymentMethods = () =>
  useQuery({ queryKey: ['payment-methods'], queryFn: lookupsApi.getPaymentMethods, staleTime: Infinity });

export const useCreateSubscriptionType = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: SubscriptionTypeRequest) => lookupsApi.createSubscriptionType(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['subscription-types'] }),
  });
};

export const useUpdateSubscriptionType = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: SubscriptionTypeRequest }) =>
      lookupsApi.updateSubscriptionType(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['subscription-types'] }),
  });
};
