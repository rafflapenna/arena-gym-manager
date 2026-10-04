import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ChevronLeft, Save } from 'lucide-react';
import { usePackage, useCreatePackage, useUpdatePackage } from './usePackages';
import { CourseMultiSelect } from './CourseMultiSelect';
import { getApiErrorMessage } from '../../utils/apiError';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { MoneyInput } from '../../components/ui/MoneyInput';
import { TextInput } from '../../components/ui/TextInput';
import { Button } from '../../components/ui/Button';
import { StickyActions } from '../../components/ui/StickyActions';
import type { PackageRequest } from '../../types';

type FormData = {
  name: string;
  price: string;
  durationMonths: string;
  active: boolean;
  courseIds: number[];
};

const empty: FormData = { name: '', price: '', durationMonths: '', active: true, courseIds: [] };

export function PackageFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = !!id;
  const packageId = isEdit ? Number(id) : 0;
  const navigate = useNavigate();

  const { data: pkg, isLoading } = usePackage(packageId);
  const createPackage = useCreatePackage();
  const updatePackage = useUpdatePackage();

  const [form, setForm] = useState<FormData>(empty);
  const [initialized, setInitialized] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  if (isEdit && pkg && !initialized) {
    setForm({
      name: pkg.name,
      price: String(pkg.price),
      durationMonths: String(pkg.durationMonths),
      active: pkg.active,
      courseIds: pkg.courses.map((c) => c.id),
    });
    setInitialized(true);
  }

  const setInput = (field: 'name' | 'price' | 'durationMonths') =>
    (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const data: PackageRequest = {
      name: form.name,
      price: parseFloat(form.price),
      durationMonths: Number(form.durationMonths),
      active: form.active,
      courseIds: form.courseIds,
    };

    try {
      if (isEdit) {
        await updatePackage.mutateAsync({ id: packageId, data });
      } else {
        await createPackage.mutateAsync(data);
      }
      navigate('/packages');
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  const isPending = createPackage.isPending || updatePackage.isPending;

  if (isEdit && isLoading) return <LoadingSpinner />;
  if (isEdit && !pkg) return <div className="p-8"><ErrorMessage message="Pacchetto non trovato." /></div>;

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <Link
        to="/packages"
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        Pacchetti
      </Link>

      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        {isEdit ? 'Modifica Pacchetto' : 'Nuovo Pacchetto'}
      </h1>

      {apiError && <div className="mb-4"><ErrorMessage message={apiError} /></div>}

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
          <FormField label="Nome *">
            <TextInput value={form.name} onChange={setInput('name')} required />
          </FormField>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <FormField label="Prezzo (€) *">
              <MoneyInput value={form.price} onChange={setInput('price')} required />
            </FormField>
            <FormField label="Durata (mesi) *">
              <TextInput type="number" inputMode="numeric" value={form.durationMonths} onChange={setInput('durationMonths')} required min="1" step="1" />
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

        <div className="bg-zinc-800 rounded-2xl p-5">
          <CourseMultiSelect
            value={form.courseIds}
            onChange={(courseIds) => setForm((prev) => ({ ...prev, courseIds }))}
          />
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
