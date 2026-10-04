import { useMyPayments } from './useMe';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatCurrency } from '../../utils/currency';
import { formatDayMonthYear } from '../../utils/dateTime';
import { lookupLabel } from '../../utils/lookupLabels';

export function MyPaymentsPage() {
  const { data, isLoading, isError } = useMyPayments();

  const payments = data ?? [];

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-[18px]">
        I miei pagamenti
      </h1>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <ErrorMessage message="Errore nel caricamento dei pagamenti." />
      ) : payments.length === 0 ? (
        <p className="text-zinc-500 text-[15px] text-center py-10">Nessun pagamento trovato.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {payments.map((p) => (
            <div key={p.id} className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">Data</p>
                  <p className="text-zinc-300 text-[14.5px]">{formatDayMonthYear(p.paymentDate)}</p>
                </div>
                <div>
                  <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">Importo</p>
                  <p className="text-lime-400 font-bold text-[14.5px]">{formatCurrency(p.amount)}</p>
                </div>
                <div>
                  <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">Metodo</p>
                  <p className="text-zinc-300 text-[14.5px]">{lookupLabel(p.paymentMethod)}</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
