import { useState } from 'react';
import { Check, Pencil, Plus, X } from 'lucide-react';
import {
  useFederations,
  useMemberFees,
  useChargeFee,
  useMemberFederationCards,
  useSaveFederationCard,
} from './useFees';
import { usePaymentMethods } from '../lookups/useLookups';
import { getApiErrorMessage } from '../../utils/apiError';
import { formatDayMonthYear } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/currency';
import { lookupLabel } from '../../utils/lookupLabels';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { Select } from '../../components/ui/Select';
import { TextInput } from '../../components/ui/TextInput';
import { Button } from '../../components/ui/Button';
import { IconButton } from '../../components/ui/IconButton';
import type { MemberFeeRequest, MemberFeeResponse } from '../../types';

/**
 * Quote del socio: associativa e tesseramenti federali, con lo stato e il pulsante per
 * incassarne una nuova.
 *
 * Mostra sempre e solo la quota **più recente per ciascun titolo** (l'associativa, e una per
 * federazione): quello che l'operatore deve sapere è "è in regola oggi?", e uno storico di
 * rinnovi lungo anni seppellirebbe la risposta.
 */
export function MemberFeesSection({ memberId }: { memberId: number }) {
  const { data: fees } = useMemberFees(memberId);
  const [charging, setCharging] = useState(false);

  if (!fees) return null;

  // Le quote arrivano ordinate per scadenza decrescente, quindi la prima di ogni gruppo e'
  // gia' quella che conta.
  const current = new Map<string, MemberFeeResponse>();
  for (const fee of fees) {
    const key = fee.type === 'ASSOCIATION' ? 'ASSOCIATION' : `FED-${fee.federation?.id}`;
    if (!current.has(key)) current.set(key, fee);
  }
  const rows = [...current.values()];

  return (
    <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-5 mb-4">
      <div className="flex items-center justify-between gap-3 mb-3">
        <p className="text-[#8b8b94] text-[13px] font-semibold">Quote e tesseramenti</p>
        {!charging && (
          <button
            type="button"
            onClick={() => setCharging(true)}
            className="flex items-center gap-1.5 min-h-11 -my-2.5 px-2 text-lime-400 hover:text-lime-300 text-xs font-semibold transition-colors"
          >
            <Plus className="w-3.5 h-3.5" /> Incassa quota
          </button>
        )}
      </div>

      {charging && <ChargeFeeForm memberId={memberId} onClose={() => setCharging(false)} />}

      {rows.length === 0 && !charging && (
        <p className="text-zinc-500 text-[13.5px]">Nessuna quota registrata.</p>
      )}

      <div className="flex flex-col gap-2">
        {rows.map((fee) => (
          <div key={fee.id} className="flex items-baseline justify-between gap-3">
            <span className="text-zinc-300 text-[14px] min-w-0">
              {fee.type === 'ASSOCIATION' ? 'Quota associativa' : fee.federation?.name}
              {fee.cardType && <span className="text-zinc-500"> · {fee.cardType.name}</span>}
            </span>
            <span
              className={`text-xs font-semibold shrink-0 ${fee.expired ? 'text-amber-400' : 'text-lime-400'}`}
            >
              {fee.expired ? 'scaduta il ' : 'fino al '}
              {formatDayMonthYear(fee.validUntil)}
            </span>
          </div>
        ))}
      </div>

      <FederationCards memberId={memberId} />
    </div>
  );
}

/**
 * Numero di tessera per ogni federazione attiva della palestra. Sta qui e non sulla quota
 * perche' non cambia ai rinnovi: si scrive una volta e resta.
 */
function FederationCards({ memberId }: { memberId: number }) {
  const { data: federations } = useFederations();
  const { data: cards } = useMemberFederationCards(memberId);
  const [editing, setEditing] = useState<number | null>(null);

  const active = (federations ?? []).filter((f) => f.active);
  if (!cards || active.length === 0) return null;

  return (
    <div className="mt-4 pt-4 border-t border-white/6 flex flex-col gap-2">
      <p className="text-[#8b8b94] text-[13px] font-semibold">N. tessera</p>
      {active.map((f) => {
        const number = cards.find((c) => c.federation.id === f.id)?.cardNumber ?? '';
        return editing === f.id ? (
          <CardNumberForm
            key={f.id}
            memberId={memberId}
            federationId={f.id}
            federationName={f.name}
            initial={number}
            onClose={() => setEditing(null)}
          />
        ) : (
          <div key={f.id} className="flex items-center justify-between gap-3">
            <span className="text-zinc-300 text-[14px] min-w-0">{f.name}</span>
            <button
              type="button"
              onClick={() => setEditing(f.id)}
              className="flex items-center gap-1.5 min-h-11 -my-2.5 px-2 text-[14px] transition-colors text-zinc-100 hover:text-lime-300"
              aria-label={`Modifica numero tessera ${f.name}`}
            >
              {number ? (
                <span className="font-mono">{number}</span>
              ) : (
                <span className="text-zinc-500">Aggiungi</span>
              )}
              <Pencil className="w-3.5 h-3.5 text-zinc-500" />
            </button>
          </div>
        );
      })}
    </div>
  );
}

