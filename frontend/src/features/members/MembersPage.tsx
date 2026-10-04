import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, UserPlus, ChevronRight, Plus } from 'lucide-react';
import { useMembers, useToggleMemberStatus } from './useMembers';
import { useSubscriptions } from '../subscriptions/useSubscriptions';
import { useCreateCheckIn } from '../checkins/useCheckIns';
import { useDebounce } from '../../hooks/useDebounce';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import {
  pickCurrentSubscription,
  memberStatus,
  daysRemaining,
  daysRemainingColorClass,
  memberStatusDotClass,
  memberStatusPillClass,
  memberStatusAvatarClass,
  type MemberStatus,
} from '../../utils/subscriptionUtils';
import { formatDayMonthYear, formatDayMonthWithArticle } from '../../utils/dateTime';
import type { MemberResponse, SubscriptionResponse } from '../../types';

type Filter = 'all' | 'active' | 'expired' | 'inactive';

const filterLabels: Record<Filter, string> = {
  all: 'Tutti',
  active: 'Attivi',
  expired: 'Scaduti',
  inactive: 'Inattivi',
};

const statusPillClasses = memberStatusPillClass;
const statusDotClasses = memberStatusDotClass;
const avatarClasses = memberStatusAvatarClass;

function initials(m: MemberResponse) {
  return `${m.personalInfo.firstName[0] ?? ''}${m.personalInfo.lastName[0] ?? ''}`.toUpperCase();
}

type MemberRow = {
  member: MemberResponse;
  status: MemberStatus;
  currentSub: SubscriptionResponse | null;
  days: number | null;
};

