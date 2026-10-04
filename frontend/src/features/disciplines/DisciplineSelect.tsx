import { useState } from 'react';
import { Plus, X } from 'lucide-react';
import { useDisciplines, useCreateDiscipline } from './useDisciplines';
import { getApiErrorMessage } from '../../utils/apiError';
import { ErrorMessage } from '../../components/ui/ErrorMessage';

type DisciplineSelectProps = {
  value: number | '';
  onChange: (id: number) => void;
};

/** Controlled select of the gym's disciplines, with an inline "+ Aggiungi" quick-add row. */
export function DisciplineSelect({ value, onChange }: DisciplineSelectProps) {
  const { data: disciplines, isLoading } = useDisciplines();
  const createDiscipline = useCreateDiscipline();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  // NOT a <form>: this widget is rendered inside CourseFormPage's <form>, and a nested
  // <form> is invalid HTML — the browser gives the inner submit button the OUTER form as
  // its form owner, so clicking "Aggiungi" submitted the course form natively (full page
  // reload to /courses/new?) and never created the discipline. Driven by onClick instead,
  // with Enter wired up manually below to keep the keyboard flow.
  const handleCreate = async () => {
    if (!name.trim()) return;
    setError(null);
    try {
      const created = await createDiscipline.mutateAsync({ name: name.trim(), active: true });
      onChange(created.id);
      setName('');
      setAdding(false);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="flex flex-col gap-2">
      <select
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        disabled={isLoading}
        required
        className="w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
      >
        <option value="">— Seleziona —</option>
        {disciplines?.map((d) => (
          <option key={d.id} value={d.id}>{d.name}</option>
        ))}
      </select>

      {adding ? (
        <div className="flex items-center gap-2 bg-zinc-900 border border-white/5 rounded-xl p-3">
          <input
            type="text"
            autoFocus
            placeholder="Nome disciplina"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              // Enter would otherwise submit the surrounding course form.
              if (e.key === 'Enter') {
                e.preventDefault();
                void handleCreate();
              }
            }}
            className="flex-1 bg-zinc-800 border border-white/5 rounded-lg px-3 py-2 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50"
          />
          <button
            type="button"
            onClick={() => void handleCreate()}
            disabled={createDiscipline.isPending || !name.trim()}
            className="bg-lime-400 text-[#0b0b0d] font-bold px-3 py-2 rounded-lg text-xs hover:bg-lime-300 disabled:opacity-50 transition-colors"
          >
            {createDiscipline.isPending ? '…' : 'Aggiungi'}
          </button>
          <button
            type="button"
            onClick={() => { setAdding(false); setName(''); setError(null); }}
            className="text-zinc-500 hover:text-zinc-300 transition-colors p-1"
            title="Annulla"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="self-start inline-flex items-center gap-1.5 min-h-11 -my-2.5 px-1 text-lime-400 hover:text-lime-300 text-xs font-semibold transition-colors"
        >
          <Plus className="w-3.5 h-3.5" /> Aggiungi disciplina
        </button>
      )}

      {error && <ErrorMessage message={error} />}
    </div>
  );
}
