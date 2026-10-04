import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft, Save } from 'lucide-react';
import { useCourse, useCreateCourse, useUpdateCourse } from './useCourses';
import { useTeachers } from '../users/useUsers';
import { DisciplineSelect } from '../disciplines';
import { getApiErrorMessage } from '../../utils/apiError';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import { StickyActions } from '../../components/ui/StickyActions';
import { ALL_DAYS, dayLabel } from '../../utils/courseDays';
import type { CourseRequest } from '../../types';

type FormData = {
  name: string;
  disciplineId: number | '';
  level: string;
  trainerId: string;
  daysOfWeek: number[];
  startTime: string;
  durationMin: string;
  capacity: string;
  active: boolean;
};

const empty: FormData = {
  name: '', disciplineId: '', level: '', trainerId: '',
  daysOfWeek: [], startTime: '', durationMin: '', capacity: '', active: true,
};

export function CourseFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const courseId = isEdit ? Number(id) : 0;
  const navigate = useNavigate();

  const { data: course, isLoading } = useCourse(courseId);
  const { data: teachers, isError: teachersError } = useTeachers();
  const createCourse = useCreateCourse();
  const updateCourse = useUpdateCourse();

  const [form, setForm] = useState<FormData>(empty);
  const [initialized, setInitialized] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  if (isEdit && course && !initialized) {
    setForm({
      name: course.name,
      disciplineId: course.discipline.id,
      level: course.level ?? '',
      trainerId: course.trainer?.id.toString() ?? '',
      // Fall back to the legacy single field for a backend not yet redeployed.
      daysOfWeek: course.daysOfWeek ?? (course.dayOfWeek ? [course.dayOfWeek] : []),
      startTime: course.startTime,
      durationMin: String(course.durationMin),
      capacity: course.capacity?.toString() ?? '',
      active: course.active,
    });
    setInitialized(true);
  }

  const setInput = (field: 'name' | 'level' | 'durationMin' | 'capacity') =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const toggleDay = (day: number) =>
    setForm((prev) => ({
      ...prev,
      daysOfWeek: prev.daysOfWeek.includes(day)
        ? prev.daysOfWeek.filter((d) => d !== day)
        : [...prev.daysOfWeek, day].sort((a, b) => a - b),
    }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    // The day chips are buttons, so the browser's own "required" check can't cover them.
    if (form.daysOfWeek.length === 0) {
      setApiError('Seleziona almeno un giorno.');
      return;
    }

    const data: CourseRequest = {
      name: form.name,
      disciplineId: Number(form.disciplineId),
      level: form.level || null,
      trainerId: form.trainerId ? Number(form.trainerId) : null,
      daysOfWeek: form.daysOfWeek,
      startTime: form.startTime,
      durationMin: Number(form.durationMin),
      capacity: form.capacity ? Number(form.capacity) : null,
      active: form.active,
    };

    try {
      if (isEdit) {
        await updateCourse.mutateAsync({ id: courseId, data });
      } else {
        await createCourse.mutateAsync(data);
      }
      navigate('/courses');
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  const isPending = createCourse.isPending || updateCourse.isPending;

  if (isEdit && isLoading) return <LoadingSpinner />;
  if (isEdit && !course) return <div className="p-8"><ErrorMessage message="Corso non trovato." /></div>;

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <Link
        to="/courses"
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        Corsi
      </Link>

      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        {isEdit ? 'Modifica Corso' : 'Nuovo Corso'}
      </h1>

      {apiError && <div className="mb-4"><ErrorMessage message={apiError} /></div>}

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
          <FormField label="Nome *">
            <TextInput value={form.name} onChange={setInput('name')} required />
          </FormField>

          <FormField label="Disciplina *">
            <DisciplineSelect
              value={form.disciplineId}
              onChange={(disciplineId) => setForm((prev) => ({ ...prev, disciplineId }))}
            />
          </FormField>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Livello / fascia">
              <TextInput value={form.level} onChange={setInput('level')} placeholder="Es. Principianti" />
            </FormField>
            <FormField label="Insegnante">
              {teachersError ? (
                <p className="text-red-400 text-xs px-1 py-3">
                  Errore nel caricamento degli insegnanti. Ricarica la pagina per riprovare.
                </p>
              ) : (
                <Select
                  value={form.trainerId}
                  onChange={(e) => setForm((prev) => ({ ...prev, trainerId: e.target.value }))}
                >
                  <option value="">— Nessuno —</option>
                  {teachers?.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.firstName ?? ''} {t.lastName ?? ''}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
          </div>

          <FormField label="Giorni *">
            {/* Toggle chips rather than a multi-<select>: a course usually runs on two or
                three days, and chips stay tappable at 375px where a native multi-select is
                barely usable. `flex-wrap` lets the row break instead of overflowing. */}
            <div className="flex flex-wrap gap-2" role="group" aria-label="Giorni del corso">
              {ALL_DAYS.map((day) => {
                const selected = form.daysOfWeek.includes(day);
                return (
                  <button
                    key={day}
                    type="button"
                    onClick={() => toggleDay(day)}
                    aria-pressed={selected}
                    className={`inline-flex items-center justify-center min-h-11 min-w-11 px-3 rounded-xl text-xs font-semibold transition-colors ${
                      selected
                        ? 'bg-lime-400 text-[#0b0b0d]'
                        : 'bg-zinc-900 border border-white/5 text-zinc-400 hover:text-zinc-200'
                    }`}
                  >
                    {dayLabel(day, 'short')}
                  </button>
                );
              })}
            </div>
          </FormField>

          <FormField label="Orario inizio *">
            <TextInput
              type="time"
              value={form.startTime}
              onChange={(e) => setForm((prev) => ({ ...prev, startTime: e.target.value }))}
              required
            />
          </FormField>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Durata (min) *">
              <TextInput type="number" inputMode="numeric" value={form.durationMin} onChange={setInput('durationMin')} required min="1" step="1" />
            </FormField>
            <FormField label="Capienza">
              <TextInput type="number" inputMode="numeric" value={form.capacity} onChange={setInput('capacity')} min="1" step="1" placeholder="Illimitata" />
            </FormField>
          </div>

          <label className="flex items-center gap-3 min-h-11 -my-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm((prev) => ({ ...prev, active: e.target.checked }))}
              className="w-4 h-4 rounded border-white/20 bg-zinc-900 text-lime-400 focus:ring-lime-400/30 focus:ring-offset-0"
            />
            <span className="text-zinc-300 text-sm">Attivo</span>
          </label>
        </div>

        <StickyActions>
          <Button type="submit" loading={isPending} className="w-full py-4">
            <Save className="w-5 h-5" />
            {isPending ? 'Salvataggio…' : 'Salva'}
          </Button>
        </StickyActions>
      </form>
    </div>
  );
}
