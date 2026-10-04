import { useState } from 'react';
import { Save } from 'lucide-react';
import { useCreateSubscription, useSubscriptionsByMember } from '../../subscriptions/useSubscriptions';
import { useMemberRewards } from '../../referrals';
import { useSubscriptionTypes } from '../../lookups/useLookups';
import { usePackages } from '../../packages/usePackages';
import { getApiErrorMessage } from '../../../utils/apiError';
import { addMonthsClamped } from '../../../utils/subscriptionUtils';
import { formatDayMonthYear, todayIsoLocal } from '../../../utils/dateTime';
import { formatCurrency } from '../../../utils/currency';
import { lookupLabel } from '../../../utils/lookupLabels';
import { ErrorMessage } from '../../../components/ui/ErrorMessage';
import { FormField } from '../../../components/ui/FormField';
import { TextInput } from '../../../components/ui/TextInput';
import { Select } from '../../../components/ui/Select';
import { Textarea } from '../../../components/ui/Textarea';
import { MoneyInput } from '../../../components/ui/MoneyInput';
import { Button } from '../../../components/ui/Button';
import { StickyActions } from '../../../components/ui/StickyActions';
import type { MemberResponse, SubscriptionRequest, SubscriptionResponse } from '../../../types';

type Mode = 'type' | 'package';

/** One day after a `YYYY-MM-DD` string, built from local date parts (no timezone parsing pitfalls). */
function addOneDay(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dt = new Date(y, m - 1, d + 1);
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
}

type SubscriptionStepProps = {
  member: MemberResponse;
  onCommit: (subscription: SubscriptionResponse) => void;
  onSkip: () => void;
};

