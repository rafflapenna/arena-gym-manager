import type { BadgeVariant } from '../components/ui/Badge';

export type MedicalCertStatus = 'valid' | 'expiring' | 'expired' | 'missing';

/** Whole days between today and `expiryDate` (negative = already past). */
function daysUntil(expiryDate: string): number {
  const end = new Date(`${expiryDate}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((end.getTime() - today.getTime()) / 86_400_000);
}

/** 4-state medical-cert status: 'missing' when the member has no cert with an expiry date at all. */
export function medicalCertStatus(
  expiryDate: string | null | undefined,
  thresholdDays = 30
): MedicalCertStatus {
  if (!expiryDate) return 'missing';
  const days = daysUntil(expiryDate);
  if (days < 0) return 'expired';
  if (days <= thresholdDays) return 'expiring';
  return 'valid';
}

export const medicalCertBadgeVariant: Record<MedicalCertStatus, BadgeVariant> = {
  valid: 'lime',
  expiring: 'amber',
  expired: 'red',
  missing: 'zinc',
};

export const medicalCertStatusLabel: Record<MedicalCertStatus, string> = {
  valid: 'Valido',
  expiring: 'In scadenza',
  expired: 'Scaduto',
  missing: 'Mancante',
};
