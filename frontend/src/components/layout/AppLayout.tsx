import { useEffect, useMemo, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  LayoutGrid,
  User,
  Users,
  CalendarCheck,
  DoorClosed,
  ChevronDown,
  Columns2,
  Building2,
  Dumbbell,
  KeyRound,
  LogOut,
  MoreHorizontal,
  ScanLine,
  Tag,
  UserPlus,
  BadgeEuro,
  type LucideIcon,
} from 'lucide-react';
import { setNavigate } from '../../services/apiClient';
import { useAuthStore } from '../../stores/authStore';
import { useMyGym } from '../../features/gyms/useGyms';
import { useExpiringSubscriptions } from '../../features/subscriptions/useSubscriptions';
import { IconButton } from '../ui/IconButton';
import { Button } from '../ui/Button';
import type { Role } from '../../types/auth';

type NavItem = {
  to: string;
  label: string;
  /** Shorter label for the mobile bottom-nav's narrow slots (~1/4 of 375px); falls back to
   * `label` when the full word already fits (e.g. "Soci", "Prove", "Quote"). */
  mobileLabel?: string;
  Icon?: LucideIcon;
  roles?: Role[];
  /** Marks the item that should carry the expiring-subscriptions count badge. */
  badge?: 'expiring';
  /** Forces an exact NavLink match (avoids highlighting e.g. /users when on /users/trainers). */
  end?: boolean;
};

type NavGroup = {
  key: string;
  /** null = the "Operativo" group: no header row, always expanded, items shown flat. */
  label: string | null;
  Icon?: LucideIcon;
  collapsible: boolean;
  items: NavItem[];
};

/** Grouped nav — "Operativo" is always flat; "Soci"/"Corsi e pacchetti"/"Palestra" collapse (see AppLayout). */
const navGroups: NavGroup[] = [
  {
    key: 'operativo',
    label: null,
    collapsible: false,
    items: [
      { to: '/dashboard', label: 'Bacheca', Icon: LayoutGrid, roles: ['STAFF', 'GYM_MANAGER'] },
    ],
  },
  {
    key: 'soci',
    label: 'Soci',
    Icon: Users,
    collapsible: true,
    items: [
      { to: '/members', label: 'Soci', Icon: User, roles: ['STAFF', 'GYM_MANAGER'] },
      { to: '/subscriptions', label: 'Abbonamenti', mobileLabel: 'Abbon.', Icon: CalendarCheck, roles: ['STAFF', 'GYM_MANAGER'], badge: 'expiring' },
      { to: '/check-ins', label: 'Presenze', Icon: DoorClosed, roles: ['STAFF', 'GYM_MANAGER'] },
      { to: '/subscription-types', label: 'Listino', Icon: Tag, roles: ['STAFF', 'GYM_MANAGER'] },
      // Appended last (not after "Soci"): MOBILE_NAV_LIMIT keeps only the first 4 flat items
      // as primary bottom-nav icons — inserting Prove earlier bumped "Presenze" (a
      // high-frequency daily action) into the "Altro" overflow instead of Prove itself.
      { to: '/leads', label: 'Prove', Icon: UserPlus, roles: ['STAFF', 'GYM_MANAGER'] },
      { to: '/fees', label: 'Quote', Icon: BadgeEuro, roles: ['STAFF', 'GYM_MANAGER'] },
    ],
  },
  {
    key: 'catalogo',
    label: 'Corsi e pacchetti',
    Icon: Columns2,
    collapsible: true,
    items: [
      { to: '/courses', label: 'Corsi', roles: ['STAFF', 'GYM_MANAGER'] },
      { to: '/packages', label: 'Pacchetti', roles: ['STAFF', 'GYM_MANAGER'] },
    ],
  },
  {
    key: 'palestra',
    label: 'Palestra',
    Icon: Building2,
    collapsible: true,
    items: [
      { to: '/my-gym', label: 'Gestione palestra', roles: ['GYM_MANAGER'] },
      { to: '/users', label: 'Gestione staff', roles: ['GYM_MANAGER'] },
      { to: '/users', label: 'Gestione capipalestra', roles: ['ADMIN'] },
      { to: '/gyms', label: 'Palestre', roles: ['ADMIN'] },
    ],
  },
];

const roleLabels: Record<Role, string> = {
  ADMIN: 'Amministratore',
  STAFF: 'Staff',
  GYM_MANAGER: 'Capopalestra',
  TRAINER: 'Insegnante',
  MEMBER: 'Socio',
};

const MOBILE_NAV_LIMIT = 5;

