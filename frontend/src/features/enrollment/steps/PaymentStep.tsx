import { useRef, useState } from 'react';
import { Save } from 'lucide-react';
import { useCreatePayment } from '../../payments/usePayments';
import { usePaymentMethods } from '../../lookups/useLookups';
import { useAuthStore } from '../../../stores/authStore';
import { getApiErrorMessage } from '../../../utils/apiError';
import { ErrorMessage } from '../../../components/ui/ErrorMessage';
import { FormField } from '../../../components/ui/FormField';
import { TextInput } from '../../../components/ui/TextInput';
import { Select } from '../../../components/ui/Select';
import { MoneyInput } from '../../../components/ui/MoneyInput';
import { Button } from '../../../components/ui/Button';
import { StickyActions } from '../../../components/ui/StickyActions';
import { lookupLabel } from '../../../utils/lookupLabels';
import { todayIsoLocal } from '../../../utils/dateTime';
import type { PaymentRequest, PaymentResponse, SubscriptionResponse } from '../../../types';

const lastMethodKey = (username: string) => `gym-manager:last-payment-method:${username}`;

const CHIP_CLASS =
  'px-4 py-2 rounded-xl text-xs font-semibold uppercase tracking-wide bg-zinc-900 border border-white/5 text-zinc-300 hover:border-lime-400/40 hover:text-lime-300 transition-colors';

type PaymentStepProps = {
  subscription: SubscriptionResponse;
  onCommit: (payment: PaymentResponse) => void;
  /** Omit to hide the "Salta" action — used by the standalone "Registra pagamento" page. */
  onSkip?: () => void;
};

/**
 * Step 3 — also reused standalone as the "Registra pagamento" page for any subscription
 * (see `PaymentStepStandalonePage`), which is why the target subscription is a prop rather
 * than wizard state.
 */
export function PaymentStep({ subscription, onCommit, onSkip }: PaymentStepProps) {
  const { data: paymentMethods } = usePaymentMethods();
  const createPayment = useCreatePayment(subscription.id);
  const username = useAuthStore((s) => s.user?.username) ?? '';

  // Package subscriptions snapshot their price into `purchasePrice`; generic-type ones use `price`.
  const fullPrice = subscription.purchasePrice ?? subscription.price;

  const [amount, setAmount] = useState(fullPrice.toFixed(2));
  const [paymentDate, setPaymentDate] = useState(todayIsoLocal());
  const [paymentMethodId, setPaymentMethodId] = useState('');
  const [description, setDescription] = useState('');
  const [apiError, setApiError] = useState<string | null>(null);
  const amountInputRef = useRef<HTMLInputElement>(null);

  // Default payment method = last one used by this operator (localStorage, per-user), else the
  // first, applied once as soon as the list loads — adjusting state during render (same pattern
  // as AddressFields' city sync) rather than in an effect.
  const [defaultMethodApplied, setDefaultMethodApplied] = useState(false);
  if (!defaultMethodApplied && paymentMethods && paymentMethods.length > 0) {
    setDefaultMethodApplied(true);
    const lastId = username ? localStorage.getItem(lastMethodKey(username)) : null;
    const match = lastId && paymentMethods.some((m) => String(m.id) === lastId) ? lastId : String(paymentMethods[0].id);
    setPaymentMethodId(match);
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const data: PaymentRequest = {
      amount: parseFloat(amount),
      paymentDate,
      paymentMethodId: Number(paymentMethodId),
      description: description || null,
    };

    try {
      const payment = await createPayment.mutateAsync(data);
      if (username) localStorage.setItem(lastMethodKey(username), paymentMethodId);
      onCommit(payment);
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      {apiError && <ErrorMessage message={apiError} />}

      <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
        <FormField label="Importo (€) *">
          <MoneyInput ref={amountInputRef} value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </FormField>

        <div className="flex gap-2 flex-wrap">
          <button type="button" onClick={() => setAmount(fullPrice.toFixed(2))} className={CHIP_CLASS}>
            Importo intero
          </button>
          <button type="button" onClick={() => setAmount((fullPrice / 2).toFixed(2))} className={CHIP_CLASS}>
            Metà
          </button>
          <button
            type="button"
            onClick={() => { setAmount(''); amountInputRef.current?.focus(); }}
            className={CHIP_CLASS}
          >
            Altro
          </button>
        </div>

        <FormField label="Data pagamento *">
          <TextInput type="date" value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} required />
        </FormField>

        <FormField label="Metodo di pagamento *">
          <Select value={paymentMethodId} onChange={(e) => setPaymentMethodId(e.target.value)} required>
            <option value="">— Seleziona —</option>
            {paymentMethods?.map((m) => (
              <option key={m.id} value={m.id}>{lookupLabel(m)}</option>
            ))}
          </Select>
        </FormField>

        <FormField label="Descrizione">
          <TextInput
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Es. Prima rata, Rinnovo…"
          />
        </FormField>
      </div>

      <StickyActions className="flex gap-3">
        <Button type="submit" loading={createPayment.isPending} className="flex-1 py-4">
          <Save className="w-5 h-5" />
          {createPayment.isPending ? 'Salvataggio…' : 'Registra Pagamento'}
        </Button>
        {onSkip && (
          <Button type="button" variant="secondary" onClick={onSkip} disabled={createPayment.isPending} className="py-4">
            Salta
          </Button>
        )}
      </StickyActions>
    </form>
  );
}
