import { useMyCourses } from './useMe';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatCourseDays } from '../../utils/courseDays';

/** Read-only "available courses" view for the MEMBER area. */
export function MyCoursesPage() {
  const { data: courses, isLoading, isError } = useMyCourses();

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-[18px]">
        Corsi
      </h1>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <ErrorMessage message="Errore nel caricamento dei corsi." />
      ) : !courses || courses.length === 0 ? (
        <p className="text-zinc-500 text-[15px] text-center py-10">Nessun corso disponibile.</p>
      ) : (
        <div className="flex flex-col gap-3">
          {courses.map((c) => (
            <div key={c.id} className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
              <div className="flex items-center justify-between gap-3 mb-1">
                <span className="text-zinc-100 text-[15.5px] font-semibold truncate">{c.name}</span>
                <span className="px-2.5 py-1 bg-lime-400/10 text-lime-400 text-xs font-semibold rounded-full whitespace-nowrap">
                  {formatCourseDays(c.daysOfWeek ?? (c.dayOfWeek ? [c.dayOfWeek] : []))} {c.startTime}
                </span>
              </div>
              <p className="text-zinc-500 text-[13.5px]">
                {c.discipline.name}
                {c.level ? ` · ${c.level}` : ''}
                {c.trainer ? ` · ${c.trainer.fullName}` : ''}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
