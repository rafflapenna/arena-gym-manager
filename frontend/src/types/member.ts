import type { Lookup } from './common';

export interface PersonalInfoRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string | null;
  birthDate?: string | null;
  birthPlace?: string | null;
  fiscalCode?: string | null;
  gender?: string | null;
  address?: string | null;
  city?: string | null;
  postalCode?: string | null;
  province?: string | null;
  region?: string | null;
  country?: string | null;
  beltGradeId?: number | null;
}

export interface PersonalInfoResponse {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  birthDate: string | null;
  birthPlace: string | null;
  fiscalCode: string | null;
  gender: string | null;
  address: string | null;
  city: string | null;
  postalCode: string | null;
  province: string | null;
  region: string | null;
  country: string | null;
  beltGrade: Lookup | null;
}

export interface MemberRequest {
  personalInfo: PersonalInfoRequest;
  registrationDate?: string | null;
  notes?: string | null;
  /** Id del socio che l'ha portato. Sblocca lo sconto al primo abbonamento. */
  referredByMemberId?: number | null;
}

/** Minimal {id, name} reference to another member. */
export interface MemberRef {
  id: number;
  name: string;
}

export interface MemberResponse {
  id: number;
  personalInfo: PersonalInfoResponse;
  registrationDate: string;
  active: boolean;
  notes: string | null;
  /** Il socio che ha portato questo ("porta un amico"), o null. */
  referredBy: MemberRef | null;
  createdAt: string;
  updatedAt: string;
}
