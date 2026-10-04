import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { LogIn, CheckCircle2, QrCode, ChevronRight } from 'lucide-react';
import { useSelfCheckIn, useMyCheckIns } from './useMyCheckIns';
import { useMySubscriptions } from './useMe';
import { getApiErrorMessage } from '../../utils/apiError';
import { formatTime, isSameDay, startOfToday } from '../../utils/dateTime';
import { pickCurrentSubscription, daysRemaining } from '../../utils/subscriptionUtils';
import type { CheckInResponse } from '../../types';

const weekdayFmt = new Intl.DateTimeFormat('it-IT', { weekday: 'long' });
const dayMonthFmt = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long' });

function startOfWeekMonday(d: Date): Date {
  const date = new Date(d);
  const day = date.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  date.setDate(date.getDate() + diff);
  date.setHours(0, 0, 0, 0);
  return date;
}

function groupLabel(checkInDate: Date, thisWeekStart: Date): string {
  const weekStart = startOfWeekMonday(checkInDate);
  const diffWeeks = Math.round((thisWeekStart.getTime() - weekStart.getTime()) / (7 * 86_400_000));
  if (diffWeeks === 0) return 'Questa settimana';
  if (diffWeeks === 1) return 'Settimana scorsa';
  return `Settimana del ${dayMonthFmt.format(weekStart)}`;
}

export function MyCheckInPage() {
  const selfCheckIn = useSelfCheckIn();
  const { data: history = [], isLoading } = useMyCheckIns();
  const { data: subscriptions } = useMySubscriptions();
  const [result, setResult] = useState<CheckInResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const currentSub = pickCurrentSubscription(subscriptions ?? []);
  const isActive = currentSub?.status.code === 'ACTIVE';
  const days = daysRemaining(currentSub?.endDate);

  const handleCheckIn = () => {
    setError(null);
    setResult(null);
    selfCheckIn.mutate(undefined, {
      onSuccess: (data) => setResult(data),
      // 409 (non idoneo) -> getApiErrorMessage estrae il detail "Rivolgiti alla reception…"
      onError: (err) => setError(getApiErrorMessage(err)),
    });
  };

  const groups = useMemo(() => {
    const today = startOfToday();
    const thisWeekStart = startOfWeekMonday(today);
    const map = new Map<string, CheckInResponse[]>();
    for (const c of history) {
      const d = new Date(c.checkInAt);
      const label = groupLabel(d, thisWeekStart);
      const list = map.get(label) ?? [];
      list.push(c);
      map.set(label, list);
    }
    return Array.from(map.entries());
  }, [history]);

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-[18px]">
        Le mie presenze
      </h1>

      <Link
        to="/me/qr"
        className="flex items-center gap-3 bg-[#1c1c1f] border border-white/6 rounded-[16px] px-4 py-[14px] mb-3.5 hover:bg-white/[.04] transition-colors"
      >
        <span className="w-10 h-10 rounded-xl bg-lime-400/12 flex items-center justify-center shrink-0">
          <QrCode className="w-5 h-5 text-lime-400" strokeWidth={1.9} />
        </span>
        <span className="flex-1 min-w-0">
          <p className="text-zinc-100 text-[15px] font-semibold">La mia tessera QR</p>
          <p className="text-zinc-500 text-[13px]">Mostrala alla reception per un ingresso rapido</p>
        </span>
        <ChevronRight className="w-[18px] h-[18px] text-zinc-600 shrink-0" strokeWidth={2} />
      </Link>

      <div
        className="rounded-[20px] p-[22px] mb-3.5 text-center border"
        style={{
          background: isActive
            ? 'linear-gradient(160deg, rgba(163,230,53,.14), rgba(163,230,53,.04))'
            : 'linear-gradient(160deg, rgba(251,191,36,.12), rgba(251,191,36,.03))',
          borderColor: isActive ? 'rgba(163,230,53,.25)' : 'rgba(251,191,36,.22)',
        }}
      >
        <button
          onClick={handleCheckIn}
          disabled={selfCheckIn.isPending}
          aria-busy={selfCheckIn.isPending}
          className="w-full inline-flex items-center justify-center gap-2.5 py-[18px] px-8 bg-lime-400 text-[#0b0b0d] font-bold text-lg rounded-2xl hover:bg-lime-300 active:scale-[0.98] transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <LogIn className="w-[22px] h-[22px]" strokeWidth={2.2} />
          {selfCheckIn.isPending ? 'Registrazione…' : 'Entra in palestra'}
        </button>
        <p className="mt-3 text-[14px] text-zinc-300">
          {isActive ? (
            <>
              Abbonamento attivo · scade tra{' '}
              <strong className="text-lime-400">{days !== null ? Math.max(days, 0) : '—'} giorni</strong>
            </>
          ) : (
            <span className="text-amber-400 font-semibold">Abbonamento non attivo — rivolgiti alla reception</span>
          )}
        </p>
      </div>

      <div role="status" aria-live="polite">
        {result && (
          <div className="flex items-center gap-2.5 bg-lime-400/10 border border-lime-400/22 rounded-[13px] px-4 py-[13px] mb-[22px] text-[14.5px] font-semibold text-[#c8f284]">
            <CheckCircle2 className="w-[18px] h-[18px] text-lime-400 shrink-0" strokeWidth={2} />
            {result.alreadyCheckedIn
              ? `Sei già in palestra dalle ${formatTime(result.checkInAt)}`
              : `Sei in palestra — entrata registrata alle ${formatTime(result.checkInAt)}`}
          </div>
        )}
      </div>

      {error && (
        <div role="alert" className="mb-[22px] px-4 py-3 bg-red-400/10 border border-red-400/20 rounded-xl text-red-400 text-sm">
          {error}
        </div>
      )}

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="w-8 h-8 border-2 border-lime-400 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : history.length === 0 ? (
        <p className="text-zinc-500 text-[15px] text-center py-10">Nessuna presenza registrata.</p>
      ) : (
        groups.map(([label, entries]) => (
          <div key={label} className="mb-[18px]">
            <p className="text-[#8b8b94] text-[13px] font-bold tracking-wide uppercase mb-[10px]">{label}</p>
            <div className="flex flex-col gap-2">
              {entries.map((c) => {
                const d = new Date(c.checkInAt);
                const today = isSameDay(d, startOfToday());
                return (
                  <div
                    key={c.id}
                    className="flex items-center gap-[13px] bg-[#1c1c1f] border border-white/5 rounded-[13px] px-4 py-[13px]"
                  >
                    <span className={`font-display font-bold text-xl w-[52px] shrink-0 ${today ? 'text-lime-400' : 'text-zinc-200'}`}>
                      {formatTime(c.checkInAt)}
                    </span>
                    <span className="text-zinc-100 text-[15px] font-semibold flex-1">
                      {today ? 'Oggi' : dayMonthFmt.format(d)}
                    </span>
                    <span className="text-zinc-500 text-[13px]">{weekdayFmt.format(d)}</span>
                  </div>
                );
              })}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
