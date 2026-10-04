import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { gymsApi } from './gymsApi';
import type { GymRequest } from '../../types/gym';

const KEYS = {
  all: ['gyms'] as const,
  detail: (id: number) => ['gyms', id] as const,
  mine: ['gyms', 'me'] as const,
};

export const useGyms = (enabled = true) =>
  useQuery({
    queryKey: KEYS.all,
    queryFn: gymsApi.getAll,
    enabled,
  });

export const useCreateGym = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: GymRequest) => gymsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all }),
  });
};

export const useUpdateGym = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: GymRequest }) =>
      gymsApi.update(id, data),
    onSuccess: (_result, { id }) => {
      qc.invalidateQueries({ queryKey: KEYS.all });
      qc.invalidateQueries({ queryKey: KEYS.detail(id) });
    },
  });
};

export const useDeleteGym = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => gymsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.all }),
  });
};

/** Current user's own gym (instructor self-management). `enabled` lets callers (e.g. the
 *  nav chrome) skip the request for roles that have no gym, such as ADMIN. Long `staleTime`
 *  because the gym name rarely changes and this is also used for a lightweight tenant chip. */
export const useMyGym = (enabled = true) =>
  useQuery({
    queryKey: KEYS.mine,
    queryFn: gymsApi.getMine,
    enabled,
    staleTime: 10 * 60 * 1000,
  });

export const useUpdateMyGym = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: GymRequest) => gymsApi.updateMine(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEYS.mine }),
  });
};
