import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { useBeltGrades } from '../lookups/useLookups';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import { Textarea } from '../../components/ui/Textarea';
import { AddressFields, loadComuni } from '../../components/address';
import type { Comune } from '../../components/address';
import { MemberPicker } from './MemberPicker';
import { parseFiscalCode, resolveBirthPlace } from '../../utils/codiceFiscale';
import statiEsteri from '../../data/statiEsteri.json';
import type { MemberRef } from '../../types';

export type MemberPersonalFieldsValue = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  birthDate: string;
  birthPlace: string;
  fiscalCode: string;
  gender: string;
  address: string;
  city: string;
  postalCode: string;
  province: string;
  region: string;
  country: string;
  beltGradeId: string;
  registrationDate: string;
  notes: string;
  referredBy: MemberRef | null;
};

/** Fields the fiscal-code decoder can fill in. Tracked so a later CF edit can safely
 *  re-derive them, but a manual edit on one of them "wins" and is never overwritten. */
type DerivableField = 'birthDate' | 'gender' | 'birthPlace';

type Props = {
  value: MemberPersonalFieldsValue;
  onChange: (patch: Partial<MemberPersonalFieldsValue>) => void;
  mode: 'create' | 'edit';
  /** Excludes this member from the "Portato da" picker results (can't have brought themselves). */
  memberId?: number;
};

const inputClass =
  'w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors';
const readonlyClass =
  'w-full bg-zinc-900/50 border border-white/5 rounded-xl px-4 py-3 text-zinc-400 text-sm';
const labelClass = 'block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5';

const STATI_ESTERI: Record<string, string> = statiEsteri;

/**
 * Shared Dati Personali / Indirizzo / Iscrizione / Altro form, used by both
 * `MemberFormPage` ("Nuovo socio" / edit) and the enrollment wizard's `MemberStep`,
 * which used to be two hand-duplicated copies (see DESIGN-inserimento-rapido-soci.it.md).
 *
 * Fully controlled (`value`/`onChange`, like `AddressFields`) — the caller owns the
 * form state and the submit flow; this component owns only the fiscal-code decoding
 * and the "Altro" collapsed/open UI state.
 */
