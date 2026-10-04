import type { BadgeVariant } from '../components/ui/Badge';
import type { LeadStatus } from '../types/lead';

/** Italian label for a lead status, used in tabs/badges on the Prove page (reception view). */
export function leadStatusLabel(status: LeadStatus): string {
  switch (status) {
    case 'REQUESTED':
      return 'Da confermare';
    case 'NEW':
      return 'Nuova';
    case 'TRIAL_SCHEDULED':
      return 'Prova fissata';
    case 'TRIED':
      return 'Prova fatta';
    case 'CONVERTED':
      return 'Convertita';
    case 'LOST':
      return 'Persa';
  }
}

/**
 * Member-facing label for "I miei amici" — coarser than the reception one: REQUESTED and NEW
 * read the same to a member (both just mean "not yet actioned"), since the REQUESTED/NEW split
 * is an internal reception detail they have no reason to see.
 */
export function myLeadStatusLabel(status: LeadStatus): string {
  switch (status) {
    case 'REQUESTED':
    case 'NEW':
      return 'In attesa di conferma';
    case 'TRIAL_SCHEDULED':
      return 'Prova fissata';
    case 'TRIED':
      return 'Provato';
    case 'CONVERTED':
      return 'Iscritto';
    case 'LOST':
      return 'Non interessato';
  }
}

/** Badge color per status — `red` stays reserved for destructive actions/errors elsewhere in the app. */
export function leadStatusBadgeVariant(status: LeadStatus): BadgeVariant {
  switch (status) {
    case 'REQUESTED':
      return 'orange';
    case 'NEW':
      return 'cyan';
    case 'TRIAL_SCHEDULED':
      return 'violet';
    case 'TRIED':
      return 'amber';
    case 'CONVERTED':
      return 'lime';
    case 'LOST':
      return 'zinc';
  }
}
