import { Link, useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft } from 'lucide-react';
import { useSubscriptionsByMember } from '../subscriptions/useSubscriptions';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { subscriptionLabel } from '../../utils/subscriptionUtils';
import { formatDayMonthYear } from '../../utils/dateTime';
import { PaymentStep } from './steps/PaymentStep';

/**
 * Standalone "Registra pagamento" page — mounts the same `PaymentStep` the wizard uses (§4 of
 * the design), reachable from any subscription regardless of status, with a header recapping
 * the target subscription instead of the wizard shell/stepper.
 */
export function PaymentStepStandalonePage() {
  const { memberId, subId } = useParams<{ memberId: string; subId: string }>();
  const navigate = useNavigate();
  const mid = Number(memberId);
  const subscriptionId = Number(subId);

  const { data: subscriptions, isLoading, isError } = useSubscriptionsByMember(mid);
  const subscription = subscriptions?.find((s) => s.id === subscriptionId);

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-xl mx-auto">
      <Link
        to={`/members/${mid}`}
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        Socio
      </Link>

      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-2">
        Registra Pagamento
      </h1>

      {subscription && (
        <p className="text-zinc-500 text-sm mb-8">
          Abbonamento: <span className="text-zinc-300">{subscriptionLabel(subscription)}</span>{' '}
          ({formatDayMonthYear(subscription.startDate)} → {formatDayMonthYear(subscription.endDate)})
        </p>
      )}

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dell'abbonamento." />}
      {!isLoading && !isError && !subscription && <ErrorMessage message="Abbonamento non trovato." />}

      {subscription && (
        <PaymentStep subscription={subscription} onCommit={() => navigate(`/members/${mid}`)} />
      )}
    </div>
  );
}
