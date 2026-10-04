import { useEffect } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { GraduationCap, Dumbbell, KeyRound, LogOut, type LucideIcon } from 'lucide-react';
import { setNavigate } from '../../services/apiClient';
import { useAuthStore } from '../../stores/authStore';

type NavItem = {
  to: string;
  label: string;
  Icon: LucideIcon;
};

const navItems: NavItem[] = [
  { to: '/trainer', label: 'I miei corsi', Icon: GraduationCap },
];

/** Minimal shell for the TRAINER area — real pages (course list, attendance) live under /trainer. */
export function TrainerLayout() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  useEffect(() => { setNavigate(navigate); }, [navigate]);

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
    <div className="min-h-screen bg-gray-950 flex">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex flex-col fixed left-0 top-0 h-full w-64 bg-gradient-to-b from-zinc-900 to-zinc-950 border-r border-white/5 z-30">
        <div className="flex items-center gap-3 px-6 py-7 border-b border-white/5">
          <div className="w-9 h-9 bg-lime-400 rounded-xl flex items-center justify-center shrink-0">
            <Dumbbell className="w-5 h-5 text-[#0b0b0d]" strokeWidth={2.5} />
          </div>
          <div>
            <p className="font-display font-bold text-base tracking-widest text-zinc-100 uppercase leading-none">
              Gym
            </p>
            <p className="font-display font-semibold text-xs tracking-wider text-lime-400 uppercase">
              Manager
            </p>
          </div>
        </div>

        <nav className="flex-1 px-3 py-6 flex flex-col gap-1">
          {navItems.map(({ to, label, Icon }) => (
            <NavLink
              key={to}
              to={to}
              end
              className={({ isActive }) =>
                `group relative flex items-center gap-3 pl-4 pr-3 py-3 rounded-xl text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${
                  isActive ? 'bg-white/5 text-zinc-100' : 'text-zinc-400 hover:text-zinc-100 hover:bg-white/5'
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    aria-hidden="true"
                    className={`absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-full bg-lime-400 transition-opacity duration-150 ${
                      isActive ? 'opacity-100' : 'opacity-0'
                    }`}
                  />
                  <Icon className="w-5 h-5 shrink-0" />
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="px-5 py-5 border-t border-white/5">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 bg-lime-400/20 rounded-full flex items-center justify-center shrink-0">
              <span className="text-lime-400 text-xs font-bold">{initials}</span>
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-zinc-100 text-sm font-semibold truncate">{displayName}</p>
              <p className="text-zinc-500 text-xs truncate">Insegnante</p>
            </div>
            <button
              onClick={() => navigate('/change-password')}
              title="Cambia password"
              className="text-zinc-500 hover:text-lime-400 transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              <KeyRound className="w-4 h-4" />
            </button>
            <button
              onClick={handleLogout}
              title="Esci"
              className="text-zinc-500 hover:text-red-400 transition-colors shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main */}
      <div className="flex-1 md:ml-64 flex flex-col min-h-screen min-w-0">
        <header className="md:hidden flex items-center justify-between px-5 py-4 bg-zinc-900 border-b border-white/5 sticky top-0 z-20">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 bg-lime-400 rounded-lg flex items-center justify-center shrink-0">
              <Dumbbell className="w-4 h-4 text-[#0b0b0d]" strokeWidth={2.5} />
            </div>
            <span className="font-display font-bold text-sm tracking-widest text-zinc-100 uppercase">
              Gym Manager
            </span>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/change-password')}
              title="Cambia password"
              className="text-zinc-500 hover:text-lime-400 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              <KeyRound className="w-4 h-4" />
            </button>
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 text-zinc-500 hover:text-red-400 transition-colors text-xs font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 rounded-md"
            >
              <LogOut className="w-4 h-4" />
              <span>Esci</span>
            </button>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto overflow-x-hidden pb-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
