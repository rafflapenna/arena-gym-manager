import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qrApi } from './qrApi';

const QR_KEYS = {
  mine: ['qr', 'me'] as const,
  member: (memberId: number) => ['qr', 'member', memberId] as const,
};

export const useMyQr = () =>
  useQuery({
    queryKey: QR_KEYS.mine,
    queryFn: qrApi.getMyQr,
  });

/** Lazy by default: pass `enabled: true` once the operator asks to reveal the card. */
export const useMemberQr = (memberId: number, enabled: boolean) =>
  useQuery({
    queryKey: QR_KEYS.member(memberId),
    queryFn: () => qrApi.getMemberQr(memberId),
    enabled: enabled && memberId > 0,
  });

export const useRotateMemberQr = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => qrApi.rotateMemberQr(memberId),
    onSuccess: (data) => {
      qc.setQueryData(QR_KEYS.member(memberId), data);
    },
  });
};

export const useCheckInByQr = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (token: string) => qrApi.checkInByQr(token),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['check-ins', 'today'] });
    },
  });
};
