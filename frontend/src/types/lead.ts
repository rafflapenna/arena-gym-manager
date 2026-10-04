import type { MemberResponse } from './member';

export type LeadStatus = 'REQUESTED' | 'NEW' | 'TRIAL_SCHEDULED' | 'TRIED' | 'CONVERTED' | 'LOST';

/** Who created the lead: the reception (default) or the member themselves via /me/leads. */
export type LeadOrigin = 'STAFF' | 'MEMBER';

/** Minimal course reference as embedded in a lead (mirrors CourseDisciplineRef). */
export interface LeadCourseRef {
  id: number;
  name: string;
}

export interface LeadRequest {
  firstName: string;
  lastName: string;
  birthDate: string;
  phone: string;
  email?: string | null;
  courseId?: number | null;
  trialDate?: string | null;
  source?: string | null;
  notes?: string | null;
  status?: LeadStatus | null;
  /** Id del socio che l'ha portata; viaggia sul socio alla conversione. */
  referredByMemberId?: number | null;
}

export interface LeadResponse {
  id: number;
  firstName: string;
  lastName: string;
  /** Null for a REQUESTED lead inserted by a member, until the reception completes it. */
  birthDate: string | null;
  phone: string;
  email: string | null;
  source: string | null;
  interestCourse: LeadCourseRef | null;
  trialDate: string | null;
  status: LeadStatus;
  notes: string | null;
  createdAt: string;
  convertedMemberId: number | null;
  /** Il socio che ha portato questa persona in palestra, o null. */
  referredBy: LeadCourseRef | null;
  origin: LeadOrigin;
  /** Set when the entered phone matches an existing member of the gym (staff-only visibility). */
  matchedMemberId: number | null;
}

/** Body of POST /leads/{id}/convert — email always; birthDate only when the lead itself has none. */
export interface LeadConvertRequest {
  email: string;
  birthDate?: string;
}

/** The BE creates the Member from the lead's own data and links it back — both come home. */
export interface LeadConvertResponse {
  lead: LeadResponse;
  member: MemberResponse;
}

/** Body of POST /leads/{id}/confirm — reception sets the agreed trial date, REQUESTED -> TRIAL_SCHEDULED. */
export interface LeadConfirmRequest {
  trialDate: string;
}

/** Body of POST /me/leads — a member reporting a friend's free trial. */
export interface MyLeadRequest {
  firstName: string;
  lastName: string;
  phone: string;
  trialDate?: string | null;
  courseId?: number | null;
}

/** GET /me/leads row — deliberately thin, no contact/birth data of the referred friend. */
export interface MyLeadResponse {
  id: number;
  firstName: string;
  lastName: string;
  status: LeadStatus;
  trialDate: string | null;
  interestCourse: LeadCourseRef | null;
  rewardEarned: boolean;
  createdAt: string;
}
