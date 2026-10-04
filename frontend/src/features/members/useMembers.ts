import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { membersApi } from './membersApi';
import type { MemberRequest } from '../../types';

export const MEMBER_KEYS = {
  all: ['members'] as const,
  list: (params: object) => ['members', 'list', params] as const,
  detail: (id: number) => ['members', id] as const,
};

export const useMembers = (params: { q?: string; active?: boolean } = {}) =>
  useQuery({
    queryKey: MEMBER_KEYS.list(params),
    queryFn: () => membersApi.getAll(params),
    placeholderData: keepPreviousData,
  });

export const useMember = (id: number) =>
  useQuery({
    queryKey: MEMBER_KEYS.detail(id),
    queryFn: () => membersApi.getById(id),
    enabled: id > 0,
  });

export const useCreateMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: MemberRequest) => membersApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.all });
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
};

export const useUpdateMember = (id: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: MemberRequest) => membersApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.all });
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.detail(id) });
    },
  });
};

export const useToggleMemberStatus = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      active ? membersApi.deactivate(id) : membersApi.activate(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.all });
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
};

export const useDeleteMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => membersApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: MEMBER_KEYS.all });
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
};

export const useCreateMemberAccount = () =>
  useMutation({
    mutationFn: (id: number) => membersApi.createAccount(id),
  });
