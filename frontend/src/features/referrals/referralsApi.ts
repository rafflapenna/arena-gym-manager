import apiClient from '../../services/apiClient';
import type { ReferralRewardResponse } from '../../types';

export const referralsApi = {
  /** Crediti "porta un amico" di un socio; `status=EARNED` filtra quelli ancora spendibili. */
  getByMember: (memberId: number, status?: string) =>
    apiClient
      .get<ReferralRewardResponse[]>(`/members/${memberId}/rewards`, {
        params: { status: status || undefined },
      })
      .then((r) => r.data),
};
