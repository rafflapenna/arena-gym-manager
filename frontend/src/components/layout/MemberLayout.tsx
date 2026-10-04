import { useEffect } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  User,
  CalendarCheck,
  CreditCard,
  LogIn,
  Dumbbell,
  KeyRound,
  LogOut,
  Columns2,
  FileText,
  UserPlus,
  type LucideIcon,
} from 'lucide-react';
import { setNavigate } from '../../services/apiClient';
import { useAuthStore } from '../../stores/authStore';

type NavItem = {
  to: string;
  label: string;
  /** Shorter label for the mobile bottom-nav's narrow slots (1/6 of 375px ≈ 62px); falls back
   * to `label` when the full word already fits. */
  mobileLabel?: string;
  Icon: LucideIcon;
  /** Shown only in the desktop sidebar — the mobile bottom nav stays at 6 items (no 7th slot). */
  desktopOnly?: boolean;
};

// Note: 6 items on the mobile bottom nav (no "Altro" overflow here, unlike AppLayout).
// "Porta un amico" is desktopOnly: reachable on mobile only via the card-link in MyProfilePage.
const navItems: NavItem[] = [
  { to: '/me',               label: 'Profilo',    Icon: User },
  { to: '/me/check-ins',     label: 'Presenze',   Icon: LogIn },
  { to: '/me/subscriptions', label: 'Abbonamenti', mobileLabel: 'Abbon.', Icon: CalendarCheck },
  { to: '/me/courses',       label: 'Corsi',      Icon: Columns2 },
  { to: '/me/payments',      label: 'Pagamenti',  Icon: CreditCard },
  { to: '/me/documents',     label: 'Documenti',  mobileLabel: 'Docum.', Icon: FileText },
  { to: '/me/friends',       label: 'Amici',      Icon: UserPlus, desktopOnly: true },
];

const bottomNavItems = navItems.filter((item) => !item.desktopOnly);

export function MemberLayout() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  useEffect(() => { setNavigate(navigate); }, [navigate]);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const initials = user?.username?.slice(0, 2).toUpperCase() ?? '??';

  return (
    <div className="min-h-screen bg-[#0b0b0d] flex">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex flex-col fixed left-0 top-0 h-full w-[236px] bg-[#121214] border-r border-white/6 z-30">
        <div className="flex items-center gap-[11px] px-5 pt-[22px] pb-4">
          <div className="w-[38px] h-[38px] bg-lime-400 rounded-[11px] flex items-center justify-center shrink-0">
            <Dumbbell className="w-5 h-5 text-[#0b0b0d]" strokeWidth={2.4} />
          </div>
          <div className="font-display leading-none">
            <p className="text-[17px] font-extrabold tracking-[.14em] text-zinc-100">GYM</p>
            <p className="text-xs font-bold tracking-[.12em] text-lime-400 mt-0.5">MANAGER</p>
          </div>
        </div>

        <nav className="flex-1 px-3 py-3 flex flex-col gap-0.5">
          {navItems.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/me'}
              className={({ isActive }) =>
                `flex items-center gap-3 py-3 px-3.5 rounded-xl text-[15px] transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${
                  isActive ? 'bg-lime-400 text-[#0b0b0d] font-bold' : 'text-zinc-400 font-semibold hover:text-zinc-100 hover:bg-white/5'
                }`
              }
            >
              <Icon className="w-[19px] h-[19px] shrink-0" strokeWidth={1.8} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="px-[18px] py-4 border-t border-white/6 flex items-center gap-[11px]">
          <div className="w-[34px] h-[34px] bg-lime-400/18 rounded-full flex items-center justify-center shrink-0">
            <span className="text-lime-400 text-xs font-bold">{initials}</span>
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-zinc-100 text-sm font-semibold truncate">{user?.username ?? '—'}</p>
            <p className="text-zinc-500 text-xs truncate">Socio</p>
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
        <header className="md:hidden flex items-center justify-between px-[18px] py-[14px] bg-[#141417] border-b border-white/6 sticky top-0 z-20">
          <div className="flex items-center gap-2.5">
            <div className="w-[30px] h-[30px] bg-lime-400 rounded-[9px] flex items-center justify-center shrink-0">
              <Dumbbell className="w-4 h-4 text-[#0b0b0d]" strokeWidth={2.4} />
            </div>
            <span className="font-display font-extrabold text-[15px] tracking-[.12em] text-zinc-100">
              GYM MANAGER
            </span>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => navigate('/change-password')}
              title="Cambia password"
              aria-label="Cambia password"
              className="flex items-center justify-center w-11 h-11 text-zinc-500 hover:text-lime-400 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              <KeyRound className="w-4 h-4" />
            </button>
            <button
              onClick={handleLogout}
              className="flex items-center justify-center min-w-11 h-11 px-2 text-zinc-500 hover:text-red-400 transition-colors text-[13px] font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              Esci
            </button>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto overflow-x-hidden pb-[calc(74px_+_env(safe-area-inset-bottom)_+_24px)] md:pb-0">
          <Outlet />
        </main>
      </div>

      {/* Mobile bottom nav */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-30 bg-[#131316]/97 backdrop-blur-lg border-t border-white/7 pb-[env(safe-area-inset-bottom)]">
        <div className="flex items-stretch h-[74px]">
          {bottomNavItems.map(({ to, label, mobileLabel, Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/me'}
              className={({ isActive }) =>
                `flex flex-col items-center justify-center gap-1 flex-1 min-w-0 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${
                  isActive ? 'text-lime-400' : 'text-zinc-500'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className="w-5 h-5 shrink-0" strokeWidth={1.9} />
                  <span className={`text-[11.5px] truncate w-full text-center ${isActive ? 'font-bold' : 'font-semibold'}`}>
                    {mobileLabel ?? label}
                  </span>
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  );
}