export function MemberPersonalFields({ value, onChange, mode, memberId }: Props) {
  const { data: beltGrades } = useBeltGrades();
  const [comuni, setComuni] = useState<Comune[] | null>(null);
  const derivedRef = useRef<Set<DerivableField>>(new Set());

  const fiscalCodeId = useId();
  const firstNameId = useId();
  const lastNameId = useId();
  const emailId = useId();
  const phoneId = useId();
  const birthDateId = useId();
  const birthPlaceId = useId();
  const beltGradeId = useId();
  const regionId = useId();
  const countryId = useId();
  const notesId = useId();
  const registrationDateId = useId();
  const altroPanelId = useId();

  // "Altro" starts closed on a brand-new member; when editing, it opens itself if any
  // of its fields already has something in it, so existing data isn't hidden away.
  // Computed once at mount on purpose — toggling stays entirely user-driven afterwards.
  const [altroOpen, setAltroOpen] = useState(
    () =>
      mode === 'edit' &&
      Boolean(
        value.beltGradeId ||
          value.region ||
          (value.country && value.country !== 'Italia') ||
          value.referredBy ||
          value.notes,
      ),
  );

  useEffect(() => {
    let active = true;
    loadComuni()
      .then((c) => { if (active) setComuni(c); })
      .catch(() => {});
    return () => { active = false; };
  }, []);

  const cfInfo = useMemo(() => parseFiscalCode(value.fiscalCode), [value.fiscalCode]);

  // Re-derive birth date / gender / birthplace whenever the fiscal code changes, or once
  // the comuni list finishes loading (it may arrive after the user already typed a CF).
  // Only overwrites a field when it's empty or still holds a previously-derived value —
  // never something the user typed by hand. Deliberately reads `value`/`onChange` from
  // the current render's closure rather than watching them: typing elsewhere in the form
  // must not re-trigger this.
  useEffect(() => {
    if (!cfInfo) return;
    const patch: Partial<MemberPersonalFieldsValue> = {};
    if (cfInfo.birthDate && (value.birthDate === '' || derivedRef.current.has('birthDate'))) {
      patch.birthDate = cfInfo.birthDate;
    }
    if (cfInfo.gender && (value.gender === '' || derivedRef.current.has('gender'))) {
      patch.gender = cfInfo.gender;
    }
    const place = resolveBirthPlace(cfInfo, comuni, STATI_ESTERI);
    if (place && (value.birthPlace === '' || derivedRef.current.has('birthPlace'))) {
      patch.birthPlace = place;
    }
    if (Object.keys(patch).length === 0) return;
    if (patch.birthDate !== undefined) derivedRef.current.add('birthDate');
    if (patch.gender !== undefined) derivedRef.current.add('gender');
    if (patch.birthPlace !== undefined) derivedRef.current.add('birthPlace');
    onChange(patch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cfInfo, comuni]);

  /** Wraps a setter for a derivable field: a manual edit stops future CF edits from overwriting it. */
  const manualChange = (field: DerivableField) => (v: string) => {
    derivedRef.current.delete(field);
    onChange({ [field]: v } as Partial<MemberPersonalFieldsValue>);
  };

  const derivedHint = Boolean(cfInfo && (cfInfo.birthDate || cfInfo.gender));

  return (
    <>
      <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">Dati Personali</p>

        <FormField label="Codice Fiscale" htmlFor={fiscalCodeId}>
          <TextInput
            id={fiscalCodeId}
            value={value.fiscalCode}
            onChange={(e) => onChange({ fiscalCode: e.target.value.toUpperCase() })}
            maxLength={16}
            autoComplete="off"
            placeholder="RSSMRA80A01H501U"
            className="uppercase tracking-wider"
          />
          {cfInfo && !cfInfo.checksumValid && (
            <p role="status" className="text-amber-400 text-xs mt-1.5">
              Il codice fiscale non sembra corretto: controllalo.
            </p>
          )}
          {derivedHint && (
            <p className="text-zinc-500 text-xs mt-1.5">Dati ricavati dal codice fiscale.</p>
          )}
        </FormField>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Nome *" htmlFor={firstNameId}>
            <TextInput
              id={firstNameId}
              value={value.firstName}
              onChange={(e) => onChange({ firstName: e.target.value })}
              required
              autoComplete="given-name"
            />
          </FormField>
          <FormField label="Cognome *" htmlFor={lastNameId}>
            <TextInput
              id={lastNameId}
              value={value.lastName}
              onChange={(e) => onChange({ lastName: e.target.value })}
              required
              autoComplete="family-name"
            />
          </FormField>
        </div>

        <FormField label="Email *" htmlFor={emailId}>
          <TextInput
            id={emailId}
            type="email"
            value={value.email}
            onChange={(e) => onChange({ email: e.target.value })}
            required
            autoComplete="email"
            inputMode="email"
          />
        </FormField>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Telefono" htmlFor={phoneId}>
            <TextInput
              id={phoneId}
              value={value.phone}
              onChange={(e) => onChange({ phone: e.target.value })}
              maxLength={20}
              inputMode="tel"
              autoComplete="tel"
            />
          </FormField>
          <FormField label="Sesso">
            <div role="radiogroup" aria-label="Sesso" className="flex gap-2">
              {(['M', 'F'] as const).map((g) => {
                const active = value.gender === g;
                return (
                  <button
                    key={g}
                    type="button"
                    role="radio"
                    aria-checked={active}
                    onClick={() => manualChange('gender')(active ? '' : g)}
                    className={`flex-1 rounded-xl py-3 text-sm font-semibold border transition-colors ${
                      active
                        ? 'bg-lime-400 text-[#0b0b0d] border-lime-400'
                        : 'bg-zinc-900 text-zinc-300 border-white/5 hover:border-white/20'
                    }`}
                  >
                    {g === 'M' ? 'Maschio' : 'Femmina'}
                  </button>
                );
              })}
            </div>
          </FormField>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Data di nascita" htmlFor={birthDateId}>
            <TextInput
              id={birthDateId}
              type="date"
              value={value.birthDate}
              onChange={(e) => manualChange('birthDate')(e.target.value)}
              autoComplete="bday"
            />
          </FormField>
          <FormField label="Luogo di nascita" htmlFor={birthPlaceId}>
            <BirthPlaceInput
              id={birthPlaceId}
              value={value.birthPlace}
              comuni={comuni}
              onChange={manualChange('birthPlace')}
            />
          </FormField>
        </div>
      </div>

      <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">Indirizzo</p>
        <AddressFields value={value} onChange={onChange} showCountry={false} showRegion={false} />
      </div>

      <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">Iscrizione</p>
        <FormField label="Data iscrizione" htmlFor={registrationDateId}>
          <TextInput
            id={registrationDateId}
            type="date"
            value={value.registrationDate}
            onChange={(e) => onChange({ registrationDate: e.target.value })}
          />
        </FormField>
      </div>

      <div className="bg-zinc-800 rounded-2xl overflow-hidden">
        <button
          type="button"
          onClick={() => setAltroOpen((open) => !open)}
          aria-expanded={altroOpen}
          aria-controls={altroPanelId}
          className="w-full flex items-center justify-between gap-2 p-5 text-left"
        >
          <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">Altro</p>
          <ChevronDown
            className={`w-4 h-4 text-zinc-500 transition-transform ${altroOpen ? 'rotate-180' : ''}`}
          />
        </button>
        {altroOpen && (
          <div id={altroPanelId} className="px-5 pb-5 flex flex-col gap-4">
            <FormField label="Cintura" htmlFor={beltGradeId}>
              <Select
                id={beltGradeId}
                value={value.beltGradeId}
                onChange={(e) => onChange({ beltGradeId: e.target.value })}
              >
                <option value="">— Nessuna —</option>
                {beltGrades?.map((g) => (
                  <option key={g.id} value={g.id}>{g.label}</option>
                ))}
              </Select>
            </FormField>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className={labelClass} htmlFor={regionId}>Regione</label>
                <input id={regionId} className={readonlyClass} value={value.region} readOnly placeholder="—" />
              </div>
              <div>
                <label className={labelClass} htmlFor={countryId}>Paese</label>
                <input
                  id={countryId}
                  className={inputClass}
                  value={value.country}
                  onChange={(e) => onChange({ country: e.target.value })}
                  autoComplete="country-name"
                />
              </div>
            </div>

            <FormField label="Portato da">
              {/* Promozione passaparola: chi porta qualcuno che poi si iscrive matura uno sconto,
                  che scatta al primo abbonamento del portato. */}
              <MemberPicker
                value={value.referredBy}
                onChange={(referredBy) => onChange({ referredBy })}
                excludeMemberId={memberId}
                placeholder="Chi l'ha portato?"
              />
            </FormField>

            <FormField label="Note" htmlFor={notesId}>
              <Textarea
                id={notesId}
                value={value.notes}
                onChange={(e) => onChange({ notes: e.target.value })}
                rows={3}
              />
            </FormField>
          </div>
        )}
      </div>
    </>
  );
}

/**
 * Free-text birthplace field with comune suggestions (same filtering as the Comune
 * field in `AddressFields`), but — unlike it — never clears or validates anything: a
 * typed value that matches nothing is left as-is, since births abroad or in merged/
 * suppressed comuni won't be in the list.
 */
function BirthPlaceInput({
  id,
  value,
  comuni,
  onChange,
}: {
  id: string;
  value: string;
  comuni: Comune[] | null;
  onChange: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, []);

  const suggestions = useMemo(() => {
    if (!comuni || value.trim().length < 2) return [];
    const q = value.trim().toLowerCase();
    const starts: Comune[] = [];
    const contains: Comune[] = [];
    for (const c of comuni) {
      const name = c.nome.toLowerCase();
      if (name.startsWith(q)) starts.push(c);
      else if (name.includes(q)) contains.push(c);
      if (starts.length >= 8) break;
    }
    return [...starts, ...contains].slice(0, 8);
  }, [comuni, value]);

  return (
    <div ref={boxRef} className="relative">
      <TextInput
        id={id}
        value={value}
        onChange={(e) => { onChange(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        placeholder="Comune o stato estero"
        autoComplete="off"
      />
      {open && suggestions.length > 0 && (
        <ul className="absolute z-30 mt-1 w-full bg-zinc-900 border border-white/10 rounded-xl overflow-hidden shadow-xl max-h-64 overflow-y-auto">
          {suggestions.map((c) => (
            <li key={`${c.nome}-${c.sigla}`}>
              <button
                type="button"
                onClick={() => { onChange(c.nome); setOpen(false); }}
                className="w-full text-left px-4 py-2.5 text-sm text-zinc-200 hover:bg-lime-400/10 hover:text-lime-300 flex items-center justify-between gap-2 transition-colors"
              >
                <span>
                  {c.nome} <span className="text-zinc-500">({c.sigla})</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