function CardNumberForm({
  memberId,
  federationId,
  federationName,
  initial,
  onClose,
}: {
  memberId: number;
  federationId: number;
  federationName: string;
  initial: string;
  onClose: () => void;
}) {
  const saveCard = useSaveFederationCard(memberId);
  const [value, setValue] = useState(initial);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setError(null);
    try {
      await saveCard.mutateAsync({ federationId, cardNumber: value.trim() });
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <form
      className="flex flex-col gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      {error && <ErrorMessage message={error} />}
      <div className="flex items-center gap-2">
        <TextInput
          autoFocus
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder={`N. tessera ${federationName}`}
          aria-label={`Numero tessera ${federationName}`}
          maxLength={50}
          inputMode="numeric"
        />
        <IconButton type="submit" aria-label="Salva" disabled={saveCard.isPending}>
          <Check className="w-4 h-4" />
        </IconButton>
        <IconButton type="button" onClick={onClose} aria-label="Annulla">
          <X className="w-4 h-4" />
        </IconButton>
      </div>
    </form>
  );
}

function ChargeFeeForm({ memberId, onClose }: { memberId: number; onClose: () => void }) {
  const chargeFee = useChargeFee(memberId);
  const { data: federations } = useFederations();
  const { data: paymentMethods } = usePaymentMethods();

  const [cardTypeId, setCardTypeId] = useState('');
  const [paymentMethodId, setPaymentMethodId] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Un unico elenco: la quota associativa piu' ogni tessera di ogni federazione, cosi'
  // l'operatore fa una scelta sola invece di due a cascata.
  const options = (federations ?? []).flatMap((f) =>
    f.cardTypes
      .filter((c) => c.active)
      .map((c) => ({ id: c.id, label: `${f.name} · ${c.name} — ${formatCurrency(c.price)}` })),
  );

  const submit = async () => {
    setError(null);
    const data: MemberFeeRequest =
      cardTypeId === 'ASSOCIATION'
        ? { type: 'ASSOCIATION', paymentMethodId: paymentMethodId ? Number(paymentMethodId) : null }
        : {
            type: 'FEDERATION',
            cardTypeId: Number(cardTypeId),
            paymentMethodId: paymentMethodId ? Number(paymentMethodId) : null,
          };
    try {
      await chargeFee.mutateAsync(data);
      onClose();
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="bg-zinc-900 border border-white/5 rounded-xl p-4 mb-4 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <p className="text-zinc-400 text-xs font-semibold uppercase tracking-wide">Incassa quota</p>
        <IconButton onClick={onClose} aria-label="Chiudi" className="-mr-2 -mt-1">
          <X className="w-4 h-4" />
        </IconButton>
      </div>

      {error && <ErrorMessage message={error} />}

      <FormField label="Quota *">
        <Select value={cardTypeId} onChange={(e) => setCardTypeId(e.target.value)} required>
          <option value="">— Seleziona —</option>
          <option value="ASSOCIATION">Quota associativa</option>
          {options.map((o) => (
            <option key={o.id} value={o.id}>{o.label}</option>
          ))}
        </Select>
      </FormField>

      <FormField label="Metodo di pagamento">
        <Select value={paymentMethodId} onChange={(e) => setPaymentMethodId(e.target.value)}>
          <option value="">— Non specificato —</option>
          {paymentMethods?.map((m) => (
            <option key={m.id} value={m.id}>{lookupLabel(m)}</option>
          ))}
        </Select>
      </FormField>

      <Button onClick={submit} disabled={!cardTypeId} loading={chargeFee.isPending}>
        {chargeFee.isPending ? 'Registro…' : 'Registra incasso'}
      </Button>
    </div>
  );
}
