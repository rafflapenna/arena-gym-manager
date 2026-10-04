import type { MemberRef } from './member';

export interface FederationCardTypeResponse {
  id: number;
  name: string;
  price: number;
  active: boolean;
}

export interface FederationResponse {
  id: number;
  name: string;
  /** Mese di inizio stagione: 9 = 1 settembre, 1 = anno solare. */
  seasonStartMonth: number;
  active: boolean;
  currentSeasonStart: string;
  currentSeasonEnd: string;
  cardTypes: FederationCardTypeResponse[];
}

export interface FederationRequest {
  name: string;
  seasonStartMonth: number;
  active?: boolean;
}

export interface FederationCardTypeRequest {
  name: string;
  price: number;
  active?: boolean;
}

export type MemberFeeType = 'ASSOCIATION' | 'FEDERATION';

export interface MemberFeeResponse {
  id: number;
  type: MemberFeeType;
  federation: MemberRef | null;
  cardType: MemberRef | null;
  amount: number;
  paymentMethod: MemberRef | null;
  paidOn: string;
  validUntil: string;
  expired: boolean;
  notes: string | null;
  /** Valorizzato solo dalla lista delle quote in scadenza. */
  member: MemberRef | null;
}

/** Numero di tessera del socio con una federazione: fisso, non cambia ai rinnovi. */
export interface MemberFederationCardResponse {
  federation: MemberRef;
  cardNumber: string;
}

export interface MemberFeeRequest {
  type: MemberFeeType;
  cardTypeId?: number | null;
  amount?: number | null;
  paymentMethodId?: number | null;
  paidOn?: string | null;
  notes?: string | null;
}
