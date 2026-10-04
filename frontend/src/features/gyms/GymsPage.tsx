import { useState } from 'react';
import { Building2, Pencil, Trash2, X, Plus, Users, UserCheck } from 'lucide-react';
import { AxiosError } from 'axios';
import { useGyms, useCreateGym, useUpdateGym, useDeleteGym } from './useGyms';
import { getApiErrorMessage } from '../../utils/apiError';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { AddressFields } from '../../components/address';
import type { GymResponse, GymRequest } from '../../types/gym';

function errMessage(e: unknown): string {
  if (e instanceof AxiosError && e.response?.status === 409) {
    return 'Impossibile eliminare: la palestra ha utenti o soci collegati. Riassegnali prima di procedere.';
  }
  return getApiErrorMessage(e);
}

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

export function GymsPage() {
  const gymsQ = useGyms();
  const createGym = useCreateGym();
  const updateGym = useUpdateGym();
  const deleteGym = useDeleteGym();

  const [editingId, setEditingId] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<GymRequest>(emptyForm());
  const [error, setError] = useState('');
  const [deleteError, setDeleteError] = useState('');
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  const editing = editingId !== null;
  const submitting = createGym.isPending || updateGym.isPending;

  const resetForm = () => {
    setEditingId(null);
    setShowForm(false);
    setForm(emptyForm());
    setError('');
  };

  const startCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError('');
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const startEdit = (gym: GymResponse) => {
    setEditingId(gym.id);
    setForm({
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
    setError('');
    setShowForm(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleChange = (field: keyof GymRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // Strip empty optional strings to avoid sending empty strings to backend
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
      if (editing) {
        await updateGym.mutateAsync({ id: editingId!, data: payload });
      } else {
        await createGym.mutateAsync(payload);
      }
      resetForm();
    } catch (err) {
      setError(errMessage(err));
    }
  };

  const handleDelete = async (id: number) => {
    setDeleteError('');
    try {
      await deleteGym.mutateAsync(id);
      setDeleteConfirmId(null);
    } catch (err) {
      setDeleteError(errMessage(err));
      setDeleteConfirmId(null);
    }
  };

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-4xl mx-auto">
      <div className="mb-8 flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight">
            Palestre
          </h1>
          <p className="text-zinc-500 text-sm mt-2">
            Gestione delle palestre. Solo gli amministratori possono modificare queste impostazioni.
          </p>
        </div>
        {!showForm && (
          <button
            onClick={startCreate}
            className="shrink-0 flex items-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold px-4 py-2.5 rounded-xl text-sm hover:bg-lime-300 transition-colors"
          >
            <Plus className="w-4 h-4" />
            Nuova palestra
          </button>
        )}
      </div>

      {/* Create / edit form */}
      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-zinc-800 rounded-2xl p-5 mb-8 flex flex-col gap-4"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-zinc-300 font-semibold text-sm">
              {editing ? (
                <Pencil className="w-4 h-4 text-lime-400" />
              ) : (
                <Plus className="w-4 h-4 text-lime-400" />
              )}
              {editing ? 'Modifica palestra' : 'Nuova palestra'}
            </div>
            <button
              type="button"
              onClick={resetForm}
              className="flex items-center gap-1 text-zinc-500 hover:text-zinc-300 text-xs"
            >
              <X className="w-3.5 h-3.5" /> Annulla
            </button>
          </div>

          {/* Name — required */}
          <FormField label="Nome palestra *">
            <TextInput required value={form.name} onChange={handleChange('name')} />
          </FormField>

          {/* Address — comune autocomplete with derived provincia/regione/CAP */}
          <AddressFields
            showCountry={false}
            value={form}
            onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
          />

          <FormField label="Telefono">
            <TextInput type="tel" value={form.phone} onChange={handleChange('phone')} />
          </FormField>

          {/* VAT / Contact email */}
          <div className="flex flex-col sm:flex-row gap-3">
            <FormField label="Partita IVA">
              <TextInput value={form.vatNumber} onChange={handleChange('vatNumber')} />
            </FormField>
            <FormField label="Email di contatto">
              <TextInput type="email" value={form.contactEmail} onChange={handleChange('contactEmail')} />
            </FormField>
          </div>

          {error && <ErrorMessage message={error} />}

          <button
            type="submit"
            disabled={submitting}
            className="self-start bg-lime-400 text-[#0b0b0d] font-bold px-5 py-2.5 rounded-xl text-sm hover:bg-lime-300 transition-colors disabled:opacity-50"
          >
            {submitting ? 'Salvataggio…' : editing ? 'Salva modifiche' : 'Crea palestra'}
          </button>
        </form>
      )}

      {/* List */}
      {deleteError && (
        <div className="mb-6">
          <ErrorMessage message={deleteError} />
        </div>
      )}
      {gymsQ.isLoading && <LoadingSpinner />}
      {gymsQ.isError && <ErrorMessage message="Errore nel caricamento delle palestre." />}

      {gymsQ.data && (
        <div className="flex flex-col gap-3">
          {gymsQ.data.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">
              Nessuna palestra ancora. Creane una per iniziare.
            </p>
          ) : (
            gymsQ.data.map((gym) => (
              <div key={gym.id} className="bg-zinc-800 rounded-2xl p-5">
                {/* Confirm delete inline */}
                {deleteConfirmId === gym.id ? (
                  <div className="flex flex-col sm:flex-row sm:items-center gap-3">
                    <p className="text-zinc-300 text-sm">
                      Eliminare <span className="font-semibold text-zinc-100">{gym.name}</span>? L&apos;operazione non è reversibile.
                    </p>
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => handleDelete(gym.id)}
                        disabled={deleteGym.isPending}
                        className="bg-red-500 hover:bg-red-400 text-white font-semibold px-3 py-1.5 rounded-lg text-xs transition-colors disabled:opacity-50"
                      >
                        {deleteGym.isPending ? 'Eliminazione…' : 'Elimina'}
                      </button>
                      <button
                        onClick={() => setDeleteConfirmId(null)}
                        className="text-zinc-500 hover:text-zinc-300 text-xs px-3 py-1.5 rounded-lg border border-zinc-700 transition-colors"
                      >
                        Annulla
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-start gap-4">
                    <div className="w-10 h-10 bg-lime-400/10 rounded-xl flex items-center justify-center shrink-0 mt-0.5">
                      <Building2 className="w-5 h-5 text-lime-400" />
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="text-zinc-100 font-semibold text-sm">{gym.name}</p>
                        {gym.city && (
                          <Badge variant="zinc">{gym.city}{gym.province ? ` (${gym.province})` : ''}</Badge>
                        )}
                      </div>

                      {gym.instructorEmail && (
                        <p className="text-zinc-500 text-xs mt-1 truncate">
                          Capopalestra: {gym.instructorEmail}
                        </p>
                      )}
                      {!gym.instructorEmail && (
                        <p className="text-zinc-600 text-xs mt-1 italic">Nessun capopalestra assegnato</p>
                      )}

                      <div className="flex items-center gap-4 mt-2 flex-wrap">
                        <span className="flex items-center gap-1 text-zinc-500 text-xs">
                          <UserCheck className="w-3.5 h-3.5" />
                          {gym.staffCount} staff
                        </span>
                        <span className="flex items-center gap-1 text-zinc-500 text-xs">
                          <Users className="w-3.5 h-3.5" />
                          {gym.memberCount} soci
                        </span>
                        {gym.contactEmail && (
                          <span className="text-zinc-600 text-xs truncate">{gym.contactEmail}</span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => startEdit(gym)}
                        className="text-zinc-500 hover:text-lime-400 transition-colors p-2 rounded-lg hover:bg-white/5"
                        title="Modifica"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => setDeleteConfirmId(gym.id)}
                        className="text-zinc-500 hover:text-red-400 transition-colors p-2 rounded-lg hover:bg-white/5"
                        title="Elimina"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
