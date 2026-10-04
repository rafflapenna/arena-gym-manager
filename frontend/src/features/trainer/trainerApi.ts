import apiClient from '../../services/apiClient';
import type { CourseAttendanceEntry, CourseResponse } from '../../types';

/**
 * - GET /trainer/courses — the trainer's own courses (read-only), distinct from
 *   the STAFF/GYM_MANAGER-only /courses catalog.
 * - GET/POST /trainer/courses/:id/attendance — attendance entries for that course
 *   (scoped server-side to check-ins tagged with that course); POST body is `{ memberId }`.
 */
export const trainerApi = {
  getCourses: () => apiClient.get<CourseResponse[]>('/trainer/courses').then((r) => r.data),

  getAttendance: (courseId: number) =>
    apiClient
      .get<CourseAttendanceEntry[]>(`/trainer/courses/${courseId}/attendance`)
      .then((r) => r.data),

  postAttendance: (courseId: number, memberId: number) =>
    apiClient
      .post<CourseAttendanceEntry>(`/trainer/courses/${courseId}/attendance`, { memberId })
      .then((r) => r.data),
};
