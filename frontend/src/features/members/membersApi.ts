import apiClient from '../../services/apiClient';
import type { MemberRequest, MemberResponse, PagedResponse } from '../../types';

type MembersParams = {
  q?: string;
  active?: boolean;
  size?: number;
  sortBy?: string;
  sortDir?: 'asc' | 'desc';
};

export const membersApi = {
  // Fetches every page so callers (list, counts) see the true full set — a single 200-item
  // page silently dropped members beyond it once a gym passed 200 members.
  getAll: async (params: MembersParams = {}): Promise<MemberResponse[]> => {
    const size = params.size ?? 200;
    const first = await apiClient
      .get<PagedResponse<MemberResponse>>('/members', { params: { size, ...params } })
      .then((r) => r.data);
    if (first.last) return first.content;

    const all = [...first.content];
    for (let page = 1; page < first.totalPages && all.length < first.totalElements; page++) {
      const next = await apiClient
        .get<PagedResponse<MemberResponse>>('/members', { params: { size, page, ...params } })
        .then((r) => r.data);
      all.push(...next.content);
      if (next.last) break;
    }
    return all;
  },

  getById: (id: number) =>
    apiClient.get<MemberResponse>(`/members/${id}`).then((r) => r.data),

  create: (data: MemberRequest) =>
    apiClient.post<MemberResponse>('/members', data).then((r) => r.data),

  update: (id: number, data: MemberRequest) =>
    apiClient.put<MemberResponse>(`/members/${id}`, data).then((r) => r.data),

  activate: (id: number) =>
    apiClient.patch<MemberResponse>(`/members/${id}/activate`).then((r) => r.data),

  deactivate: (id: number) =>
    apiClient.patch<MemberResponse>(`/members/${id}/deactivate`).then((r) => r.data),

  delete: (id: number) =>
    apiClient.delete(`/members/${id}`),

  createAccount: (id: number) =>
    apiClient.post<{ username: string; inviteLink: string }>(`/members/${id}/account`).then((r) => r.data),
};
