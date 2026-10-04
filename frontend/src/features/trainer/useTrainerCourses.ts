import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { trainerApi } from './trainerApi';

export const TRAINER_KEYS = {
  courses: ['trainer', 'courses'] as const,
  attendance: (courseId: number) => ['trainer', 'courses', courseId, 'attendance'] as const,
};

export const useTrainerCourses = () =>
  useQuery({
    queryKey: TRAINER_KEYS.courses,
    queryFn: trainerApi.getCourses,
  });

export const useCourseAttendance = (courseId: number) =>
  useQuery({
    queryKey: TRAINER_KEYS.attendance(courseId),
    queryFn: () => trainerApi.getAttendance(courseId),
    enabled: courseId > 0,
  });

export const usePostAttendance = (courseId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (memberId: number) => trainerApi.postAttendance(courseId, memberId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: TRAINER_KEYS.attendance(courseId) });
      // Attendance writes a check-in too — refresh the operator "today" view.
      qc.invalidateQueries({ queryKey: ['check-ins', 'today'] });
    },
  });
};
