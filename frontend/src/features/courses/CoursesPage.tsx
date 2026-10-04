import { Link } from 'react-router-dom';
import { Boxes, ChevronRight, Plus } from 'lucide-react';
import { useCourses } from './useCourses';
import { Badge } from '../../components/ui/Badge';
import { formatCourseDays } from '../../utils/courseDays';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';


export function CoursesPage() {
  const { data: courses, isLoading, isError } = useCourses();

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight">
          Corsi
        </h1>
        <Link
          to="/courses/new"
          className="shrink-0 flex items-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-lime-300 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Nuovo
        </Link>
      </div>

      <Link
        to="/packages"
        className="inline-flex items-center gap-1.5 text-lime-400 hover:text-lime-300 text-xs font-semibold mb-6 transition-colors"
      >
        <Boxes className="w-3.5 h-3.5" />
        Gestisci Pacchetti
      </Link>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei corsi." />}

      {courses && (
        <div className="flex flex-col gap-3">
          {courses.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun corso trovato.</p>
          ) : (
            courses.map((c) => (
              <Link
                key={c.id}
                to={`/courses/${c.id}/edit`}
                className="flex items-center gap-4 bg-zinc-800 hover:bg-zinc-700/80 rounded-2xl p-4 transition-colors group"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="text-zinc-100 font-semibold text-sm truncate">{c.name}</p>
                    <Badge variant={c.active ? 'lime' : 'zinc'}>
                      {c.active ? 'Attivo' : 'Inattivo'}
                    </Badge>
                  </div>
                  <p className="text-zinc-500 text-xs truncate">
                    {c.discipline.name} · {c.trainer?.fullName ?? 'Nessun insegnante'}
                  </p>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-cyan-400 text-xs font-semibold text-right max-w-[6.5rem] leading-snug">
                    {formatCourseDays(c.daysOfWeek ?? (c.dayOfWeek ? [c.dayOfWeek] : []))}
                    <br />
                    {c.startTime}
                  </span>
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
