export interface LoginRequest {
  username: string;
  password: string;
}

export type Role = 'ADMIN' | 'STAFF' | 'GYM_MANAGER' | 'MEMBER' | 'TRAINER';

/**
 * Role string as it may still arrive on the wire (JWT claim, login response) while
 * old tokens / a not-yet-migrated backend still emit the legacy `INSTRUCTOR` value.
 * Normalize with `normalizeRole` before storing it anywhere in app state.
 */
export type WireRole = Role | 'INSTRUCTOR';

export interface AuthResponse {
  token: string;
  username: string;
  name?: string;
  role: WireRole;
  memberId?: number;
  gymId?: number;
  mustChangePassword?: boolean;
}

export interface AuthUser {
  username: string;
  name?: string;
  role: Role;
  memberId?: number;
  gymId?: number;
  mustChangePassword?: boolean;
}

/** Maps the legacy `INSTRUCTOR` wire value to `GYM_MANAGER`; returns null for unknown roles. */
export function normalizeRole(raw: string): Role | null {
  if (raw === 'INSTRUCTOR') return 'GYM_MANAGER';
  if (raw === 'ADMIN' || raw === 'STAFF' || raw === 'GYM_MANAGER' || raw === 'MEMBER' || raw === 'TRAINER') {
    return raw;
  }
  return null;
}
