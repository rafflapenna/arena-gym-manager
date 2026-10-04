import { useCourses } from '../courses/useCourses';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { formatCourseDays } from '../../utils/courseDays';

type CourseMultiSelectProps = {
  value: number[];
  onChange: (ids: number[]) => void;
};

/** Checkbox multi-select of the gym's courses — no external UI library, plain fieldset/legend. */
export function CourseMultiSelect({ value, onChange }: CourseMultiSelectProps) {
  const { data: courses, isLoading } = useCourses();

  const toggle = (id: number) => {
    onChange(value.includes(id) ? value.filter((v) => v !== id) : [...value, id]);
  };

  if (isLoading) return <LoadingSpinner />;

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5">
        Corsi inclusi
      </legend>

      {!courses || courses.length === 0 ? (
        <p className="text-zinc-600 text-sm">Nessun corso disponibile. Crea prima un corso.</p>
      ) : (
        <div className="flex flex-col gap-2 max-h-72 overflow-y-auto bg-zinc-900 border border-white/5 rounded-xl p-3">
          {courses.map((c) => (
            <label
              key={c.id}
              className="flex items-center gap-3 min-h-11 px-2 py-2 rounded-lg hover:bg-white/5 cursor-pointer transition-colors"
            >
              <input
                type="checkbox"
                checked={value.includes(c.id)}
                onChange={() => toggle(c.id)}
                className="w-4 h-4 rounded border-white/20 bg-zinc-800 text-lime-400 focus:ring-lime-400/30 focus:ring-offset-0"
              />
              <span className="flex-1 min-w-0 text-zinc-100 text-sm truncate">
                {c.name}
                <span className="text-zinc-500"> · {c.discipline.name}</span>
              </span>
              <span className="text-zinc-600 text-xs shrink-0">
                {formatCourseDays(c.daysOfWeek ?? (c.dayOfWeek ? [c.dayOfWeek] : []))} {c.startTime}
              </span>
            </label>
          ))}
        </div>
      )}
    </fieldset>
  );
}
