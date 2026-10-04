import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { disciplinesApi } from './disciplinesApi';
import type { DisciplineRequest } from '../../types';

export const DISCIPLINE_KEYS = {
  all: ['disciplines'] as const,
};

export const useDisciplines = () =>
  useQuery({
    queryKey: DISCIPLINE_KEYS.all,
    queryFn: disciplinesApi.getAll,
  });

/** First lookup-mutation of the project — disciplines are per-gym, not a global lookup. */
export const useCreateDiscipline = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: DisciplineRequest) => disciplinesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: DISCIPLINE_KEYS.all }),
  });
};

export const useUpdateDiscipline = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: DisciplineRequest }) =>
      disciplinesApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: DISCIPLINE_KEYS.all }),
  });
};

export const useDeleteDiscipline = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => disciplinesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: DISCIPLINE_KEYS.all }),
  });
};
