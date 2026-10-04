import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { checkInsApi } from './checkInsApi';

const CHECK_IN_KEYS = {
  all: ['check-ins'] as const,
  byMember: (memberId: number) => ['check-ins', 'member', memberId] as const,
  today: (date?: string) => ['check-ins', 'today', date ?? 'today'] as const,
};

export const useMemberCheckIns = (memberId: number) =>
  useQuery({
    queryKey: CHECK_IN_KEYS.byMember(memberId),
    queryFn: () => checkInsApi.getByMember(memberId),
    enabled: memberId > 0,
  });

export const useTodayCheckIns = (date?: string) =>
  useQuery({
    queryKey: CHECK_IN_KEYS.today(date),
    queryFn: () => checkInsApi.getToday(date),
  });

export const useCreateCheckIn = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => checkInsApi.create(memberId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: CHECK_IN_KEYS.byMember(memberId) });
      // Invalidate all "today" variants regardless of the selected date.
      qc.invalidateQueries({ queryKey: ['check-ins', 'today'] });
    },
  });
};
