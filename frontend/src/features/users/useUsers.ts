import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { usersApi } from './usersApi';
import type {
  CreateInstructorRequest,
  CreateStaffRequest,
  CreateTrainerRequest,
  UpdateUserRequest,
} from '../../types/user';

const KEYS = {
  instructors: ['users', 'instructors'] as const,
  staff: ['users', 'staff'] as const,
  trainers: ['users', 'trainers'] as const,
  teachers: ['users', 'teachers'] as const,
};

export const useInstructors = (enabled = true) =>
  useQuery({
    queryKey: KEYS.instructors,
    queryFn: usersApi.getInstructors,
    enabled,
  });

export const useStaff = (enabled = true) =>
  useQuery({
    queryKey: KEYS.staff,
    queryFn: usersApi.getStaff,
    enabled,
  });

/** Trainers of the current gym — backs the "Insegnanti" tab of the user management page. */
export const useTrainers = (enabled = true) =>
  useQuery({
    queryKey: KEYS.trainers,
    queryFn: usersApi.getTrainers,
    enabled,
  });

/**
 * Users assignable as a course's teacher: the trainers PLUS the gym manager, who in a small
 * gym usually teaches too. Distinct from useTrainers on purpose — that list must not show a
 * gym manager among the trainers to manage.
 */
export const useTeachers = (enabled = true) =>
  useQuery({
    queryKey: KEYS.teachers,
    queryFn: usersApi.getTeachers,
    enabled,
  });

export const useCreateInstructor = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateInstructorRequest) => usersApi.createInstructor(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.instructors }),
  });
};

export const useCreateStaff = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateStaffRequest) => usersApi.createStaff(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.staff }),
  });
};

export const useCreateTrainer = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateTrainerRequest) => usersApi.createTrainer(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.trainers }),
  });
};

export const useUpdateUser = (scope: 'instructors' | 'staff' | 'trainers') => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateUserRequest }) =>
      usersApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users', scope] }),
  });
};

export const useResetUserPassword = (scope: 'instructors' | 'staff' | 'trainers') => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => usersApi.resetPassword(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users', scope] }),
  });
};

export const useToggleUserStatus = (scope: 'instructors' | 'staff' | 'trainers') => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      active ? usersApi.deactivate(id) : usersApi.activate(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users', scope] }),
  });
};
