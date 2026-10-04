import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { leadsApi, type LeadsParams } from './leadsApi';
import { MEMBER_KEYS } from '../members/useMembers';
import type { LeadRequest } from '../../types';

export const LEAD_KEYS = {
  all: ['leads'] as const,
  list: (params: LeadsParams) => ['leads', 'list', params] as const,
  detail: (id: number) => ['leads', id] as const,
};

export const useLeads = (params: LeadsParams = {}) =>
  useQuery({
    queryKey: LEAD_KEYS.list(params),
    queryFn: () => leadsApi.getAll(params),
  });

export const useLead = (id: number) =>
  useQuery({
    queryKey: LEAD_KEYS.detail(id),
    queryFn: () => leadsApi.getById(id),
    enabled: id > 0,
  });

export const useCreateLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: LeadRequest) => leadsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: LEAD_KEYS.all }),
  });
};

export const useUpdateLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: LeadRequest }) => leadsApi.update(id, data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: LEAD_KEYS.all });
      qc.invalidateQueries({ queryKey: LEAD_KEYS.detail(variables.id) });
    },
  });
};

export const useDeleteLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => leadsApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: LEAD_KEYS.all }),
  });
};

/**
 * Convert creates a Member, not just a lead update — invalidate the leads list AND the
 * members queries (pattern: useCheckIns.ts invalidating "today" across date variants;
 * useMembers.ts's own useCreateMember invalidating dashboard stats the same way).
 * `dashboard/stats` is still targeted as a raw literal since useCreateMember itself does
 * the same (no exported key for it) — only `members` has an exported root key to reuse.
 */
export const useConvertLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, email, birthDate }: { id: number; email: string; birthDate?: string }) =>
      leadsApi.convert(id, email, birthDate),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: LEAD_KEYS.all });
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.all });
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
};

export const useConfirmLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, trialDate }: { id: number; trialDate: string }) =>
      leadsApi.confirm(id, trialDate),
    onSuccess: () => qc.invalidateQueries({ queryKey: LEAD_KEYS.all }),
  });
};

export const useDeclineLead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => leadsApi.decline(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: LEAD_KEYS.all }),
  });
};
