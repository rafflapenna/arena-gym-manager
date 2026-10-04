import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { X, Search, ScanLine } from 'lucide-react';
import { useMembers } from '../members/useMembers';
import { useCreateCheckIn } from '../checkins/useCheckIns';
import { useDebounce } from '../../hooks/useDebounce';
import { getApiErrorMessage } from '../../utils/apiError';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import type { MemberResponse } from '../../types';

const MAX_RESULTS = 8;

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

function QuickAccessMemberRow({ member }: { member: MemberResponse }) {
  const createCheckIn = useCreateCheckIn(member.id);
  const [feedback, setFeedback] = useState<{ tone: 'ok' | 'warning'; text: string } | null>(null);
  const fullName = `${member.personalInfo.firstName} ${member.personalInfo.lastName}`;

  const handleCheckIn = () => {
    setFeedback(null);
    createCheckIn.mutate(undefined, {
      onSuccess: (result) => {
        if (result.outcome === 'WARNING') {
          setFeedback({ tone: 'warning', text: result.warnings[0] ?? 'Da verificare' });
        } else {
          setFeedback({ tone: 'ok', text: result.alreadyCheckedIn ? 'Già registrato oggi' : 'Ingresso registrato' });
        }
      },
      onError: (err) => setFeedback({ tone: 'warning', text: getApiErrorMessage(err) }),
    });
  };

  return (
    <div className="flex items-center gap-3 py-2.5">
      <div className="w-9 h-9 rounded-lg bg-lime-400/12 flex items-center justify-center shrink-0 font-bold text-xs text-lime-400">
        {initialsOf(fullName)}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-zinc-100 text-[14.5px] font-semibold truncate">{fullName}</p>
        {feedback && (
          <p className={`text-[12.5px] mt-0.5 ${feedback.tone === 'ok' ? 'text-lime-400' : 'text-amber-400'}`}>
            {feedback.text}
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={handleCheckIn}
        disabled={createCheckIn.isPending}
        className="shrink-0 inline-flex items-center gap-1.5 text-[13px] font-bold py-2 px-3.5 rounded-lg bg-lime-400 text-[#0b0b0d] active:scale-[0.98] transition-transform duration-150 disabled:opacity-50"
      >
        {createCheckIn.isPending ? '…' : 'Registra ingresso'}
      </button>
    </div>
  );
}

/** "Accesso rapido" bottom-sheet/modal: QR scanner shortcut + search-and-check-in by name. */
export function QuickAccessModal({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const trimmedQ = debouncedQ.trim();
  const { data: members, isLoading } = useMembers({ q: trimmedQ || undefined });

  const results = trimmedQ.length > 0 ? (members ?? []).slice(0, MAX_RESULTS) : [];

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleScanClick = () => {
    onClose();
    navigate('/check-ins/scan');
  };

  return (
    <>
      <button
        type="button"
        aria-label="Chiudi accesso rapido"
        onClick={onClose}
        className="fixed inset-0 z-40 bg-black/60"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Accesso rapido"
        className="fixed inset-x-0 bottom-0 z-50 sm:inset-0 sm:m-auto sm:h-fit sm:max-w-md bg-[#161618] border border-white/8 rounded-t-2xl sm:rounded-2xl max-h-[85dvh] flex flex-col overflow-hidden shadow-2xl pb-[env(safe-area-inset-bottom)] sm:pb-0"
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/7 shrink-0">
          <p className="text-zinc-100 text-[17px] font-bold">Accesso rapido</p>
          <button
            type="button"
            onClick={onClose}
            aria-label="Chiudi"
            className="flex items-center justify-center w-11 h-11 -mr-2 text-zinc-500 hover:text-zinc-200 transition-colors"
          >
            <X className="w-5 h-5" strokeWidth={2} />
          </button>
        </div>

        <div className="p-5 overflow-y-auto flex-1">
          <button
            type="button"
            onClick={handleScanClick}
            className="w-full inline-flex items-center justify-center gap-2.5 py-3.5 px-5 mb-5 rounded-xl bg-lime-400 text-[#0b0b0d] font-bold text-[15px] active:scale-[0.98] transition-transform duration-150"
          >
            <ScanLine className="w-[19px] h-[19px]" strokeWidth={2.2} />
            Scansiona QR
          </button>

          <div className="relative mb-1">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-[17px] h-[17px] text-zinc-500 pointer-events-none" />
            <input
              type="text"
              autoFocus
              placeholder="Cerca socio per nome…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="w-full bg-[#1c1c1f] border border-white/7 rounded-xl pl-[42px] pr-4 py-[12px] text-zinc-100 text-[15px] placeholder-zinc-500 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
            />
          </div>

          {trimmedQ.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">
              Digita il nome del socio da registrare.
            </p>
          ) : isLoading ? (
            <LoadingSpinner />
          ) : results.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun socio trovato.</p>
          ) : (
            <div className="divide-y divide-white/5 mt-2">
              {results.map((member) => (
                <QuickAccessMemberRow key={member.id} member={member} />
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
