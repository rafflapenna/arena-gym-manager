import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { meService } from '../../services/meService';

const ME_CHECK_INS_KEY = ['me', 'check-ins'] as const;

export const useMyCheckIns = () =>
  useQuery({
    queryKey: ME_CHECK_INS_KEY,
    queryFn: meService.getCheckIns,
  });

export const useSelfCheckIn = () => {
  const qc = useQueryClient();
  return useMutation({
    // Rejected with 409 when the member is not eligible (no active subscription);
    // the page handles that error (no interception here).
    mutationFn: () => meService.createCheckIn(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ME_CHECK_INS_KEY }),
  });
};
