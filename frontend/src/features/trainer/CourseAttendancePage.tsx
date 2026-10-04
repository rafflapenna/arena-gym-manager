import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, DoorOpen } from 'lucide-react';
import { useTrainerCourses, useCourseAttendance, usePostAttendance } from './useTrainerCourses';
import { getApiErrorMessage } from '../../utils/apiError';
import { formatTime } from '../../utils/dateTime';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';

/**
 * Manual member-ID entry; a search/autocomplete or QR flow would be a nicer follow-up,
 * but this is a functional first pass for marking course attendance.
 */
export function CourseAttendancePage() {
  const { id } = useParams<{ id: string }>();
  const courseId = Number(id);

  const { data: courses } = useTrainerCourses();
  const course = courses?.find((c) => c.id === courseId);

  const { data: entries, isLoading, isError } = useCourseAttendance(courseId);
  const postAttendance = usePostAttendance(courseId);

  const [memberId, setMemberId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const id = Number(memberId);
    if (!id) return;
    postAttendance.mutate(id, {
      onSuccess: () => setMemberId(''),
      onError: (err) => setError(getApiErrorMessage(err)),
    });
  };

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <Link
        to="/trainer"
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        I miei corsi
      </Link>

      <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight mb-1">
        Presenze
      </h1>
      {course && <p className="text-zinc-500 text-sm mb-8">{course.name}</p>}

      <form
        onSubmit={handleSubmit}
        className="bg-zinc-800 rounded-2xl p-5 flex flex-col sm:flex-row items-end gap-3 mb-8"
      >
        <div className="flex-1 w-full">
          <label
            htmlFor="attendance-member-id"
            className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5"
          >
            ID socio
          </label>
          <input
            id="attendance-member-id"
            type="number"
            min="1"
            value={memberId}
            onChange={(e) => setMemberId(e.target.value)}
            required
            className="w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
          />
        </div>
        <button
          type="submit"
          disabled={postAttendance.isPending}
          className="w-full sm:w-auto flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-5 py-3 rounded-xl text-sm hover:bg-lime-300 disabled:opacity-50 transition-colors"
        >
          <DoorOpen className="w-4 h-4" />
          {postAttendance.isPending ? 'Registrazione…' : 'Registra presenza'}
        </button>
      </form>

      {error && <div className="mb-6"><ErrorMessage message={error} /></div>}

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento delle presenze." />}

      {entries && (
        <div className="flex flex-col gap-2">
          {entries.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessuna presenza registrata.</p>
          ) : (
            entries.map((entry) => (
              <div
                key={entry.checkInId}
                className="flex items-center justify-between bg-zinc-800 rounded-xl px-4 py-3"
              >
                <span className="text-zinc-100 text-sm font-medium">{entry.memberName}</span>
                <span className="text-zinc-500 text-xs">{formatTime(entry.timestamp)}</span>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
