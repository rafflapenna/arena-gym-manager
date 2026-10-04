import { useState } from 'react';
import { Tag, Pencil, Plus, X } from 'lucide-react';
import { useSubscriptionTypes, useCreateSubscriptionType, useUpdateSubscriptionType } from './useLookups';
import { getApiErrorMessage } from '../../utils/apiError';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { MoneyInput } from '../../components/ui/MoneyInput';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Button } from '../../components/ui/Button';
import { IconButton } from '../../components/ui/IconButton';
import { formatCurrency } from '../../utils/currency';
import { lookupLabel } from '../../utils/lookupLabels';
import type { Lookup, SubscriptionTypeRequest } from '../../types';

type FormData = { code: string; label: string; months: string; defaultPrice: string };

const empty: FormData = { code: '', label: '', months: '', defaultPrice: '' };

/**
 * Backoffice page for the subscription-type price list: it's the source of the "defaultPrice"
 * pre-fill and end-date preview the enrollment wizard uses (see `docs/DESIGN-wizard-iscrizione.it.md`).
 */
export function SubscriptionTypesPage() {
  const { data: types, isLoading, isError } = useSubscriptionTypes();
  const createType = useCreateSubscriptionType();
  const updateType = useUpdateSubscriptionType();

  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<FormData>(empty);
  const [error, setError] = useState<string | null>(null);
  // Sotto `md` il form parte chiuso dietro un "+": tenerlo sempre aperto lasciava la lista
  // (quello che si consulta più spesso) sotto la piega. Su desktop resta sempre visibile.
  const [mobileFormOpen, setMobileFormOpen] = useState(false);

  const editing = editingId !== null;

  const startCreate = () => {
    setEditingId(null);
    setForm(empty);
    setError(null);
  };

  const startEdit = (t: Lookup) => {
    setEditingId(t.id);
    setForm({
      code: t.code,
      label: t.label,
      months: t.months !== null ? String(t.months) : '',
      defaultPrice: t.defaultPrice !== null ? String(t.defaultPrice) : '',
    });
    setError(null);
    setMobileFormOpen(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const setInput = (field: 'code' | 'label' | 'months' | 'defaultPrice') =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const data: SubscriptionTypeRequest = {
      code: form.code,
      label: form.label,
      months: Number(form.months),
      defaultPrice: form.defaultPrice ? parseFloat(form.defaultPrice) : null,
    };

    try {
      if (editing) {
        await updateType.mutateAsync({ id: editingId, data });
      } else {
        await createType.mutateAsync(data);
      }
      startCreate();
      setMobileFormOpen(false);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const isPending = createType.isPending || updateType.isPending;

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <div className="mb-6 flex items-start justify-between gap-3">
        <div>
          <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight">
            Listino abbonamenti
          </h1>
          <p className="text-zinc-500 text-sm mt-2">
            Durata e prezzo di listino per ogni tipo: il wizard di iscrizione li usa per precompilare
            prezzo e scadenza prevista.
          </p>
        </div>
        {!mobileFormOpen && !editing && (
          <IconButton
            onClick={() => setMobileFormOpen(true)}
            aria-label="Nuovo tipo abbonamento"
            className="md:hidden bg-lime-400 text-[#0b0b0d] hover:text-[#0b0b0d] hover:bg-lime-300 shrink-0"
          >
            <Plus className="w-5 h-5" />
          </IconButton>
        )}
      </div>

      <form
        onSubmit={handleSubmit}
        className={`bg-zinc-800 rounded-2xl p-5 mb-8 flex-col gap-4 ${mobileFormOpen || editing ? 'flex' : 'hidden'} md:flex`}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-zinc-300 font-semibold text-sm">
            <Tag className="w-4 h-4 text-lime-400" />
            {editing ? 'Modifica tipo abbonamento' : 'Nuovo tipo abbonamento'}
          </div>
          <button
            type="button"
            onClick={() => { startCreate(); setMobileFormOpen(false); }}
            className="flex items-center gap-1 text-zinc-500 hover:text-zinc-300 text-xs"
          >
            <X className="w-3.5 h-3.5" /> Annulla
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Codice *">
            <TextInput
              value={form.code}
              onChange={setInput('code')}
              required
              disabled={editing}
              placeholder="Es. MONTHLY"
            />
          </FormField>
          <FormField label="Nome *">
            <TextInput value={form.label} onChange={setInput('label')} required placeholder="Es. Mensile" />
          </FormField>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Durata (mesi) *">
            <TextInput type="number" inputMode="numeric" min="1" step="1" value={form.months} onChange={setInput('months')} required />
          </FormField>
          <FormField label="Prezzo di listino (€)">
            <MoneyInput value={form.defaultPrice} onChange={setInput('defaultPrice')} />
          </FormField>
        </div>

        {error && <ErrorMessage message={error} />}

        <Button type="submit" loading={isPending} className="self-start">
          {isPending ? 'Salvataggio…' : editing ? 'Salva modifiche' : 'Crea tipo abbonamento'}
        </Button>
      </form>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei tipi abbonamento." />}

      {types && (
        <div className="flex flex-col gap-3">
          {types.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun tipo abbonamento ancora.</p>
          ) : (
            types.map((t) => (
              <div key={t.id} className="flex items-center gap-4 bg-zinc-800 rounded-2xl p-4">
                <div className="flex-1 min-w-0">
                  <p className="text-zinc-100 font-semibold text-sm truncate">{lookupLabel(t)}</p>
                  <p className="text-zinc-500 text-xs truncate">
                    {t.code}
                    {t.months !== null ? ` · ${t.months} mes${t.months === 1 ? 'e' : 'i'}` : ''}
                  </p>
                </div>
                <span className="text-cyan-400 font-semibold text-sm shrink-0">
                  {t.defaultPrice !== null ? formatCurrency(t.defaultPrice) : '—'}
                </span>
                <IconButton
                  onClick={() => startEdit(t)}
                  aria-label={`Modifica ${t.label}`}
                  className="-m-2 hover:text-lime-400"
                  title="Modifica"
                >
                  <Pencil className="w-4 h-4" />
                </IconButton>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
