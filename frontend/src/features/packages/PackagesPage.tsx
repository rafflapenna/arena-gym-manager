import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, PackagePlus } from 'lucide-react';
import { usePackages } from './usePackages';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatCurrency } from '../../utils/currency';

export function PackagesPage() {
  const { data: packages, isLoading, isError } = usePackages();

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <Link
        to="/courses"
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        Corsi
      </Link>

      <div className="flex items-center justify-between mb-8">
        <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight">
          Pacchetti
        </h1>
        <Link
          to="/packages/new"
          className="shrink-0 flex items-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-lime-300 transition-colors"
        >
          <PackagePlus className="w-4 h-4" />
          Nuovo
        </Link>
      </div>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei pacchetti." />}

      {packages && (
        <div className="flex flex-col gap-3">
          {packages.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun pacchetto trovato.</p>
          ) : (
            packages.map((p) => (
              <Link
                key={p.id}
                to={`/packages/${p.id}/edit`}
                className="flex items-center gap-4 bg-zinc-800 hover:bg-zinc-700/80 rounded-2xl p-4 transition-colors group"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="text-zinc-100 font-semibold text-sm truncate">{p.name}</p>
                    <Badge variant={p.active ? 'lime' : 'zinc'}>
                      {p.active ? 'Attivo' : 'Inattivo'}
                    </Badge>
                  </div>
                  <p className="text-zinc-500 text-xs truncate">
                    {p.courses.length} cors{p.courses.length === 1 ? 'o' : 'i'} · {p.durationMonths} mes{p.durationMonths === 1 ? 'e' : 'i'}
                  </p>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-cyan-400 font-semibold text-sm">{formatCurrency(p.price)}</span>
                  <ChevronRight className="w-4 h-4 text-zinc-600 group-hover:text-zinc-400 transition-colors" />
                </div>
              </Link>
            ))
          )}
        </div>
      )}
    </div>
  );
}
