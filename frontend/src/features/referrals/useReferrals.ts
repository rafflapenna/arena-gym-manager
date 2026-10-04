import { useQuery } from '@tanstack/react-query';
import { referralsApi } from './referralsApi';

export const REFERRAL_KEYS = {
  all: ['referrals'] as const,
  byMember: (memberId: number, status?: string) => ['referrals', memberId, status ?? 'all'] as const,
};

/**
 * Crediti passaparola di un socio. Non ha una mutation propria: maturano e si consumano lato
 * server creando un abbonamento, quindi e' `useCreateSubscription` a invalidarli.
 */
export const useMemberRewards = (memberId: number, status?: string) =>
  useQuery({
    queryKey: REFERRAL_KEYS.byMember(memberId, status),
    queryFn: () => referralsApi.getByMember(memberId, status),
    enabled: memberId > 0,
  });
