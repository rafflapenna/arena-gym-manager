import apiClient from '../../services/apiClient';
import type { CheckInResponse, QrTokenResponse } from '../../types';

export const qrApi = {
  /** MEMBER: own identity token (backfilled lazily on the backend). */
  getMyQr: () => apiClient.get<QrTokenResponse>('/me/qr').then((r) => r.data),

  /** STAFF/GYM_MANAGER: a member's token (for printing/display). */
  getMemberQr: (memberId: number) =>
    apiClient.get<QrTokenResponse>(`/members/${memberId}/qr`).then((r) => r.data),

  /** Invalidates the previous token (lost/compromised card) and issues a new one. */
  rotateMemberQr: (memberId: number) =>
    apiClient.post<QrTokenResponse>(`/members/${memberId}/qr/rotate`).then((r) => r.data),

  /** Reception check-in by scanned token. 201 new / 200 already checked in / 404 unknown token. */
  checkInByQr: (token: string) =>
    apiClient.post<CheckInResponse>('/check-ins/qr', { token }).then((r) => r.data),
};