export function MembersPage() {
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const debouncedQ = useDebounce(q, 300);

  const { data: members, isLoading, isError } = useMembers({ q: debouncedQ || undefined });
  const { data: subscriptions } = useSubscriptions();

  const rows: MemberRow[] = useMemo(() => {
    if (!members) return [];
    const byMember = new Map<number, SubscriptionResponse[]>();
    for (const s of subscriptions ?? []) {
      const list = byMember.get(s.memberId) ?? [];
      list.push(s);
      byMember.set(s.memberId, list);
    }
    return members.map((member) => {
      const currentSub = pickCurrentSubscription(byMember.get(member.id) ?? []);
      const status = memberStatus(member, currentSub);
      return { member, status, currentSub, days: daysRemaining(currentSub?.endDate) };
    });
  }, [members, subscriptions]);

  const counts = useMemo(
    () => ({
      all: rows.length,
      active: rows.filter((r) => r.status === 'active').length,
      expired: rows.filter((r) => r.status === 'expired').length,
      inactive: rows.filter((r) => r.status === 'inactive').length,
    }),
    [rows]
  );

  const filteredRows = filter === 'all' ? rows : rows.filter((r) => r.status === filter);

  return (
    <div className="px-5 py-7 md:px-10 md:py-10 max-w-6xl mx-auto font-body pb-6">
      <div className="hidden md:flex items-center justify-between mb-6">
        <h1 className="font-display font-bold text-[40px] text-zinc-100 uppercase leading-none">Soci</h1>
        <Link
          to="/enroll"
          className="flex items-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-[22px] py-[13px] rounded-[13px] text-[15px] hover:bg-lime-300 active:scale-[0.98] transition-all duration-150"
        >
          <UserPlus className="w-[18px] h-[18px]" strokeWidth={2.4} />
          Nuovo socio
        </Link>
      </div>

      <div className="flex md:hidden items-center justify-between mb-4">
        <h1 className="font-display font-bold text-[32px] text-zinc-100 uppercase leading-none">Soci</h1>
        <span className="text-zinc-400 text-sm">{rows.length} iscritti</span>
      </div>

      <div className="flex flex-col md:flex-row gap-3 mb-5">
        <div className="relative flex-1">
          <Search className="absolute left-[18px] top-1/2 -translate-y-1/2 w-[18px] h-[18px] text-zinc-500 pointer-events-none" />
          <input
            type="text"
            placeholder="Cerca per nome, email, telefono…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            className="w-full bg-[#1c1c1f] border border-white/7 rounded-[13px] pl-[46px] pr-4 py-[14px] text-zinc-100 text-base placeholder-zinc-500 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
          />
        </div>

        {/* Desktop: segmented control */}
        <div className="hidden md:flex bg-[#1c1c1f] border border-white/7 rounded-[13px] p-1 gap-0.5">
          {(Object.keys(filterLabels) as Filter[]).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`px-[18px] py-[10px] rounded-[10px] text-sm font-semibold whitespace-nowrap transition-colors ${
                filter === f ? 'bg-lime-400 text-[#0b0b0d]' : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              {filterLabels[f]} · {counts[f]}
            </button>
          ))}
        </div>

        {/* Mobile: scrollable chips */}
        <div className="flex md:hidden gap-2 overflow-x-auto pb-1 -mx-5 px-5">
          {(Object.keys(filterLabels) as Filter[]).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={`shrink-0 inline-flex items-center justify-center min-h-11 px-4 rounded-full text-[13.5px] font-semibold whitespace-nowrap transition-colors ${
                filter === f
                  ? 'bg-lime-400 text-[#0b0b0d]'
                  : 'bg-[#1c1c1f] border border-white/8 text-zinc-400'
              }`}
            >
              {filterLabels[f]}
            </button>
          ))}
        </div>
      </div>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei soci." />}

      {members && filteredRows.length === 0 && (
        <p className="text-zinc-500 text-[15px] text-center py-10">Nessun socio trovato.</p>
      )}

      {/* Desktop table */}
      {filteredRows.length > 0 && (
        <div className="hidden md:block bg-[#161618] border border-white/6 rounded-2xl overflow-hidden">
          <div className="grid grid-cols-[2.4fr_2.6fr_1.2fr_1.3fr_1.7fr_44px] gap-3 items-center px-[22px] py-[13px] border-b border-white/8 text-[12.5px] font-semibold tracking-wide text-[#8b8b94] uppercase">
            <span>Nome</span>
            <span>Contatti</span>
            <span>Iscritto il</span>
            <span>Giorni rimasti</span>
            <span>Stato</span>
            <span />
          </div>
          {filteredRows.map(({ member, status, days }, i) => (
            <Link
              key={member.id}
              to={`/members/${member.id}`}
              className={`grid grid-cols-[2.4fr_2.6fr_1.2fr_1.3fr_1.7fr_44px] gap-3 items-center px-[22px] py-[15px] hover:bg-white/[.03] transition-colors duration-150 ${
                i < filteredRows.length - 1 ? 'border-b border-white/5' : ''
              } ${status === 'inactive' ? 'opacity-65' : ''}`}
            >
              <div className="flex items-center gap-[13px] min-w-0">
                <div className={`w-[42px] h-[42px] rounded-[11px] flex items-center justify-center font-bold text-sm shrink-0 ${avatarClasses[status]}`}>
                  {initials(member)}
                </div>
                <span className="text-zinc-100 text-base font-semibold truncate">
                  {member.personalInfo.firstName} {member.personalInfo.lastName}
                </span>
              </div>
              <div className="min-w-0">
                <p className="text-zinc-300 text-[14.5px] leading-snug" style={{ overflowWrap: 'anywhere' }}>
                  {member.personalInfo.email}
                </p>
                {member.personalInfo.phone && (
                  <p className="text-zinc-500 text-[13.5px] mt-0.5">{member.personalInfo.phone}</p>
                )}
              </div>
              <span className="text-zinc-400 text-[14.5px]">{formatDayMonthYear(member.registrationDate)}</span>
              <span className="text-zinc-100 text-[15.5px] font-bold">
                {days !== null && status !== 'inactive' ? (
                  <>
                    <span className={daysRemainingColorClass(days)}>{Math.max(days, 0)}</span>{' '}
                    <span className="text-zinc-500 text-[13px] font-medium">giorni</span>
                  </>
                ) : (
                  <span className="text-zinc-500 font-semibold">—</span>
                )}
              </span>
              <span
                className={`justify-self-start inline-flex items-center gap-[7px] px-[13px] py-[7px] rounded-full text-[13px] font-semibold whitespace-nowrap ${statusPillClasses[status]}`}
              >
                <span className={`w-[7px] h-[7px] rounded-full ${statusDotClasses[status]}`} />
                {status === 'active' ? 'Attivo' : status === 'inactive' ? 'Inattivo' : 'Scaduto'}
              </span>
              <ChevronRight className="w-[18px] h-[18px] text-zinc-600" strokeWidth={2} />
            </Link>
          ))}
        </div>
      )}

      {filteredRows.length > 0 && (
        <div className="hidden md:flex gap-5 mt-4 mx-1 text-[13.5px] text-[#8b8b94] items-center">
          <span className="inline-flex items-center gap-[7px]">
            <span className="w-2 h-2 rounded-full bg-lime-400" />
            Attivo con abbonamento
          </span>
          <span className="inline-flex items-center gap-[7px]">
            <span className="w-2 h-2 rounded-full bg-amber-400" />
            Attivo, abbonamento scaduto
          </span>
          <span className="inline-flex items-center gap-[7px]">
            <span className="w-2 h-2 rounded-full bg-zinc-500" />
            Inattivo
          </span>
        </div>
      )}

      {/* Mobile card list */}
      <div className="flex md:hidden flex-col gap-3">
        {filteredRows.map(({ member, status, currentSub, days }) => (
          <MobileMemberCard key={member.id} member={member} status={status} currentSub={currentSub} days={days} />
        ))}
      </div>

      <Link
        to="/enroll"
        aria-label="Nuovo socio"
        className="md:hidden fixed bottom-[86px] right-[18px] w-[58px] h-[58px] rounded-[18px] bg-lime-400 flex items-center justify-center shadow-lg shadow-lime-400/25 z-20 active:scale-[0.98] transition-transform duration-150"
      >
        <Plus className="w-[26px] h-[26px] text-[#0b0b0d]" strokeWidth={2.4} />
      </Link>
    </div>
  );
}

function MobileMemberCard({
  member,
  status,
  currentSub,
  days,
}: {
  member: MemberResponse;
  status: MemberStatus;
  currentSub: SubscriptionResponse | null;
  days: number | null;
}) {
  const createCheckIn = useCreateCheckIn(member.id);
  const toggleStatus = useToggleMemberStatus();
  const [feedback, setFeedback] = useState<string | null>(null);
  const feedbackTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => () => {
    if (feedbackTimeoutRef.current) clearTimeout(feedbackTimeoutRef.current);
  }, []);

  const showFeedback = (text: string) => {
    setFeedback(text);
    if (feedbackTimeoutRef.current) clearTimeout(feedbackTimeoutRef.current);
    feedbackTimeoutRef.current = setTimeout(() => setFeedback(null), 2500);
  };

  const handleCheckIn = () => {
    createCheckIn.mutate(undefined, {
      onSuccess: (r) => showFeedback(r.alreadyCheckedIn ? 'Già presente oggi' : 'Presenza registrata'),
      onError: () => showFeedback('Errore, riprova'),
    });
  };

  const handleReactivate = () => {
    toggleStatus.mutate(
      { id: member.id, active: false },
      { onError: () => showFeedback('Errore, riprova') },
    );
  };

  const subtitle =
    status === 'active'
      ? `Attivo · ${days ?? 0} giorni rimasti`
      : status === 'expired'
        ? `Abbonamento scaduto ${currentSub ? formatDayMonthWithArticle(currentSub.endDate) : ''}`
        : 'Inattivo';

  return (
    <div
      className={`bg-[#1c1c1f] rounded-2xl overflow-hidden border ${
        status === 'expired' ? 'border-amber-400/25' : 'border-white/5'
      } ${status === 'inactive' ? 'opacity-70' : ''}`}
    >
      <div className="flex items-center gap-[13px] px-4 pt-[15px] pb-3">
        <div className={`w-[46px] h-[46px] rounded-xl flex items-center justify-center font-bold text-sm shrink-0 ${avatarClasses[status]}`}>
          {initials(member)}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-zinc-100 text-[17px] font-semibold truncate">
            {member.personalInfo.firstName} {member.personalInfo.lastName}
          </p>
          <p className="text-zinc-400 text-[13.5px] mt-0.5" style={{ overflowWrap: 'anywhere' }}>
            {member.personalInfo.email}
          </p>
          <div className="flex items-center gap-1.5 mt-1">
            <span className={`w-[7px] h-[7px] rounded-full ${statusDotClasses[status]}`} />
            <span className={`text-[12.5px] font-semibold ${status === 'active' ? 'text-lime-400' : status === 'expired' ? 'text-amber-400' : 'text-zinc-400'}`}>
              {subtitle}
            </span>
          </div>
        </div>
      </div>
      <div className="flex border-t border-white/6">
        {status === 'active' && (
          <button
            onClick={handleCheckIn}
            disabled={createCheckIn.isPending}
            className="flex-1 text-center py-3 text-[14px] font-semibold text-lime-400 border-r border-white/6 active:bg-white/5 transition-colors duration-150 disabled:opacity-50"
          >
            {feedback ?? (createCheckIn.isPending ? 'Registro…' : 'Check-in')}
          </button>
        )}
        {status === 'expired' && (
          <Link
            to={`/members/${member.id}/enroll`}
            className="flex-1 text-center py-3 text-[14px] font-semibold text-amber-400 border-r border-white/6 active:bg-white/5 transition-colors duration-150"
          >
            Rinnova
          </Link>
        )}
        {status === 'inactive' && (
          <button
            onClick={handleReactivate}
            disabled={toggleStatus.isPending}
            className="flex-1 text-center py-3 text-[14px] font-semibold text-zinc-300 border-r border-white/6 active:bg-white/5 transition-colors duration-150 disabled:opacity-50"
          >
            {feedback ?? 'Riattiva'}
          </button>
        )}
        <Link
          to={`/members/${member.id}`}
          className="flex-1 text-center py-3 text-[14px] font-semibold text-zinc-300 active:bg-white/5 transition-colors duration-150"
        >
          Scheda
        </Link>
      </div>
    </div>
  );
}
