import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { packagesApi } from './packagesApi';
import type { PackageRequest } from '../../types';

export const PACKAGE_KEYS = {
  all: ['packages'] as const,
  detail: (id: number) => ['packages', id] as const,
};

export const usePackages = () =>
  useQuery({
    queryKey: PACKAGE_KEYS.all,
    queryFn: packagesApi.getAll,
  });

export const usePackage = (id: number) =>
  useQuery({
    queryKey: PACKAGE_KEYS.detail(id),
    queryFn: () => packagesApi.getById(id),
    enabled: id > 0,
  });

export const useCreatePackage = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: PackageRequest) => packagesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all }),
  });
};

export const useUpdatePackage = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: PackageRequest }) =>
      packagesApi.update(id, data),
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all });
      qc.invalidateQueries({ queryKey: PACKAGE_KEYS.detail(variables.id) });
    },
  });
};

export const useDeletePackage = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => packagesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: PACKAGE_KEYS.all }),
  });
};