function CountBadge({ count, tone = 'amber' }: { count: number; tone?: 'amber' | 'lime' }) {
  if (count <= 0) return null;
  const toneClasses = tone === 'amber' ? 'bg-amber-400/15 text-amber-400' : 'bg-lime-400/15 text-lime-400';
  return (
    <span
      className={`inline-flex items-center justify-center min-w-[22px] h-5 px-1.5 rounded-full text-[12px] font-bold leading-none ${toneClasses}`}
    >
      {count > 99 ? '99+' : count}
    </span>
  );
}

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const [isMoreOpen, setIsMoreOpen] = useState(false);
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  const canSeeGymScopedData = user?.role === 'STAFF' || user?.role === 'GYM_MANAGER';
  const myGymQuery = useMyGym(canSeeGymScopedData);
  const expiringQuery = useExpiringSubscriptions(7, canSeeGymScopedData);
  const tenantName = myGymQuery.data?.name;
  const expiringCount = expiringQuery.data?.length ?? 0;

  useEffect(() => { setNavigate(navigate); }, [navigate]);

  const visibleGroups = useMemo(
    () =>
      navGroups
        .map((group) => ({
          ...group,
          items: group.items.filter((item) => !item.roles || (user?.role && item.roles.includes(user.role))),
        }))
        .filter((group) => group.items.length > 0),
    [user]
  );

  // When a single group remains (e.g. ADMIN sees only "Palestra"), nothing to disambiguate —
  // render it flat, same as "Operativo", instead of a lone collapsible header.
  const soloGroup = visibleGroups.length === 1;

  const activeGroupKey = visibleGroups.find((group) =>
    group.items.some((item) => location.pathname.startsWith(item.to))
  )?.key;

  const isGroupOpen = (group: NavGroup) =>
    !group.collapsible || soloGroup || (openGroups[group.key] ?? false) || group.key === activeGroupKey;

  const toggleGroup = (key: string) => setOpenGroups((prev) => ({ ...prev, [key]: !(prev[key] ?? false) }));

  const flatItems = visibleGroups.flatMap((group) => group.items);
  const hasOverflow = flatItems.length > MOBILE_NAV_LIMIT;
  const mobilePrimaryItems = hasOverflow ? flatItems.slice(0, MOBILE_NAV_LIMIT - 1) : flatItems;
  const mobileOverflowItems = hasOverflow ? flatItems.slice(MOBILE_NAV_LIMIT - 1) : [];
  const mobileOverflowGroups = hasOverflow
    ? visibleGroups
        .map((group) => ({
          label: group.label,
          items: group.items.filter((item) => mobileOverflowItems.includes(item)),
        }))
        .filter((group) => group.items.length > 0)
    : [];
  const isOverflowActive = mobileOverflowItems.some((item) => location.pathname.startsWith(item.to));

  const badgeCountFor = (item: NavItem) => (item.badge === 'expiring' ? expiringCount : 0);

  useEffect(() => {
    if (!isMoreOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsMoreOpen(false);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isMoreOpen]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const displayName = user?.name?.trim() || user?.username || '—';
  const initials = displayName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase() || '??';

  return (
    <div className="min-h-screen bg-[#0b0b0d] flex">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex flex-col fixed left-0 top-0 h-full w-[236px] bg-[#121214] border-r border-white/6 z-30">
        <div className="px-5 pt-[22px] pb-4">
          <div className="flex items-center gap-[11px]">
            <div className="w-[38px] h-[38px] bg-lime-400 rounded-[11px] flex items-center justify-center shrink-0">
              <Dumbbell className="w-5 h-5 text-[#0b0b0d]" strokeWidth={2.4} />
            </div>
            <div className="font-display leading-none">
              <p className="text-[17px] font-extrabold tracking-[.14em] text-zinc-100">GYM</p>
              <p className="text-xs font-bold tracking-[.12em] text-lime-400 mt-0.5">MANAGER</p>
            </div>
          </div>
          {canSeeGymScopedData && (
            <div className="mt-3 min-h-[1.75rem]">
              {tenantName && (
                <p className="inline-flex max-w-full items-center gap-1.5 rounded-full bg-white/5 px-[11px] py-[5px] text-xs font-medium text-zinc-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-lime-400 shrink-0" />
                  <span className="truncate">{tenantName}</span>
                </p>
              )}
            </div>
          )}
        </div>

        <nav className="flex-1 px-3 py-2 flex flex-col gap-0.5 overflow-y-auto">
          {visibleGroups.map((group) => {
            const isFlat = !group.collapsible || soloGroup;
            const open = isGroupOpen(group);
            return (
              <div key={group.key} className="flex flex-col gap-0.5">
                {group.label && !isFlat && group.Icon && (
                  <>
                    <div className="h-px bg-white/7 mx-3.5 my-2.5" />
                    <button
                      type="button"
                      onClick={() => toggleGroup(group.key)}
                      aria-expanded={open}
                      className="flex items-center gap-3 py-3 px-3.5 rounded-xl text-[15px] font-semibold text-zinc-300 hover:bg-white/5 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60"
                    >
                      <group.Icon className="w-[19px] h-[19px] shrink-0" strokeWidth={1.8} />
                      <span className="flex-1 text-left truncate">{group.label}</span>
                      <ChevronDown
                        className={`w-[15px] h-[15px] text-zinc-500 shrink-0 transition-transform duration-150 ${
                          open ? 'rotate-180' : ''
                        }`}
                        strokeWidth={2.2}
                      />
                    </button>
                  </>
                )}
                {(isFlat || open) &&
                  group.items.map((item) => {
                    const { to, label, Icon, end } = item;
                    const badgeCount = badgeCountFor(item);
                    return (
                      <NavLink
                        key={`${to}-${label}`}
                        to={to}
                        end={end}
                        className={({ isActive }) =>
                          `flex items-center gap-3 rounded-xl text-[15px] transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${
                            Icon ? 'py-3 px-3.5' : 'py-[10px] pr-3.5 pl-[45px] text-[14.5px]'
                          } ${
                            isActive
                              ? 'bg-lime-400 text-[#0b0b0d] font-bold'
                              : `text-zinc-400 hover:text-zinc-100 hover:bg-white/5 ${Icon ? 'font-semibold' : 'font-medium'}`
                          }`
                        }
                      >
                        {Icon && <Icon className="w-[19px] h-[19px] shrink-0" strokeWidth={1.8} />}
                        <span className="flex-1 truncate">{label}</span>
                        <CountBadge count={badgeCount} />
                      </NavLink>
                    );
                  })}
              </div>
            );
          })}
        </nav>

        <div className="px-[18px] py-4 border-t border-white/6 flex items-center gap-[11px]">
          <div className="w-[34px] h-[34px] bg-lime-400/18 rounded-full flex items-center justify-center shrink-0">
            <span className="text-lime-400 text-xs font-bold">{initials}</span>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-zinc-100 text-sm font-semibold truncate">{displayName}</p>
            <p className="text-zinc-500 text-xs truncate">{user ? roleLabels[user.role] : ''}</p>
          </div>
          <button
            onClick={() => navigate('/change-password')}
            title="Cambia password"
            className="text-zinc-500 hover:text-lime-400 transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
          >
            <KeyRound className="w-[17px] h-[17px]" strokeWidth={1.8} />
          </button>
          <button
            onClick={handleLogout}
            title="Esci"
            className="text-zinc-500 hover:text-red-400 transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
          >
            <LogOut className="w-[17px] h-[17px]" strokeWidth={1.8} />
          </button>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 md:ml-[236px] flex flex-col min-h-screen min-w-0">
        <header className="md:hidden flex items-center justify-between gap-3 px-5 py-4 bg-[#141417] border-b border-white/6 sticky top-0 z-20">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-7 h-7 bg-lime-400 rounded-lg flex items-center justify-center shrink-0">
              <Dumbbell className="w-4 h-4 text-[#0b0b0d]" strokeWidth={2.4} />
            </div>
            <span className="font-display font-extrabold text-sm tracking-[.12em] text-zinc-100 shrink-0">
              GYM MANAGER
            </span>
            {canSeeGymScopedData && tenantName && (
              <span className="truncate max-w-[6rem] rounded-full bg-white/5 px-2 py-0.5 text-[10px] font-medium text-zinc-400">
                {tenantName}
              </span>
            )}
          </div>
          <div className="flex items-center gap-1 shrink-0">
            <IconButton onClick={() => navigate('/change-password')} title="Cambia password" aria-label="Cambia password">
              <KeyRound className="w-4 h-4" />
            </IconButton>
            <Button variant="ghost" onClick={handleLogout}>
              <LogOut className="w-4 h-4" />
              <span>Esci</span>
            </Button>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto overflow-x-hidden pb-[calc(96px_+_env(safe-area-inset-bottom))] md:pb-0">
          <Outlet />
        </main>
      </div>

      {/* Mobile "more" overflow sheet */}
      {hasOverflow && (
        <>
          <button
            type="button"
            aria-label="Chiudi menu"
            onClick={() => setIsMoreOpen(false)}
            className={`md:hidden fixed inset-0 z-30 bg-black/50 transition-opacity duration-150 motion-reduce:transition-none ${
              isMoreOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
            }`}
            tabIndex={-1}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-hidden={!isMoreOpen}
            className={`md:hidden fixed bottom-[calc(70px_+_env(safe-area-inset-bottom))] inset-x-0 z-40 bg-[#131316] border-t border-white/7 rounded-t-2xl overflow-hidden shadow-2xl transition-transform duration-150 ease-out motion-reduce:transition-none ${
              // Closed: slide past its own height PLUS the bottom-nav offset (its 70px row plus
              // the safe-area strip it now pads), or the top sliver of the sheet stays visible
              // above the nav (it's anchored at bottom-[calc(70px + safe-area)]).
              isMoreOpen ? 'translate-y-0' : 'translate-y-[calc(100%+70px+env(safe-area-inset-bottom))] pointer-events-none'
            }`}
          >
            <div className="flex justify-center pt-2.5 pb-1">
              <span aria-hidden="true" className="h-1 w-9 rounded-full bg-zinc-700" />
            </div>
            <div className="flex flex-col py-1 max-h-[70dvh] overflow-y-auto">
              {canSeeGymScopedData && (
                <button
                  type="button"
                  onClick={() => {
                    setIsMoreOpen(false);
                    navigate('/check-ins/scan');
                  }}
                  className="flex items-center gap-3 px-5 py-3 text-[15px] font-bold text-lime-400 hover:bg-white/5 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60"
                >
                  <ScanLine className="w-[19px] h-[19px] shrink-0" strokeWidth={2} />
                  <span className="flex-1 text-left truncate">Scansiona QR</span>
                </button>
              )}
              {mobileOverflowGroups.map((group) => (
                <div key={group.label ?? 'default'} className="flex flex-col">
                  {group.label && (
                    <p className="px-5 pt-3 pb-1 text-[13px] font-semibold tracking-wide text-zinc-500">
                      {group.label}
                    </p>
                  )}
                  {group.items.map((item) => {
                    const { to, label, end } = item;
                    const badgeCount = badgeCountFor(item);
                    return (
                      <NavLink
                        key={`${to}-${label}`}
                        to={to}
                        end={end}
                        onClick={() => setIsMoreOpen(false)}
                        className={({ isActive }) =>
                          `flex items-center gap-3 px-5 py-3 text-[15px] font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${
                            isActive ? 'text-lime-400' : 'text-zinc-300 hover:text-zinc-100 hover:bg-white/5'
                          }`
                        }
                      >
                        <span className="flex-1 truncate">{label}</span>
                        <CountBadge count={badgeCount} />
                      </NavLink>
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {/* Mobile bottom nav */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-[#131316]/95 backdrop-blur-lg border-t border-white/7 pb-[env(safe-area-inset-bottom)]">
        <div className="flex items-stretch h-[70px] px-2">
          {mobilePrimaryItems.map((item) => {
            const { to, label, mobileLabel, Icon, end } = item;
            const badgeCount = badgeCountFor(item);
            return (
              <NavLink
                key={`${to}-${label}`}
                to={to}
                end={end}
                className={({ isActive }) =>
                  `flex flex-col items-center justify-center gap-1 flex-1 min-w-0 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-lg ${
                    isActive ? 'text-lime-400' : 'text-zinc-500'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={`relative inline-flex items-center justify-center w-11 h-[30px] rounded-full ${
                        isActive ? 'bg-lime-400/15' : ''
                      }`}
                    >
                      {Icon && <Icon className="w-5 h-5 shrink-0" strokeWidth={1.8} />}
                      {badgeCount > 0 && (
                        <span className="absolute -top-1 right-1.5 min-w-[15px] h-[15px] px-[3px] rounded-full bg-amber-400 text-[#0b0b0d] text-[10px] font-bold leading-[15px] text-center">
                          {badgeCount > 9 ? '9+' : badgeCount}
                        </span>
                      )}
                    </span>
                    <span className={`text-[11px] tracking-wide truncate w-full text-center ${isActive ? 'font-bold' : 'font-semibold'}`}>
                      {mobileLabel ?? label}
                    </span>
                  </>
                )}
              </NavLink>
            );
          })}
          {hasOverflow && (
            <button
              type="button"
              onClick={() => setIsMoreOpen((open) => !open)}
              aria-haspopup="true"
              aria-expanded={isMoreOpen}
              className={`flex flex-col items-center justify-center gap-1 flex-1 min-w-0 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-lg ${
                isMoreOpen || isOverflowActive ? 'text-lime-400' : 'text-zinc-500'
              }`}
            >
              <MoreHorizontal className="w-5 h-5 shrink-0" />
              <span className="text-[11px] font-semibold tracking-wide truncate w-full text-center">Altro</span>
            </button>
          )}
        </div>
      </nav>
    </div>
  );
}
