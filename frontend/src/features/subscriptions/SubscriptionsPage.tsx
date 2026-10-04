import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSubscriptions } from './useSubscriptions';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FilterChips } from '../../components/ui/FilterChips';
import { subscriptionBadgeVariant, subscriptionLabel } from '../../utils/subscriptionUtils';
import { formatDayMonthYear } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/currency';
import { lookupLabel } from '../../utils/lookupLabels';

type StatusFilter = 'all' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';

// L'annullamento abbonamento vive solo nella scheda socio (MemberDetailPage): qui, in un elenco
// di tutti gli abbonamenti della palestra, il nome del socio a cui si riferisce la conferma non
// è mai a fuoco quanto nella sua scheda — rischio di annullare quello sbagliato.
export function SubscriptionsPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const { data: subscriptions, isLoading, isError } = useSubscriptions(
    statusFilter === 'all' ? undefined : statusFilter
  );

  const tabs: { key: StatusFilter; label: string }[] = [
    { key: 'all', label: 'Tutti' },
    { key: 'ACTIVE', label: 'Attivi' },
    { key: 'EXPIRED', label: 'Scaduti' },
    { key: 'CANCELLED', label: 'Annullati' },
  ];

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        Abbonamenti
      </h1>

      <div className="mb-6">
        <FilterChips options={tabs} value={statusFilter} onChange={setStatusFilter} aria-label="Filtra per stato" />
      </div>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento degli abbonamenti." />}

      {subscriptions && (
        <div className="flex flex-col gap-3">
          {subscriptions.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun abbonamento trovato.</p>
          ) : (
            subscriptions.map((sub) => (
              <div
                key={sub.id}
                className="bg-zinc-800 rounded-2xl p-5 border-t-2 border-t-zinc-700"
              >
                <div className="flex items-start justify-between mb-2 gap-3">
                  <div className="min-w-0">
                    <Link
                      to={`/members/${sub.memberId}`}
                      className="text-zinc-100 font-semibold text-sm hover:text-lime-400 transition-colors truncate block"
                    >
                      {sub.memberFullName}
                    </Link>
                    <p className="text-zinc-500 text-xs">{subscriptionLabel(sub)}</p>
                  </div>
                  <Badge variant={subscriptionBadgeVariant(sub.status.code)}>
                    {lookupLabel(sub.status)}
                  </Badge>
                </div>
                <div className="flex items-center gap-4 text-xs text-zinc-500">
                  <span>{formatDayMonthYear(sub.startDate)} → {formatDayMonthYear(sub.endDate)}</span>
                  <span className="text-cyan-400 font-semibold">{formatCurrency(sub.price)}</span>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
