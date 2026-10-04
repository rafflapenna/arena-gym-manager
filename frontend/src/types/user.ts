import type { Role } from './auth';

export interface UserResponse {
  id: number;
  username: string;
  firstName?: string | null;
  lastName?: string | null;
  role: Role;
  active: boolean;
  mustChangePassword: boolean;
  gymId?: number | null;
  gymName?: string | null;
  memberId?: number | null;
}

/** Payload for POST /users/instructors */
export interface CreateInstructorRequest {
  email: string;
  firstName?: string;
  lastName?: string;
  /** ID of the existing gym to assign the instructor to. */
  gymId: number;
}

/** Payload for POST /users/staff */
export interface CreateStaffRequest {
  email: string;
  firstName?: string;
  lastName?: string;
}

/** Payload for POST /users/trainers */
export interface CreateTrainerRequest {
  email: string;
  firstName?: string;
  lastName?: string;
}

export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
  /** Optional: if changed, becomes the new login username. */
  email?: string;
}

export interface UserInviteResponse {
  id: number;
  username: string;
  role: Role;
  gymId?: number | null;
  gymName?: string | null;
  inviteLink: string;
}
