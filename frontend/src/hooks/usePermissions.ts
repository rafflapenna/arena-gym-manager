import { useAuthStore } from '../stores/authStore';

export function usePermissions() {
  const role = useAuthStore((s) => s.user?.role);

  const canManageMembers = role === 'STAFF' || role === 'GYM_MANAGER';

  return {
    canEdit: canManageMembers,
    canViewPayments: canManageMembers,
    canViewSubscriptions: canManageMembers,
    canManageLookups: role === 'ADMIN',
    // User-creation hierarchy
    canManageInstructors: role === 'ADMIN',
    canManageStaff: role === 'GYM_MANAGER',
    canManageUsers: role === 'ADMIN' || role === 'GYM_MANAGER',
    canCreateMemberAccount: role === 'STAFF' || role === 'GYM_MANAGER',
    canCheckIn: role === 'STAFF' || role === 'GYM_MANAGER',
    // Courses / packages / disciplines catalog (per-gym, not a global lookup — ADMIN excluded)
    canManageCatalog: role === 'STAFF' || role === 'GYM_MANAGER',
    // Leads/trials (per-gym, same perimeter as members — ADMIN excluded)
    canManageLeads: role === 'STAFF' || role === 'GYM_MANAGER',
    isAdmin: role === 'ADMIN',
    isInstructor: role === 'GYM_MANAGER',
    isTrainer: role === 'TRAINER',
    isMember: role === 'MEMBER',
  };
}
