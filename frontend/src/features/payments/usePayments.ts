import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paymentsApi } from './paymentsApi';
import type { PaymentRequest } from '../../types';

const KEYS = {
  all: ['payments'] as const,
  byMember: (memberId: number) => ['payments', 'member', memberId] as const,
  bySubscription: (subId: number) => ['payments', 'subscription', subId] as const,
};

export const usePaymentsByMember = (memberId: number) =>
  useQuery({
    queryKey: KEYS.byMember(memberId),
    queryFn: () => paymentsApi.getByMember(memberId),
    enabled: memberId > 0,
  });

export const useCreatePayment = (subscriptionId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: PaymentRequest) => paymentsApi.create(subscriptionId, data),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: KEYS.all });
      qc.invalidateQueries({ queryKey: KEYS.byMember(data.memberId) });
      qc.invalidateQueries({ queryKey: KEYS.bySubscription(subscriptionId) });
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
};
