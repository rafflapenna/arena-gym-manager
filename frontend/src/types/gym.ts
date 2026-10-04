export interface GymRequest {
  name: string;
  address?: string;
  city?: string;
  postalCode?: string;
  province?: string;
  region?: string;
  phone?: string;
  vatNumber?: string;
  contactEmail?: string;
  /** Quota associativa annuale. Omesso = lascia invariato. */
  membershipFeeAmount?: number;
}

export interface GymResponse {
  id: number;
  name: string;
  address?: string | null;
  city?: string | null;
  postalCode?: string | null;
  province?: string | null;
  region?: string | null;
  phone?: string | null;
  vatNumber?: string | null;
  contactEmail?: string | null;
  membershipFeeAmount: number;
  createdAt: string;
  instructorId?: number | null;
  instructorEmail?: string | null;
  staffCount: number;
  memberCount: number;
}
