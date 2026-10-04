import { useState } from 'react';
import { Search, X } from 'lucide-react';
import { useMembers } from './useMembers';
import { useDebounce } from '../../hooks/useDebounce';
import type { MemberRef } from '../../types';

type MemberPickerProps = {
  value: MemberRef | null;
  onChange: (member: MemberRef | null) => void;
  /** Escluso dai risultati: un socio non può aver portato se stesso. */
  excludeMemberId?: number;
  placeholder?: string;
};

/**
 * Cerca e seleziona un socio. Nasce per il "Portato da" della promozione passaparola, ma non
 * sa nulla di quella: è un picker generico.
 *
 * La ricerca parte solo da due caratteri — `useMembers` senza `q` scarica l'anagrafica intera
 * (paginata lato api fino a esaurimento), che è sprecato per un campo opzionale che spesso
 * resta vuoto.
 */
export function MemberPicker({ value, onChange, excludeMemberId, placeholder }: MemberPickerProps) {
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const enabled = debouncedQ.trim().length >= 2;

  const { data: members, isLoading } = useMembers(enabled ? { q: debouncedQ } : {});

  const results = (enabled && members ? members : [])
    .filter((m) => m.id !== excludeMemberId)
    .slice(0, 8);

  if (value) {
    return (
      <div className="flex items-center gap-2 bg-zinc-900 border border-white/5 rounded-xl px-4 py-3">
        <span className="flex-1 min-w-0 text-zinc-100 text-sm truncate">{value.name}</span>
        <button
          type="button"
          onClick={() => { onChange(null); setQ(''); }}
          aria-label="Rimuovi socio selezionato"
          className="text-zinc-500 hover:text-zinc-300 transition-colors shrink-0"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500 pointer-events-none" />
        <input
          type="text"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={placeholder ?? 'Cerca un socio…'}
          className="w-full bg-zinc-900 border border-white/5 rounded-xl pl-10 pr-4 py-3 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
        />
      </div>

      {enabled && (
        <div className="flex flex-col gap-1">
          {isLoading && <p className="text-zinc-600 text-xs px-1">Ricerca…</p>}
          {!isLoading && results.length === 0 && (
            <p className="text-zinc-600 text-xs px-1">Nessun socio trovato.</p>
          )}
          {results.map((m) => {
            const name = `${m.personalInfo.firstName} ${m.personalInfo.lastName}`.trim();
            return (
              <button
                key={m.id}
                type="button"
                onClick={() => { onChange({ id: m.id, name }); setQ(''); }}
                className="text-left bg-zinc-900 hover:bg-zinc-800 border border-white/5 rounded-lg px-3 py-2 text-zinc-200 text-sm transition-colors"
              >
                {name}
                <span className="text-zinc-600"> · {m.personalInfo.email}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