/** Step 2 — type/package toggle unchanged, plus listino price prefill, end-date preview and smart start date. */
export function SubscriptionStep({ member, onCommit, onSkip }: SubscriptionStepProps) {
  const { data: types } = useSubscriptionTypes();
  const { data: packages } = usePackages();
  const { data: existingSubs } = useSubscriptionsByMember(member.id);
  const createSubscription = useCreateSubscription(member.id);

  const [mode, setMode] = useState<Mode>('type');
  const [typeId, setTypeId] = useState('');
  const [packageId, setPackageId] = useState('');
  const [startDate, setStartDate] = useState(todayIsoLocal());
  const [startDateTouched, setStartDateTouched] = useState(false);
  const [price, setPrice] = useState('');
  const [priceDirty, setPriceDirty] = useState(false);
  const [notes, setNotes] = useState('');
  const [apiError, setApiError] = useState<string | null>(null);
  const [rewardId, setRewardId] = useState<number | null>(null);

  // Crediti passaparola ancora spendibili di questo socio (li ha maturati portando qualcuno
  // che poi si e' iscritto). Non cumulabili: se ne applica al massimo uno per abbonamento.
  const { data: rewards } = useMemberRewards(member.id, 'EARNED');
  const selectedReward = rewards?.find((r) => r.id === rewardId) ?? null;

  // Smart start date: if the member already has an active subscription ending in the future,
  // default to the day after it ends instead of today, to avoid overlapping periods. Applied
  // once, as soon as the member's subscriptions have loaded — adjusting state during render
  // (same pattern as AddressFields' city sync) rather than in an effect. Skipped if the user
  // already typed a start date by hand before the query resolved.
  const [smartDefaultApplied, setSmartDefaultApplied] = useState(false);
  if (!smartDefaultApplied && existingSubs !== undefined) {
    setSmartDefaultApplied(true);
    if (!startDateTouched) {
      const today = todayIsoLocal();
      const activeFuture = existingSubs.find((s) => s.status.code === 'ACTIVE' && s.endDate > today);
      if (activeFuture) setStartDate(addOneDay(activeFuture.endDate));
    }
  }

  const selectedType = types?.find((t) => t.id === Number(typeId));
  const selectedPackage = packages?.find((p) => p.id === Number(packageId));

  const handleTypeChange = (id: string) => {
    setTypeId(id);
    const type = types?.find((t) => t.id === Number(id));
    // Never overwrite a price the user has already typed by hand (§6 "prezzo listino modificato").
    if (type && !priceDirty) {
      setPrice(type.defaultPrice !== null ? String(type.defaultPrice) : '');
    }
  };

  const endDatePreview =
    mode === 'type' && selectedType?.months
      ? addMonthsClamped(startDate, selectedType.months)
      : mode === 'package' && selectedPackage
        ? addMonthsClamped(startDate, selectedPackage.durationMonths)
        : null;

  // Anteprima locale dello sconto: il prezzo autorevole resta quello che calcola il backend
  // applicando il credito, questa serve solo a far vedere all'operatore cosa sta per incassare.
  const fullPrice = mode === 'type' ? parseFloat(price) : (selectedPackage?.price ?? NaN);
  const discountedPrice =
    selectedReward && Number.isFinite(fullPrice)
      ? Math.round(fullPrice * (100 - selectedReward.percent)) / 100
      : null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const data: SubscriptionRequest =
      mode === 'type'
        ? { typeId: Number(typeId), startDate, price: parseFloat(price), notes: notes || null, rewardId }
        : { packageId: Number(packageId), startDate, notes: notes || null, rewardId };

    try {
      const subscription = await createSubscription.mutateAsync(data);
      onCommit(subscription);
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="flex flex-col gap-5">
      {apiError && <ErrorMessage message={apiError} />}

      <div className="flex gap-2" role="radiogroup" aria-label="Tipo di abbonamento">
        {([
          { key: 'type', label: 'Tipo generico' },
          { key: 'package', label: 'Pacchetto' },
        ] as const).map(({ key, label }) => (
          <button
            key={key}
            type="button"
            role="radio"
            aria-checked={mode === key}
            onClick={() => setMode(key)}
            className={`flex-1 px-4 py-2.5 rounded-xl text-sm font-semibold uppercase tracking-wide transition-colors ${
              mode === key ? 'bg-lime-400 text-[#0b0b0d]' : 'bg-zinc-800 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {rewards && rewards.length > 0 && (
          <div className="bg-lime-400/8 border border-lime-400/25 rounded-2xl p-4 flex flex-col gap-2">
            <p className="text-lime-400 text-xs font-semibold uppercase tracking-widest">
              Sconto passaparola
            </p>
            {rewards.map((r) => (
              <label key={r.id} className="flex items-start gap-3 cursor-pointer">
                <input
                  type="checkbox"
                  checked={rewardId === r.id}
                  // Uno solo per abbonamento: spuntarne un altro sostituisce il precedente.
                  onChange={(e) => setRewardId(e.target.checked ? r.id : null)}
                  className="mt-0.5 w-4 h-4 rounded border-white/20 bg-zinc-900 text-lime-400 focus:ring-lime-400/30 focus:ring-offset-0"
                />
                <span className="text-zinc-200 text-sm">
                  &minus;{r.percent}% per aver portato {r.referredMember.name}
                </span>
              </label>
            ))}
            {selectedReward && discountedPrice !== null && (
              <p className="text-zinc-400 text-xs">
                Prezzo: <span className="line-through">{formatCurrency(fullPrice)}</span>{' '}
                <span className="text-lime-400 font-semibold">{formatCurrency(discountedPrice)}</span>
              </p>
            )}
          </div>
        )}

        <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
          {mode === 'type' ? (
            <>
              <FormField label="Tipo abbonamento *">
                <Select value={typeId} onChange={(e) => handleTypeChange(e.target.value)} required>
                  <option value="">— Seleziona —</option>
                  {types?.map((t) => (
                    <option key={t.id} value={t.id}>
                      {lookupLabel(t)}{t.months ? ` (${t.months} mes${t.months === 1 ? 'e' : 'i'})` : ''}
                    </option>
                  ))}
                </Select>
              </FormField>

              <FormField label="Prezzo (€) *">
                <MoneyInput
                  value={price}
                  onChange={(e) => { setPrice(e.target.value); setPriceDirty(true); }}
                  required
                />
              </FormField>
            </>
          ) : (
            <>
              <FormField label="Pacchetto *">
                <Select value={packageId} onChange={(e) => setPackageId(e.target.value)} required>
                  <option value="">— Seleziona —</option>
                  {packages?.filter((p) => p.active).map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </Select>
              </FormField>

              {selectedPackage && (
                <div className="bg-zinc-900 border border-white/5 rounded-xl p-4 flex flex-col gap-2">
                  <p className="text-zinc-500 text-xs font-semibold uppercase tracking-wide">Riepilogo</p>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-zinc-400">Durata</span>
                    <span className="text-zinc-100">
                      {selectedPackage.durationMonths} mes{selectedPackage.durationMonths === 1 ? 'e' : 'i'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-zinc-400">Prezzo</span>
                    <span className="text-cyan-400 font-semibold">{formatCurrency(selectedPackage.price)}</span>
                  </div>
                  <div>
                    <span className="text-zinc-400 text-sm">Corsi inclusi</span>
                    <ul className="mt-1 list-disc list-inside text-zinc-300 text-sm">
                      {selectedPackage.courses.map((c) => (
                        <li key={c.id}>{c.name}</li>
                      ))}
                    </ul>
                  </div>
                </div>
              )}
            </>
          )}

          <FormField label="Data inizio *">
            <TextInput
              type="date"
              value={startDate}
              onChange={(e) => { setStartDate(e.target.value); setStartDateTouched(true); }}
              required
            />
          </FormField>

          {endDatePreview && (
            <div className="flex items-center justify-between bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-sm">
              <span className="text-zinc-400">Scadenza prevista</span>
              <span className="text-zinc-100 font-semibold">{formatDayMonthYear(endDatePreview)}</span>
            </div>
          )}

          <FormField label="Note">
            <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} />
          </FormField>
        </div>

        <StickyActions className="flex gap-3">
          <Button type="submit" loading={createSubscription.isPending} className="flex-1 py-4">
            <Save className="w-5 h-5" />
            {createSubscription.isPending ? 'Salvataggio…' : 'Crea Abbonamento'}
          </Button>
          <Button type="button" variant="secondary" onClick={onSkip} disabled={createSubscription.isPending} className="py-4">
            Salta
          </Button>
        </StickyActions>
      </form>
    </div>
  );
}
