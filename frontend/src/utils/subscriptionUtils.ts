import type { SubscriptionResponse } from '../types/subscription';
import { formatDayMonthWithArticle } from './dateTime';
import { lookupLabel } from './lookupLabels';

export function subscriptionBadgeVariant(statusCode: string): 'lime' | 'amber' | 'zinc' {
  if (statusCode === 'ACTIVE') return 'lime';
  if (statusCode === 'EXPIRED') return 'amber';
  return 'zinc';
}

/** `type` is null for subscriptions created from a package — falls back to the package name. */
export function subscriptionLabel(sub: {
  type: { code: string; label: string } | null;
  package: { name: string } | null;
}): string {
  return (sub.type && lookupLabel(sub.type)) ?? sub.package?.name ?? '—';
}

/** Member status as shown across the app — always exactly one of these three. */
export type MemberStatus = 'active' | 'expired' | 'inactive';

/**
 * Picks the member's "current" subscription out of a full list: the one with the
 * latest `endDate` (works for both the active plan and the most recently expired one).
 */
export function pickCurrentSubscription(subs: SubscriptionResponse[]): SubscriptionResponse | null {
  if (subs.length === 0) return null;
  return subs.reduce((latest, s) => (s.endDate > latest.endDate ? s : latest));
}

/**
 * 3-state member status. `member.active === false` always wins (Inattivo); otherwise it
 * depends on the current subscription: ACTIVE → Attivo, EXPIRED/assente → Scaduto.
 */
export function memberStatus(
  member: { active: boolean },
  currentSub: SubscriptionResponse | null
): MemberStatus {
  if (!member.active) return 'inactive';
  if (currentSub?.status.code === 'ACTIVE') return 'active';
  return 'expired';
}

/** Whole days between today and `endDate` (negative = already past). Null if no date. */
export function daysRemaining(endDate?: string | null): number | null {
  if (!endDate) return null;
  const end = new Date(`${endDate}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((end.getTime() - today.getTime()) / 86_400_000);
}

/** Text color class for a days-remaining figure: amber once it's urgent (<=10) or past, white otherwise. */
export function daysRemainingColorClass(days: number | null): string {
  if (days === null) return 'text-zinc-500';
  return days <= 10 ? 'text-amber-400' : 'text-zinc-100';
}

/** Shared visual tokens for the 3-state member status pill/dot/avatar, used across Members + detail. */
export const memberStatusDotClass: Record<MemberStatus, string> = {
  active: 'bg-lime-400',
  expired: 'bg-amber-400',
  inactive: 'bg-zinc-500',
};

export const memberStatusPillClass: Record<MemberStatus, string> = {
  active: 'bg-lime-400/12 text-lime-400',
  expired: 'bg-amber-400/13 text-amber-400',
  inactive: 'bg-white/7 text-zinc-300',
};

export const memberStatusAvatarClass: Record<MemberStatus, string> = {
  active: 'bg-lime-400/12 text-lime-400',
  expired: 'bg-amber-400/12 text-amber-400',
  inactive: 'bg-white/7 text-zinc-300',
};

/** "Attivo" / "Inattivo" / "Scaduto il 20 giu" (falls back to plain "Scaduto" with no current sub). */
export function memberStatusLabel(status: MemberStatus, currentSub: SubscriptionResponse | null): string {
  if (status === 'active') return 'Attivo';
  if (status === 'inactive') return 'Inattivo';
  return currentSub ? `Scaduto ${formatDayMonthWithArticle(currentSub.endDate)}` : 'Scaduto';
}

/** Whether the "Rinnova abbonamento" quick action should surface: expired, or active but ending soon. */
export function needsRenewal(status: MemberStatus, days: number | null): boolean {
  if (status === 'expired') return true;
  return status === 'active' && days !== null && days <= 10;
}

/**
 * Adds `months` to a `YYYY-MM-DD` date string with the same end-of-month clamping semantics as
 * Java's `LocalDate.plusMonths` (e.g. 31 Jan + 1 month = 28/29 Feb, 31 Jan + 3 months = 30 Apr).
 * Used for the wizard's end-date preview — must match `SubscriptionService.java` exactly, no ±1 day.
 */
export function addMonthsClamped(startDate: string, months: number): string {
  const [y, m, d] = startDate.split('-').map(Number);
  const targetMonthIndex = m - 1 + months; // 0-indexed
  const daysInTargetMonth = new Date(y, targetMonthIndex + 1, 0).getDate();
  const day = Math.min(d, daysInTargetMonth);
  const result = new Date(y, targetMonthIndex, day);
  const yyyy = result.getFullYear();
  const mm = String(result.getMonth() + 1).padStart(2, '0');
  const dd = String(result.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}
