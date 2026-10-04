import { useState } from 'react';
import { Plus, Save, Trash2, X } from 'lucide-react';
import {
  useFederations,
  useAddCardType,
  useUpdateCardType,
  useDeleteCardType,
  useCreateFederation,
} from './useFees';
import { useMyGym, useUpdateMyGym } from '../gyms/useGyms';
import { usePermissions } from '../../hooks/usePermissions';
import { getApiErrorMessage } from '../../utils/apiError';
import { formatDayMonthYear } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/currency';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import { MoneyInput } from '../../components/ui/MoneyInput';
import { IconButton } from '../../components/ui/IconButton';
import type { FederationResponse } from '../../types';

const MONTHS = [
  'gennaio', 'febbraio', 'marzo', 'aprile', 'maggio', 'giugno',
  'luglio', 'agosto', 'settembre', 'ottobre', 'novembre', 'dicembre',
];

/**
 * Quote: la quota associativa della palestra e i listini di tesseramento delle federazioni.
 *
 * Solo il gestore modifica gli importi (il backend rifiuta comunque le scritture dello staff);
 * allo staff la pagina resta leggibile, perché è lui che incassa e deve sapere quanto chiedere.
 */
export function FederationsPage() {
  const { canManageLookups, isInstructor } = usePermissions();
  const canEditPrices = isInstructor || canManageLookups;

  const { data: federations, isLoading, isError } = useFederations();
  const { data: gym } = useMyGym();
  const updateGym = useUpdateMyGym();
  const createFederation = useCreateFederation();

  const [fee, setFee] = useState<string | null>(null);
  const [feeError, setFeeError] = useState<string | null>(null);
  const [feeSaved, setFeeSaved] = useState(false);
  const [addingFederation, setAddingFederation] = useState(false);

  // Il campo si semina dal valore salvato solo finché l'utente non lo tocca, così un refetch
  // non gli riscrive sotto le dita quello che sta digitando.
  const feeValue = fee ?? (gym ? String(gym.membershipFeeAmount ?? '') : '');

  const saveFee = async () => {
    if (!gym) return;
    setFeeError(null);
    setFeeSaved(false);
    try {
      // PUT /gyms/me sostituisce l'intera anagrafica, quindi i campi vanno rimandati tutti:
      // spedire solo l'importo azzererebbe indirizzo, telefono e P.IVA. La response ha campi
      // nullable e la request no, da cui il ?? undefined.
      await updateGym.mutateAsync({
        name: gym.name,
        address: gym.address ?? undefined,
        city: gym.city ?? undefined,
        postalCode: gym.postalCode ?? undefined,
        province: gym.province ?? undefined,
        region: gym.region ?? undefined,
        phone: gym.phone ?? undefined,
        vatNumber: gym.vatNumber ?? undefined,
        contactEmail: gym.contactEmail ?? undefined,
        membershipFeeAmount: Number(feeValue),
      });
      setFeeSaved(true);
    } catch (err) {
      setFeeError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        Quote
      </h1>

      {/* ── Quota associativa ── */}
      <div className="bg-zinc-800 rounded-2xl p-5 mb-6">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest mb-3">
          Quota associativa
        </p>
        <p className="text-zinc-400 text-[13px] mb-4">
          Vale 12 mesi dal giorno in cui viene pagata, quindi ogni socio ha la sua scadenza.
        </p>
        {feeError && <div className="mb-3"><ErrorMessage message={feeError} /></div>}
        <div className="flex items-end gap-3">
          <div className="flex-1">
            <FormField label="Importo">
              <MoneyInput
                value={feeValue}
                onChange={(e) => { setFee(e.target.value); setFeeSaved(false); }}
                disabled={!canEditPrices}
              />
            </FormField>
          </div>
          {canEditPrices && (
            <button
              type="button"
              onClick={saveFee}
              disabled={updateGym.isPending || !feeValue}
              className="flex items-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-4 py-3 rounded-xl text-sm hover:bg-lime-300 disabled:opacity-50 transition-colors"
            >
              <Save className="w-4 h-4" />
              {updateGym.isPending ? 'Salvo…' : 'Salva'}
            </button>
          )}
        </div>
        {feeSaved && <p className="text-lime-400 text-xs mt-2">Quota aggiornata.</p>}
      </div>

      {/* ── Federazioni ── */}
      <div className="flex items-center justify-between mb-3 gap-3">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">
          Tesseramenti federali
        </p>
        {canEditPrices && !addingFederation && (
          <button
            type="button"
            onClick={() => setAddingFederation(true)}
            className="flex items-center gap-1.5 min-h-11 px-1 text-lime-400 hover:text-lime-300 text-xs font-semibold transition-colors"
          >
            <Plus className="w-3.5 h-3.5" /> Aggiungi federazione
          </button>
        )}
      </div>

      {addingFederation && (
        <NewFederationForm
          onClose={() => setAddingFederation(false)}
          onSubmit={(data) => createFederation.mutateAsync(data)}
          pending={createFederation.isPending}
        />
      )}

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento delle federazioni." />}

      <div className="flex flex-col gap-4">
        {federations?.map((f) => (
          <FederationCard key={f.id} federation={f} canEdit={canEditPrices} />
        ))}
      </div>
    </div>
  );
}

function NewFederationForm({
  onClose,
  onSubmit,
  pending,
}: {
  onClose: () => void;
  onSubmit: (data: { name: string; seasonStartMonth: number }) => Promise<unknown>;
  pending: boolean;
}) {
  const [name, setName] = useState('');
  const [month, setMonth] = useState('9');
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await onSubmit({ name: name.trim(), seasonStartMonth: Number(month) });
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="bg-zinc-800 rounded-2xl p-5 mb-4 flex flex-col gap-4">
      {error && <ErrorMessage message={error} />}
      <FormField label="Nome federazione">
        <TextInput value={name} onChange={(e) => setName(e.target.value)} autoFocus />
      </FormField>
      <FormField label="La stagione inizia il 1°">
        <Select value={month} onChange={(e) => setMonth(e.target.value)}>
          {MONTHS.map((m, i) => (
            <option key={i} value={i + 1}>{m}</option>
          ))}
        </Select>
      </FormField>
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={submit}
          disabled={pending || !name.trim()}
          className="flex-1 bg-lime-400 text-[#0b0b0d] font-bold py-2.5 rounded-lg text-xs hover:bg-lime-300 disabled:opacity-50 transition-colors"
        >
          {pending ? 'Salvo…' : 'Aggiungi'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="text-zinc-500 hover:text-zinc-300 text-xs px-3 py-2.5 rounded-lg border border-zinc-700 transition-colors"
        >
          Annulla
        </button>
      </div>
    </div>
  );
}

function FederationCard({ federation, canEdit }: { federation: FederationResponse; canEdit: boolean }) {
  const addCardType = useAddCardType();
  const updateCardType = useUpdateCardType();
  const deleteCardType = useDeleteCardType();

  const [adding, setAdding] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPrice, setNewPrice] = useState('');
  const [error, setError] = useState<string | null>(null);

  const add = async () => {
    setError(null);
    try {
      await addCardType.mutateAsync({
        federationId: federation.id,
        data: { name: newName.trim(), price: Number(newPrice) },
      });
      setNewName('');
      setNewPrice('');
      setAdding(false);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const changePrice = async (cardTypeId: number, name: string, price: string) => {
    setError(null);
    try {
      await updateCardType.mutateAsync({ cardTypeId, data: { name, price: Number(price) } });
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const remove = async (cardTypeId: number) => {
    setError(null);
    try {
      await deleteCardType.mutateAsync(cardTypeId);
    } catch (err) {
      // Il backend rifiuta con 409 una riga gia' incassata: e' storia, non si riscrive.
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="bg-zinc-800 rounded-2xl p-5">
      <div className="flex items-baseline justify-between gap-3 mb-1">
        <p className="text-zinc-100 font-semibold">{federation.name}</p>
        {!federation.active && <span className="text-zinc-500 text-xs">non attiva</span>}
      </div>
      <p className="text-zinc-500 text-xs mb-4">
        Stagione in corso: {formatDayMonthYear(federation.currentSeasonStart)} –{' '}
        {formatDayMonthYear(federation.currentSeasonEnd)}
      </p>

      {error && <div className="mb-3"><ErrorMessage message={error} /></div>}

      <div className="flex flex-col gap-2">
        {federation.cardTypes.map((c) => (
          <CardTypeRow
            key={c.id}
            name={c.name}
            price={c.price}
            canEdit={canEdit}
            onSave={(price) => changePrice(c.id, c.name, price)}
            onDelete={() => remove(c.id)}
          />
        ))}
      </div>

      {canEdit && (adding ? (
        <div className="flex flex-col sm:flex-row gap-2 mt-3">
          <TextInput
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Nome tessera"
            autoFocus
          />
          <div className="sm:w-32">
            <MoneyInput value={newPrice} onChange={(e) => setNewPrice(e.target.value)} />
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={add}
              disabled={addCardType.isPending || !newName.trim() || !newPrice}
              className="bg-lime-400 text-[#0b0b0d] font-bold px-3 py-2 rounded-lg text-xs hover:bg-lime-300 disabled:opacity-50 transition-colors"
            >
              Aggiungi
            </button>
            <button
              type="button"
              onClick={() => setAdding(false)}
              className="text-zinc-500 hover:text-zinc-300 p-1"
              aria-label="Annulla"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-3 flex items-center gap-1.5 min-h-11 px-1 text-lime-400 hover:text-lime-300 text-xs font-semibold transition-colors"
        >
          <Plus className="w-3.5 h-3.5" /> Aggiungi tessera
        </button>
      ))}
    </div>
  );
}

function CardTypeRow({
  name,
  price,
  canEdit,
  onSave,
  onDelete,
}: {
  name: string;
  price: number;
  canEdit: boolean;
  onSave: (price: string) => Promise<void>;
  onDelete: () => Promise<void>;
}) {
  const [value, setValue] = useState(String(price));
  const dirty = Number(value) !== price;

  return (
    <div className="flex items-center gap-2">
      <span className="flex-1 min-w-0 text-zinc-300 text-sm truncate">{name}</span>
      {canEdit ? (
        <>
          <div className="w-24 shrink-0">
            <MoneyInput value={value} onChange={(e) => setValue(e.target.value)} />
          </div>
          <IconButton
            onClick={() => onSave(value)}
            disabled={!dirty}
            className="text-lime-400 hover:text-lime-300 disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label={`Salva prezzo ${name}`}
          >
            <Save className="w-4 h-4" />
          </IconButton>
          <IconButton
            onClick={onDelete}
            className="text-zinc-600 hover:text-red-400"
            aria-label={`Elimina ${name}`}
          >
            <Trash2 className="w-4 h-4" />
          </IconButton>
        </>
      ) : (
        <span className="text-zinc-400 text-sm shrink-0">{formatCurrency(price)}</span>
      )}
    </div>
  );
}
