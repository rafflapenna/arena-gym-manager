import type { MemberRef } from './member';

export type ReferralRewardStatus = 'EARNED' | 'USED' | 'EXPIRED';

/** Uno sconto maturato per aver portato qualcuno che poi si e' iscritto. */
export interface ReferralRewardResponse {
  id: number;
  /** Il socio la cui iscrizione ha fatto maturare lo sconto. */
  referredMember: MemberRef;
  /** Percentuale di sconto, congelata al momento in cui e' maturata. */
  percent: number;
  status: ReferralRewardStatus;
  expiresAt: string | null;
  usedOnSubscriptionId: number | null;
  earnedAt: string;
  usedAt: string | null;
}
