import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Calendar, Smartphone, UserRound, QrCode, ScanLine } from 'lucide-react';
import { useTodayCheckIns } from './useCheckIns';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatTime, formatRelativeDayLabel, todayIsoLocal, dateToIsoLocal } from '../../utils/dateTime';

function addDaysIso(iso: string, delta: number): string {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + delta);
  return dateToIsoLocal(d);
}

export function TodayCheckInsPage() {
  const [date, setDate] = useState(todayIsoLocal());
  const { data: checkIns = [], isLoading, isError } = useTodayCheckIns(date);
  const dateInputRef = useRef<HTMLInputElement>(null);

  const isToday = date >= todayIsoLocal();
  const toVerifyCount = checkIns.filter((c) => c.outcome === 'WARNING').length;

  return (
    <div className="px-5 py-8 md:px-10 md:py-10 max-w-3xl mx-auto font-body">
      <div className="flex items-center justify-between gap-4 mb-1.5 flex-wrap">
        <h1 className="font-display font-bold text-[34px] text-zinc-100 uppercase leading-none">Presenze</h1>
        <div className="flex items-center bg-[#1c1c1f] border border-white/9 rounded-xl overflow-hidden">
          <button
            type="button"
            onClick={() => setDate((d) => addDaysIso(d, -1))}
            className="flex items-center justify-center w-11 h-11 text-zinc-400 hover:text-zinc-200 transition-colors"
            aria-label="Giorno precedente"
          >
            <ChevronLeft className="w-4 h-4" strokeWidth={2.2} />
          </button>
          <div className="relative">
            <button
              type="button"
              onClick={() => dateInputRef.current?.showPicker?.() ?? dateInputRef.current?.focus()}
              className="flex items-center gap-2 min-h-11 px-1.5 text-zinc-100 text-[15px] font-semibold"
            >
              <Calendar className="w-4 h-4 text-lime-400" strokeWidth={1.9} />
              {formatRelativeDayLabel(date)}
            </button>
            <input
              ref={dateInputRef}
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value || todayIsoLocal())}
              className="absolute inset-0 opacity-0 cursor-pointer"
              aria-label="Scegli data"
            />
          </div>
          <button
            type="button"
            onClick={() => setDate((d) => addDaysIso(d, 1))}
            disabled={isToday}
            className="flex items-center justify-center w-11 h-11 text-zinc-400 hover:text-zinc-200 transition-colors disabled:text-zinc-700 disabled:cursor-not-allowed"
            aria-label="Giorno successivo"
          >
            <ChevronRight className="w-4 h-4" strokeWidth={2.2} />
          </button>
        </div>
      </div>

      <Link
        to="/check-ins/scan"
        className="inline-flex items-center gap-2 mb-6 text-[14.5px] font-bold py-3 px-5 rounded-xl bg-lime-400 text-[#0b0b0d] active:scale-[0.98] transition-transform duration-150"
      >
        <ScanLine className="w-4 h-4" strokeWidth={2.2} />
        Scansiona QR
      </Link>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <ErrorMessage message="Errore nel caricamento delle presenze." />
      ) : (
        <>
          <p className="text-zinc-400 text-[15px] mb-[22px]">
            <strong className="text-zinc-100 font-bold">
              {checkIns.length} {checkIns.length === 1 ? 'presenza' : 'presenze'}
            </strong>
            {toVerifyCount > 0 && ` · ${toVerifyCount} da verificare`}
          </p>
          {checkIns.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessuna presenza registrata.</p>
          ) : (
            <div className="flex flex-col gap-2.5">
              {checkIns.map((c) => {
                const isWarning = c.outcome === 'WARNING';
                return (
                  <div
                    key={c.id}
                    className={`flex items-center gap-4 bg-[#1c1c1f] border rounded-[14px] px-[18px] py-[14px] ${
                      isWarning ? 'border-amber-400/30' : 'border-white/5'
                    }`}
                  >
                    <span className="font-display font-bold text-2xl text-lime-400 w-16 shrink-0">
                      {formatTime(c.checkInAt)}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-zinc-100 text-base font-semibold truncate">{c.memberFullName}</p>
                      {isWarning && (
                        <p className="text-amber-400 text-[13.5px] mt-0.5">Abbonamento scaduto — da verificare</p>
                      )}
                    </div>
                    {isWarning ? (
                      <Link
                        to={`/members/${c.memberId}`}
                        className="inline-flex items-center gap-[7px] bg-amber-400/13 text-amber-400 text-[13.5px] font-bold py-[9px] px-4 rounded-[10px] whitespace-nowrap active:scale-[0.98] transition-transform duration-150"
                      >
                        Verifica
                      </Link>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/6 text-zinc-400 text-[13px] font-semibold whitespace-nowrap">
                        {c.source === 'SELF' ? (
                          <Smartphone className="w-[13px] h-[13px]" strokeWidth={1.9} />
                        ) : c.source === 'QR' ? (
                          <QrCode className="w-[13px] h-[13px]" strokeWidth={1.9} />
                        ) : (
                          <UserRound className="w-[13px] h-[13px]" strokeWidth={1.9} />
                        )}
                        {c.source === 'SELF' ? 'App' : c.source === 'QR' ? 'QR' : 'Reception'}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
}
