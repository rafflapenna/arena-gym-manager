import { useState } from 'react';
import { Link } from 'react-router-dom';
import { User, AlertCircle, TrendingUp, UserPlus, CreditCard, Zap, type LucideIcon } from 'lucide-react';
import { useMembers } from '../members/useMembers';
import { useExpiringSubscriptions } from '../subscriptions/useSubscriptions';
import { useExpiringDocuments } from '../documents';
import { useDashboardStats } from './useDashboard';
import { QuickAccessModal } from './QuickAccessModal';
import { useAuthStore } from '../../stores/authStore';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { subscriptionLabel, daysRemaining, daysRemainingColorClass } from '../../utils/subscriptionUtils';
import { formatDayMonthWithArticle, formatMonthLong, formatWeekdayLongDate } from '../../utils/dateTime';

type StatCard = {
  label: string;
  value: string | number;
  subtext: string;
  Icon: LucideIcon;
  iconBg: string;
  iconColor: string;
  amber?: boolean;
  isLoading: boolean;
  isError: boolean;
};

const currencyFormatter = new Intl.NumberFormat('it-IT', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0,
});

function initialsOf(fullName: string): string {
  return (
    fullName
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toUpperCase() || '—'
  );
}

export function DashboardPage() {
  const [quickAccessOpen, setQuickAccessOpen] = useState(false);
  const { data: members, isLoading: membersLoading, isError: membersError } = useMembers();
  const { data: expiring, isLoading: expiringLoading, isError: expiringError } = useExpiringSubscriptions(30);
  const { data: expiringCerts, isLoading: certsLoading, isError: certsError } = useExpiringDocuments(30);
  const { data: dashboardStats, isLoading: statsLoading, isError: statsError } = useDashboardStats();
  const user = useAuthStore((s) => s.user);
  const displayName = user?.name?.trim() || user?.username || '';

  const activeCount = members?.filter((m) => m.active).length;
  const totalCount = members?.length;
  const today = new Date();

  const stats: StatCard[] = [
    {
      label: 'Soci attivi',
      value: activeCount ?? '—',
      subtext: totalCount !== undefined ? `su ${totalCount} iscritti` : ' ',
      Icon: User,
      iconBg: 'bg-lime-400/12',
      iconColor: 'text-lime-400',
      isLoading: membersLoading,
      isError: membersError,
    },
    {
      label: 'In scadenza',
      value: expiring?.length ?? '—',
      subtext: 'nei prossimi 30 giorni',
      Icon: AlertCircle,
      iconBg: 'bg-amber-400/12',
      iconColor: 'text-amber-400',
      amber: true,
      isLoading: expiringLoading,
      isError: expiringError,
    },
    {
      label: 'Entrate del mese',
      value: dashboardStats ? currencyFormatter.format(dashboardStats.monthlyRevenue) : '€ —',
      subtext: `incassato a ${formatMonthLong(today)}`,
      Icon: TrendingUp,
      iconBg: 'bg-cyan-400/10',
      iconColor: 'text-cyan-400',
      isLoading: statsLoading,
      isError: statsError,
    },
    {
      label: 'Nuovi soci',
      value: dashboardStats?.newMembersThisMonth ?? '—',
      subtext: 'questo mese',
      Icon: UserPlus,
      iconBg: 'bg-violet-400/12',
      iconColor: 'text-violet-400',
      isLoading: statsLoading,
      isError: statsError,
    },
  ];

  const upcomingExpirations = [...(expiring ?? [])]
    .sort((a, b) => a.endDate.localeCompare(b.endDate))
    .slice(0, 5);

  const upcomingCertExpirations = [...(expiringCerts ?? [])]
    .sort((a, b) => a.expiryDate.localeCompare(b.expiryDate))
    .slice(0, 5);

  return (
    <div className="px-5 py-8 md:px-12 md:py-10 max-w-6xl mx-auto font-body">
      <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-5 mb-7">
        <div>
          <p className="text-zinc-500 text-sm mb-1">{formatWeekdayLongDate(today)}</p>
          <h1 className="font-display font-bold text-[32px] md:text-[38px] text-zinc-100 uppercase leading-none">
            Bentornato, <span className="text-lime-400">{displayName}</span>
          </h1>
        </div>
        <div className="flex gap-2.5 flex-wrap">
          <button
            type="button"
            onClick={() => setQuickAccessOpen(true)}
            className="flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3 px-5 rounded-xl text-[15px] hover:bg-lime-300 active:scale-[0.98] transition-all duration-150"
          >
            <Zap className="w-[17px] h-[17px] shrink-0" strokeWidth={2.4} />
            Accesso rapido
          </button>
          <Link
            to="/enroll"
            className="flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3 px-5 rounded-xl text-[15px] hover:bg-lime-300 active:scale-[0.98] transition-all duration-150"
          >
            <UserPlus className="w-[17px] h-[17px] shrink-0" strokeWidth={2.4} />
            Nuovo socio
          </Link>
          <Link
            to="/members"
            title="Scegli il socio a cui registrare il pagamento"
            className="flex items-center justify-center gap-2 bg-[#1c1c1f] text-zinc-100 font-semibold py-3 px-5 rounded-xl text-[15px] border border-white/9 hover:bg-zinc-800 active:scale-[0.98] transition-all duration-150"
          >
            <CreditCard className="w-[17px] h-[17px] shrink-0" strokeWidth={1.9} />
            Registra pagamento — scegli socio
          </Link>
        </div>
      </div>

      {quickAccessOpen && <QuickAccessModal onClose={() => setQuickAccessOpen(false)} />}

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5 mb-7">
        {stats.map((stat) => {
          const StatIcon = stat.Icon;
          return (
            <div
              key={stat.label}
              className={`bg-[#1c1c1f] rounded-2xl p-5 flex flex-col border ${
                stat.amber ? 'border-amber-400/25' : 'border-white/6'
              }`}
            >
              <div className="flex items-center gap-2.5 mb-3">
                <span className={`w-[34px] h-[34px] ${stat.iconBg} rounded-[10px] flex items-center justify-center shrink-0`}>
                  <StatIcon className={`w-[17px] h-[17px] ${stat.iconColor}`} strokeWidth={1.9} />
                </span>
                <span className="text-zinc-300 text-[14.5px] font-semibold leading-tight">{stat.label}</span>
              </div>
              {stat.isLoading ? (
                <div className="h-[46px] w-16 bg-white/6 rounded-lg animate-pulse" />
              ) : stat.isError ? (
                <p className="font-display font-bold text-[28px] leading-none text-red-400">Errore</p>
              ) : (
                <p className={`font-display font-bold text-[46px] leading-none ${stat.amber ? 'text-amber-400' : 'text-zinc-100'}`}>
                  {stat.value}
                </p>
              )}
              <p className="mt-1.5 text-[13.5px] text-zinc-500">{stat.isLoading ? ' ' : stat.isError ? 'Riprova più tardi' : stat.subtext}</p>
            </div>
          );
        })}
      </div>

      <div className="bg-[#161618] border border-white/6 rounded-2xl overflow-hidden">
        <div className="flex items-center justify-between px-[22px] py-4 border-b border-white/7">
          <span className="text-zinc-100 text-[16.5px] font-bold">Abbonamenti in scadenza</span>
          <Link to="/subscriptions" className="text-lime-400 text-sm font-semibold hover:underline">
            Vedi tutti
          </Link>
        </div>
        {expiringLoading ? (
          <LoadingSpinner />
        ) : expiringError ? (
          <div className="p-[22px]">
            <ErrorMessage message="Errore nel caricamento degli abbonamenti in scadenza." />
          </div>
        ) : upcomingExpirations.length === 0 ? (
          <p className="px-[22px] py-6 text-zinc-500 text-[15px]">Nessun abbonamento in scadenza nei prossimi 30 giorni.</p>
        ) : (
          upcomingExpirations.map((sub, i) => {
            const days = daysRemaining(sub.endDate);
            const isExpired = sub.status.code === 'EXPIRED' || (days ?? 0) <= 0;
            return (
              <div
                key={sub.id}
                className={`flex flex-wrap items-center gap-y-2 gap-x-4 px-[22px] py-3.5 ${
                  i < upcomingExpirations.length - 1 ? 'border-b border-white/5' : ''
                }`}
              >
                <div className="flex items-center gap-4 w-full sm:w-auto sm:flex-1 min-w-0">
                  <div
                    className={`w-[42px] h-[42px] rounded-[11px] flex items-center justify-center font-bold text-sm shrink-0 ${
                      isExpired ? 'bg-amber-400/12 text-amber-400' : 'bg-lime-400/12 text-lime-400'
                    }`}
                  >
                    {initialsOf(sub.memberFullName)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-zinc-100 text-[15.5px] font-semibold truncate">{sub.memberFullName}</p>
                    <p className="text-zinc-500 text-[13.5px] mt-0.5 truncate">
                      {subscriptionLabel(sub)} · {isExpired ? 'scaduto' : 'scade'} {formatDayMonthWithArticle(sub.endDate)}
                    </p>
                  </div>
                </div>
                {/* Seconda riga a 375px, allineata sotto il nome (58px = avatar + gap): la riga
                    unica originale lasciava al nome solo pochi caratteri prima di troncarlo. */}
                <div className="flex items-center gap-3 ml-[58px] sm:ml-0 shrink-0">
                  <span className={`text-[15px] font-bold whitespace-nowrap ${daysRemainingColorClass(days)}`}>
                    {days !== null && days > 0 ? `${days} giorni` : '0 giorni'}
                  </span>
                  <Link
                    to={`/members/${sub.memberId}`}
                    className={`inline-flex items-center gap-1.5 text-[13.5px] font-bold py-2 px-4 rounded-[10px] whitespace-nowrap active:scale-[0.98] transition-transform duration-150 ${
                      isExpired
                        ? 'bg-amber-400/13 text-amber-400'
                        : 'bg-[#1c1c1f] border border-white/9 text-zinc-200'
                    }`}
                  >
                    Rinnova
                  </Link>
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="bg-[#161618] border border-white/6 rounded-2xl overflow-hidden mt-5">
        <div className="flex items-center justify-between px-[22px] py-4 border-b border-white/7">
          <span className="text-zinc-100 text-[16.5px] font-bold">Certificati in scadenza</span>
        </div>
        {certsLoading ? (
          <LoadingSpinner />
        ) : certsError ? (
          <div className="p-[22px]">
            <ErrorMessage message="Errore nel caricamento dei certificati in scadenza." />
          </div>
        ) : upcomingCertExpirations.length === 0 ? (
          <p className="px-[22px] py-6 text-zinc-500 text-[15px]">
            Nessun certificato medico in scadenza nei prossimi 30 giorni.
          </p>
        ) : (
          upcomingCertExpirations.map((cert, i) => {
            const isExpired = cert.daysUntilExpiry <= 0;
            return (
              <div
                key={cert.documentId}
                className={`flex flex-wrap items-center gap-y-2 gap-x-4 px-[22px] py-3.5 ${
                  i < upcomingCertExpirations.length - 1 ? 'border-b border-white/5' : ''
                }`}
              >
                <div className="flex items-center gap-4 w-full sm:w-auto sm:flex-1 min-w-0">
                  <div
                    className={`w-[42px] h-[42px] rounded-[11px] flex items-center justify-center font-bold text-sm shrink-0 ${
                      isExpired ? 'bg-amber-400/12 text-amber-400' : 'bg-lime-400/12 text-lime-400'
                    }`}
                  >
                    {initialsOf(cert.memberFullName)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-zinc-100 text-[15.5px] font-semibold truncate">{cert.memberFullName}</p>
                    <p className="text-zinc-500 text-[13.5px] mt-0.5 truncate">
                      Certificato medico · {isExpired ? 'scaduto' : 'scade'} {formatDayMonthWithArticle(cert.expiryDate)}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3 ml-[58px] sm:ml-0 shrink-0">
                  <span className={`text-[15px] font-bold whitespace-nowrap ${daysRemainingColorClass(cert.daysUntilExpiry)}`}>
                    {cert.daysUntilExpiry > 0 ? `${cert.daysUntilExpiry} giorni` : '0 giorni'}
                  </span>
                  <Link
                    to={`/members/${cert.memberId}`}
                    className={`inline-flex items-center gap-1.5 text-[13.5px] font-bold py-2 px-4 rounded-[10px] whitespace-nowrap active:scale-[0.98] transition-transform duration-150 ${
                      isExpired
                        ? 'bg-amber-400/13 text-amber-400'
                        : 'bg-[#1c1c1f] border border-white/9 text-zinc-200'
                    }`}
                  >
                    Vedi
                  </Link>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
