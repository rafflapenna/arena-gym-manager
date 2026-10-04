import { useMySubscriptions } from './useMe';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { subscriptionLabel, subscriptionBadgeVariant } from '../../utils/subscriptionUtils';
import { lookupLabel } from '../../utils/lookupLabels';
import { formatDayMonthYear } from '../../utils/dateTime';

export function MySubscriptionsPage() {
  const { data, isLoading, isError } = useMySubscriptions();

  const subscriptions = data ?? [];

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-[18px]">
        I miei abbonamenti
      </h1>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <ErrorMessage message="Errore nel caricamento degli abbonamenti." />
      ) : subscriptions.length === 0 ? (
        <p className="text-zinc-500 text-[15px] text-center py-10">Nessun abbonamento trovato.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {subscriptions.map((s) => (
            <div key={s.id} className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
              <div className="flex items-center justify-between gap-3 mb-3">
                <span className="text-zinc-100 text-[15.5px] font-semibold truncate">{subscriptionLabel(s)}</span>
                <Badge variant={subscriptionBadgeVariant(s.status.code)}>{lookupLabel(s.status)}</Badge>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">Inizio</p>
                  <p className="text-zinc-300 text-[14.5px]">{formatDayMonthYear(s.startDate)}</p>
                </div>
                <div>
                  <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">Fine</p>
                  <p className="text-zinc-300 text-[14.5px]">{formatDayMonthYear(s.endDate)}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
