import { useState } from 'react';
import { Building2, Save, Check } from 'lucide-react';
import { useMyGym, useUpdateMyGym } from './useGyms';
import { getApiErrorMessage } from '../../utils/apiError';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Button } from '../../components/ui/Button';
import { AddressFields } from '../../components/address';
import type { GymRequest, GymResponse } from '../../types/gym';

const emptyForm = (): GymRequest => ({
  name: '',
  address: '',
  city: '',
  postalCode: '',
  province: '',
  region: '',
  phone: '',
  vatNumber: '',
  contactEmail: '',
});

const toForm = (gym: GymResponse): GymRequest => ({
  name: gym.name,
  address: gym.address ?? '',
  city: gym.city ?? '',
  postalCode: gym.postalCode ?? '',
  province: gym.province ?? '',
  region: gym.region ?? '',
  phone: gym.phone ?? '',
  vatNumber: gym.vatNumber ?? '',
  contactEmail: gym.contactEmail ?? '',
});

/** Instructor self-service: view and edit the data of their own gym (PUT /gyms/me). */
export function MyGymPage() {
  const gymQ = useMyGym();
  const updateGym = useUpdateMyGym();

  // Both are lazily initialized from gymQ.data so the form is already populated on the very
  // first render when the query data is already in the TanStack cache at mount (in which case
  // the sync effect below is a no-op, since prevGymData already matches gymQ.data).
  const [form, setForm] = useState<GymRequest>(() => (gymQ.data ? toForm(gymQ.data) : emptyForm()));
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [prevGymData, setPrevGymData] = useState(() => gymQ.data);

  // Populate the form when query data arrives or changes.
  // Pattern: adjust state during render by tracking the previous data reference.
  if (gymQ.data && gymQ.data !== prevGymData) {
    setPrevGymData(gymQ.data);
    setForm(toForm(gymQ.data));
  }

  const handleChange = (field: keyof GymRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    setSaved(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    const payload: GymRequest = {
      name: form.name,
      ...(form.address?.trim() && { address: form.address.trim() }),
      ...(form.city?.trim() && { city: form.city.trim() }),
      ...(form.postalCode?.trim() && { postalCode: form.postalCode.trim() }),
      ...(form.province?.trim() && { province: form.province.trim() }),
      ...(form.region?.trim() && { region: form.region.trim() }),
      ...(form.phone?.trim() && { phone: form.phone.trim() }),
      ...(form.vatNumber?.trim() && { vatNumber: form.vatNumber.trim() }),
      ...(form.contactEmail?.trim() && { contactEmail: form.contactEmail.trim() }),
    };
    try {
      await updateGym.mutateAsync(payload);
      setSaved(true);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <div className="mb-8 flex items-center gap-3">
        <div className="w-10 h-10 bg-lime-400/10 rounded-xl flex items-center justify-center shrink-0">
          <Building2 className="w-5 h-5 text-lime-400" />
        </div>
        <div>
          <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight">
            La mia palestra
          </h1>
          <p className="text-zinc-500 text-sm mt-2">Aggiorna i dati della tua palestra.</p>
        </div>
      </div>

      {gymQ.isLoading && <LoadingSpinner />}
      {gymQ.isError && <ErrorMessage message="Errore nel caricamento della palestra." />}

      {gymQ.data && (
        <form onSubmit={handleSubmit} className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4">
          <FormField label="Nome palestra *">
            <TextInput required value={form.name} onChange={handleChange('name')} />
          </FormField>

          <AddressFields
            showCountry={false}
            value={form}
            onChange={(patch) => { setForm((prev) => ({ ...prev, ...patch })); setSaved(false); }}
          />

          <FormField label="Telefono">
            <TextInput type="tel" value={form.phone} onChange={handleChange('phone')} />
          </FormField>

          <div className="flex flex-col sm:flex-row gap-3">
            <FormField label="Partita IVA">
              <TextInput value={form.vatNumber} onChange={handleChange('vatNumber')} />
            </FormField>
            <FormField label="Email di contatto">
              <TextInput type="email" value={form.contactEmail} onChange={handleChange('contactEmail')} />
            </FormField>
          </div>

          {error && <ErrorMessage message={error} />}

          <Button type="submit" loading={updateGym.isPending} className="self-start">
            {saved && !updateGym.isPending ? <Check className="w-4 h-4" /> : <Save className="w-4 h-4" />}
            {updateGym.isPending ? 'Salvataggio…' : saved ? 'Salvato' : 'Salva modifiche'}
          </Button>
        </form>
      )}
    </div>
  );
}
