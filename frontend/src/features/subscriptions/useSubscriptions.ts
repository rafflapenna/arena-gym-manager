import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { subscriptionsApi } from './subscriptionsApi';
import { REFERRAL_KEYS } from '../referrals';
import type { SubscriptionRequest } from '../../types';

const KEYS = {
  all: ['subscriptions'] as const,
  list: (status?: string) => ['subscriptions', 'list', status ?? 'all'] as const,
  byMember: (memberId: number) => ['subscriptions', 'member', memberId] as const,
  expiring: (days: number) => ['subscriptions', 'expiring', days] as const,
};

export const useSubscriptions = (status?: string) =>
  useQuery({
    queryKey: KEYS.list(status),
    queryFn: () => subscriptionsApi.getAll(status),
  });

export const useExpiringSubscriptions = (days: number, enabled = true) =>
  useQuery({
    queryKey: KEYS.expiring(days),
    queryFn: () => subscriptionsApi.getExpiring(days),
    enabled,
  });

export const useSubscriptionsByMember = (memberId: number) =>
  useQuery({
    queryKey: KEYS.byMember(memberId),
    queryFn: () => subscriptionsApi.getByMember(memberId),
    enabled: memberId > 0,
  });

export const useCreateSubscription = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: SubscriptionRequest) => subscriptionsApi.create(memberId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEYS.all });
      qc.invalidateQueries({ queryKey: KEYS.byMember(memberId) });
      // Creare un abbonamento muove anche i crediti passaparola: consuma quello eventualmente
      // speso qui e ne fa maturare uno per chi ha portato questo socio. Si invalida la radice
      // perche' il credito che nasce e' di un ALTRO socio (il referrer), non di questo.
      qc.invalidateQueries({ queryKey: REFERRAL_KEYS.all });
    },
  });
};

export const useCancelSubscription = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => subscriptionsApi.cancel(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all }),
  });
};
