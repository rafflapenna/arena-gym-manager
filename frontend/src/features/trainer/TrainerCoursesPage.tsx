import { Link } from 'react-router-dom';
import { ChevronRight, ClipboardCheck } from 'lucide-react';
import { useTrainerCourses } from './useTrainerCourses';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatCourseDays } from '../../utils/courseDays';

/** Read-only list of the trainer's own courses (GET /trainer/courses). */
export function TrainerCoursesPage() {
  const { data: courses, isLoading, isError } = useTrainerCourses();

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        I miei corsi
      </h1>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento dei corsi." />}

      {courses && (
        <div className="flex flex-col gap-3">
          {courses.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessun corso assegnato.</p>
          ) : (
            courses.map((c) => (
              <Link
                key={c.id}
                to={`/trainer/courses/${c.id}/attendance`}
                className="flex items-center gap-4 bg-zinc-800 hover:bg-zinc-700/80 rounded-2xl p-4 transition-colors group"
              >
                <div className="w-10 h-10 bg-lime-400/10 rounded-xl flex items-center justify-center shrink-0">
                  <ClipboardCheck className="w-5 h-5 text-lime-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <p className="text-zinc-100 font-semibold text-sm truncate">{c.name}</p>
                    <Badge variant={c.active ? 'lime' : 'zinc'}>
                      {c.active ? 'Attivo' : 'Inattivo'}
                    </Badge>
                  </div>
                  <p className="text-zinc-500 text-xs truncate">{c.discipline.name}</p>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="text-cyan-400 text-xs font-semibold text-right">
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
