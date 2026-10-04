import { useEffect, useMemo, useRef, useState } from 'react';
import { loadComuni } from './comuni';
import type { Comune } from './comuni';

interface AddressValue {
  address?: string;
  city?: string;
  province?: string;
  region?: string;
  postalCode?: string;
  country?: string;
}

const inputClass =
  'w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors';
const readonlyClass =
  'w-full bg-zinc-900/50 border border-white/5 rounded-xl px-4 py-3 text-zinc-400 text-sm';
const labelClass =
  'block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5';

interface Props {
  value: AddressValue;
  onChange: (patch: Partial<AddressValue>) => void;
  showCountry?: boolean;
  /** False hides the read-only Regione field — the caller renders it elsewhere (e.g. inside a collapsible "Altro" section). */
  showRegion?: boolean;
}

export function AddressFields({ value, onChange, showCountry = true, showRegion = true }: Props) {
  const [comuni, setComuni] = useState<Comune[] | null>(null);
  const [query, setQuery] = useState(value.city ?? '');
  const [prevCity, setPrevCity] = useState(value.city);
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  // Sync the visible text when the city is changed from outside (edit mode).
  // Pattern: adjust state during render by tracking the previous prop value.
  if (value.city !== prevCity) {
    setPrevCity(value.city);
    setQuery(value.city ?? '');
  }

  useEffect(() => {
    let active = true;
    loadComuni().then((c) => { if (active) setComuni(c); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const suggestions = useMemo(() => {
    if (!comuni || query.trim().length < 2) return [];
    const q = query.trim().toLowerCase();
    const starts: Comune[] = [];
    const contains: Comune[] = [];
    for (const c of comuni) {
      const name = c.nome.toLowerCase();
      if (name.startsWith(q)) starts.push(c);
      else if (name.includes(q)) contains.push(c);
      if (starts.length >= 8) break;
    }
    return [...starts, ...contains].slice(0, 8);
  }, [comuni, query]);

  // CAP options for the currently selected comune (exact name + province match).
  const capOptions = useMemo(() => {
    if (!comuni || !value.city) return [];
    const match = comuni.find(
      (c) => c.nome === value.city && (!value.province || c.sigla === value.province),
    );
    return match?.cap ?? [];
  }, [comuni, value.city, value.province]);

  const selectComune = (c: Comune) => {
    setQuery(c.nome);
    setOpen(false);
    const cap =
      c.cap.length === 1
        ? c.cap[0]
        : c.cap.includes(value.postalCode ?? '')
          ? value.postalCode
          : '';
    onChange({ city: c.nome, province: c.sigla, region: c.regione, postalCode: cap });
  };

  return (
    <div className="flex flex-col gap-4">
      <div>
        <label className={labelClass}>Indirizzo</label>
        <input
          className={inputClass}
          value={value.address ?? ''}
          onChange={(e) => onChange({ address: e.target.value })}
          placeholder="Via e numero civico"
          autoComplete="address-line1"
        />
      </div>

      <div ref={boxRef} className="relative">
        <label className={labelClass}>Comune</label>
        <input
          className={inputClass}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
            // Manual edit (not a selection from the list) — clear the derived fields so a
            // stale comune's provincia/regione/CAP can't linger after the text changes.
            onChange({ city: e.target.value, province: '', region: '', postalCode: '' });
          }}
          onFocus={() => setOpen(true)}
          placeholder={comuni ? 'Inizia a digitare il comune…' : 'Caricamento comuni…'}
          autoComplete="off"
        />
        {open && suggestions.length > 0 && (
          <ul className="absolute z-30 mt-1 w-full bg-zinc-900 border border-white/10 rounded-xl overflow-hidden shadow-xl max-h-64 overflow-y-auto">
            {suggestions.map((c) => (
              <li key={`${c.nome}-${c.sigla}`}>
                <button
                  type="button"
                  onClick={() => selectComune(c)}
                  className="w-full text-left px-4 py-2.5 text-sm text-zinc-200 hover:bg-lime-400/10 hover:text-lime-300 flex items-center justify-between gap-2 transition-colors"
                >
                  <span>
                    {c.nome} <span className="text-zinc-500">({c.sigla})</span>
                  </span>
                  <span className="text-zinc-600 text-xs">{c.regione}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className={`grid grid-cols-1 gap-4 ${showRegion ? 'sm:grid-cols-2' : ''}`}>
        <div>
          <label className={labelClass}>Provincia</label>
          <input className={readonlyClass} value={value.province ?? ''} readOnly placeholder="—" />
        </div>
        {showRegion && (
          <div>
            <label className={labelClass}>Regione</label>
            <input className={readonlyClass} value={value.region ?? ''} readOnly placeholder="—" />
          </div>
        )}
      </div>

      <div className={`grid grid-cols-1 gap-4 ${showCountry ? 'sm:grid-cols-2' : ''}`}>
        <div>
          <label className={labelClass}>CAP</label>
          {capOptions.length > 1 ? (
            <select
              className={inputClass}
              value={value.postalCode ?? ''}
              onChange={(e) => onChange({ postalCode: e.target.value })}
              autoComplete="postal-code"
            >
              <option value="">— Seleziona —</option>
              {capOptions.map((cap) => (
                <option key={cap} value={cap}>{cap}</option>
              ))}
            </select>
          ) : (
            <input
              className={inputClass}
              value={value.postalCode ?? ''}
              onChange={(e) => onChange({ postalCode: e.target.value })}
              inputMode="numeric"
              maxLength={10}
              placeholder="CAP"
              autoComplete="postal-code"
            />
          )}
        </div>
        {showCountry && (
          <div>
            <label className={labelClass}>Paese</label>
            <input
              className={inputClass}
              value={value.country ?? ''}
              onChange={(e) => onChange({ country: e.target.value })}
              autoComplete="country-name"
            />
          </div>
        )}
      </div>
    </div>
  );
}
