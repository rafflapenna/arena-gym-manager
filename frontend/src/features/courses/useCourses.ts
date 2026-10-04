import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { coursesApi } from './coursesApi';
import { PACKAGE_KEYS } from '../packages/usePackages';
import type { CourseRequest } from '../../types';

export const COURSE_KEYS = {
  all: ['courses'] as const,
  today: ['courses', 'today'] as const,
  detail: (id: number) => ['courses', id] as const,
};

export const useCourses = () =>
  useQuery({
    queryKey: COURSE_KEYS.all,
    queryFn: coursesApi.getAll,
  });

export const useTodayCourses = () =>
  useQuery({
    queryKey: COURSE_KEYS.today,
    queryFn: coursesApi.getToday,
  });

export const useCourse = (id: number) =>
  useQuery({
    queryKey: COURSE_KEYS.detail(id),
    queryFn: () => coursesApi.getById(id),
    enabled: id > 0,
  });

export const useCreateCourse = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CourseRequest) => coursesApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: COURSE_KEYS.all });
      // Course names/availability feed the package multi-select.
      qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all });
    },
  });
};

export const useUpdateCourse = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: CourseRequest }) => coursesApi.update(id, data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: COURSE_KEYS.all });
      qc.invalidateQueries({ queryKey: COURSE_KEYS.detail(variables.id) });
      qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all });
    },
  });
};

export const useDeleteCourse = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => coursesApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: COURSE_KEYS.all });
      qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all });
    },
  });
};
