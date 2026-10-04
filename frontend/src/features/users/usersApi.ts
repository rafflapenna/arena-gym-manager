import apiClient from '../../services/apiClient';
import type {
  CreateInstructorRequest,
  CreateStaffRequest,
  CreateTrainerRequest,
  UpdateUserRequest,
  UserInviteResponse,
  UserResponse,
} from '../../types/user';

export const usersApi = {
  // ── Instructors (ADMIN) ──
  getInstructors: () =>
    apiClient.get<UserResponse[]>('/users/instructors').then((r) => r.data),

  createInstructor: (data: CreateInstructorRequest) =>
    apiClient.post<UserInviteResponse>('/users/instructors', data).then((r) => r.data),

  // ── Staff (GYM_MANAGER) ──
  getStaff: () =>
    apiClient.get<UserResponse[]>('/users/staff').then((r) => r.data),

  createStaff: (data: CreateStaffRequest) =>
    apiClient.post<UserInviteResponse>('/users/staff', data).then((r) => r.data),

  // ── Trainers (GYM_MANAGER) ──
  getTrainers: () =>
    apiClient.get<UserResponse[]>('/users/trainers').then((r) => r.data),

  createTrainer: (data: CreateTrainerRequest) =>
    apiClient.post<UserInviteResponse>('/users/trainers', data).then((r) => r.data),

  // ── Assignable course teachers: trainers + the gym manager (GYM_MANAGER/STAFF) ──
  getTeachers: () =>
    apiClient.get<UserResponse[]>('/users/teachers').then((r) => r.data),

  // ── Update / reset password (instructor & staff) ──
  update: (id: number, data: UpdateUserRequest) =>
    apiClient.put<UserResponse>(`/users/${id}`, data).then((r) => r.data),

  resetPassword: (id: number) =>
    apiClient.post<UserInviteResponse>(`/users/${id}/reset-password`).then((r) => r.data),

  // ── Enable / disable ──
  activate: (id: number) =>
    apiClient.patch<UserResponse>(`/users/${id}/activate`).then((r) => r.data),

  deactivate: (id: number) =>
    apiClient.patch<UserResponse>(`/users/${id}/deactivate`).then((r) => r.data),
};
