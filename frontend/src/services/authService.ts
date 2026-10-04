import apiClient from './apiClient';
import type { LoginRequest, AuthResponse } from '../types/auth';

export const authService = {
  login: (data: LoginRequest): Promise<AuthResponse> =>
    apiClient.post('/auth/login', data).then((r) => r.data),

  forgotPassword: (email: string): Promise<void> =>
    apiClient.post('/auth/forgot-password', { email }).then(() => undefined),

  /** Open to every authenticated role (including MEMBER) — unlike the old MEMBER-only /me/password. */
  changePassword: (data: { currentPassword: string; newPassword: string }): Promise<void> =>
    apiClient.post('/users/me/password', data).then(() => undefined),
};
