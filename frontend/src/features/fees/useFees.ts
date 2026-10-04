import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { federationsApi, memberFeesApi } from './feesApi';
import type {
  FederationCardTypeRequest,
  FederationRequest,
  MemberFeeRequest,
} from '../../types';

export const FEE_KEYS = {
  federations: ['federations'] as const,
  memberFees: (memberId: number) => ['fees', 'member', memberId] as const,
  expiring: (days: number) => ['fees', 'expiring', days] as const,
  memberCards: (memberId: number) => ['fees', 'cards', memberId] as const,
};

export const useFederations = () =>
  useQuery({ queryKey: FEE_KEYS.federations, queryFn: federationsApi.getAll });

/** Ogni scrittura sui listini rinfresca l'intero albero federazioni: la pagina li mostra annidati. */
const useFederationMutation = <TArgs>(fn: (args: TArgs) => Promise<unknown>) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: FEE_KEYS.federations }),
  });
};

export const useCreateFederation = () =>
  useFederationMutation((data: FederationRequest) => federationsApi.create(data));

export const useUpdateFederation = () =>
  useFederationMutation(({ id, data }: { id: number; data: FederationRequest }) =>
    federationsApi.update(id, data));

export const useDeleteFederation = () =>
  useFederationMutation((id: number) => federationsApi.delete(id));

export const useAddCardType = () =>
  useFederationMutation(({ federationId, data }: { federationId: number; data: FederationCardTypeRequest }) =>
    federationsApi.addCardType(federationId, data));

export const useUpdateCardType = () =>
  useFederationMutation(({ cardTypeId, data }: { cardTypeId: number; data: FederationCardTypeRequest }) =>
    federationsApi.updateCardType(cardTypeId, data));

export const useDeleteCardType = () =>
  useFederationMutation((cardTypeId: number) => federationsApi.deleteCardType(cardTypeId));

export const useMemberFees = (memberId: number) =>
  useQuery({
    queryKey: FEE_KEYS.memberFees(memberId),
    queryFn: () => memberFeesApi.getByMember(memberId),
    enabled: memberId > 0,
  });

export const useChargeFee = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: MemberFeeRequest) => memberFeesApi.charge(memberId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: FEE_KEYS.memberFees(memberId) });
      qc.invalidateQueries({ queryKey: ['fees', 'expiring'] });
    },
  });
};

export const useDeleteFee = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (feeId: number) => memberFeesApi.delete(feeId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: FEE_KEYS.memberFees(memberId) });
      qc.invalidateQueries({ queryKey: ['fees', 'expiring'] });
    },
  });
};

export const useMemberFederationCards = (memberId: number) =>
  useQuery({
    queryKey: FEE_KEYS.memberCards(memberId),
    queryFn: () => memberFeesApi.getCards(memberId),
    enabled: memberId > 0,
  });

/** Salva il numero di tessera; stringa vuota = rimuovilo. */
export const useSaveFederationCard = (memberId: number) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ federationId, cardNumber }: { federationId: number; cardNumber: string }) =>
      cardNumber
        ? memberFeesApi.saveCard(memberId, federationId, cardNumber)
        : memberFeesApi.deleteCard(memberId, federationId),
    onSuccess: () => qc.invalidateQueries({ queryKey: FEE_KEYS.memberCards(memberId) }),
  });
};
