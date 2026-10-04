/**
 * Italian translations for the lookup codes `DataInitializer`/`SubscriptionTypeSeeder` seed in
 * English on the BE (SubscriptionStatus, PaymentMethod, SubscriptionType default "Listino"
 * rows). Those tables are DB-persisted and gym-editable, so this only covers the known seeded
 * codes — a gym's own custom payment method or subscription type falls back to its own `label`
 * as-is. BeltGrade is already seeded in Italian (see DataInitializer) and isn't covered here.
 */
const TRANSLATIONS: Record<string, string> = {
  // SubscriptionStatus
  ACTIVE: 'Attivo',
  EXPIRED: 'Scaduto',
  CANCELLED: 'Annullato',
  // PaymentMethod (only CASH is seeded by default; anything else is gym-created)
  CASH: 'Contanti',
  // SubscriptionType — per-gym "Listino" defaults seeded by SubscriptionTypeSeeder
  MONTHLY: 'Mensile',
  QUARTERLY: 'Trimestrale',
  ANNUAL: 'Annuale',
};

/**
 * Italian label for a lookup row, falling back to its own `label` when the code isn't one of
 * the known seeded ones.
 */
export function lookupLabel(lookup: { code: string; label: string }): string {
  return TRANSLATIONS[lookup.code] ?? lookup.label;
}
