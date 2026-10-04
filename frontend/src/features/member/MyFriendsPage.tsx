import { useId, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { Save } from 'lucide-react';
import { useMyLeads, useCreateMyLead, useMyCourses } from './useMe';
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError';
import { myLeadStatusLabel, leadStatusBadgeVariant } from '../../utils/leadUtils';
import { formatDayMonthYear, todayIsoLocal } from '../../utils/dateTime';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import type { MyLeadRequest } from '../../types';

/** Error copy keyed by backend `code` — anything else falls back to getApiErrorMessage (spec §FE). */
function friendRequestErrorMessage(err: unknown): string {
  switch (getApiErrorCode(err)) {
    case 'LEAD_DUPLICATE_PHONE':
      return 'Questo numero è già stato segnalato in palestra.';
    case 'LEAD_PENDING_LIMIT':
      return 'Hai già 5 amici in attesa di conferma. Potrai segnalarne altri quando la reception li avrà gestiti.';
    default:
      return getApiErrorMessage(err);
  }
}

type FormState = {
  firstName: string;
  lastName: string;
  phone: string;
  trialDate: string;
  courseId: string;
};

function buildEmptyForm(): FormState {
  return { firstName: '', lastName: '', phone: '', trialDate: '', courseId: '' };
}

/** "Porta un amico": the member reports a friend's free trial; the reception confirms it. */
export function MyFriendsPage() {
  const { data: leads, isLoading, isError } = useMyLeads();
  const { data: courses, isError: coursesError } = useMyCourses();
  const createLead = useCreateMyLead();

  const [form, setForm] = useState<FormState>(buildEmptyForm);
  const [apiError, setApiError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const firstNameId = useId();
  const lastNameId = useId();
  const phoneId = useId();
  const trialDateId = useId();
  const courseId = useId();

  const showCourseField = !coursesError && !!courses && courses.length > 0;

  const setField = (field: keyof FormState) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);
    setSuccessMessage(null);

    const data: MyLeadRequest = {
      firstName: form.firstName,
      lastName: form.lastName,
      phone: form.phone,
      trialDate: form.trialDate || null,
      courseId: form.courseId ? Number(form.courseId) : null,
    };

    try {
      await createLead.mutateAsync(data);
      setForm(buildEmptyForm());
      setSuccessMessage('Prova segnalata! La reception la confermerà a breve.');
    } catch (err) {
      setApiError(friendRequestErrorMessage(err));
    }
  };

  return (
    <div className="px-[18px] py-6 md:px-10 md:py-10 max-w-2xl mx-auto font-body">
      <h1 className="font-display font-bold text-[30px] text-zinc-100 uppercase leading-none mb-1.5">
        Porta un amico
      </h1>
      <p className="text-zinc-500 text-[14px] mb-[18px]">
        Segnala la prova gratuita di un amico: se poi si iscrive, maturi uno sconto.
      </p>

      <form
        onSubmit={handleSubmit}
        className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px] flex flex-col gap-4 mb-6"
      >
        {apiError && (
          <div role="alert">
            <ErrorMessage message={apiError} />
          </div>
        )}
        {successMessage && (
          <p role="status" className="bg-lime-400/10 border border-lime-400/20 rounded-xl p-4 text-lime-400 text-sm">
            {successMessage}
          </p>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <FormField label="Nome *" htmlFor={firstNameId}>
            <TextInput
              id={firstNameId}
              value={form.firstName}
              onChange={setField('firstName')}
              required
              maxLength={100}
            />
          </FormField>
          <FormField label="Cognome *" htmlFor={lastNameId}>
            <TextInput
              id={lastNameId}
              value={form.lastName}
              onChange={setField('lastName')}
              required
              maxLength={100}
            />
          </FormField>
        </div>

        <FormField label="Cellulare *" htmlFor={phoneId}>
          <TextInput
            id={phoneId}
            type="tel"
            value={form.phone}
            onChange={setField('phone')}
            required
            maxLength={20}
          />
        </FormField>

        <FormField label="Giorno di prova proposto" htmlFor={trialDateId}>
          <TextInput
            id={trialDateId}
            type="date"
            value={form.trialDate}
            onChange={setField('trialDate')}
            min={todayIsoLocal()}
          />
        </FormField>

        {showCourseField && (
          <FormField label="Corso" htmlFor={courseId}>
            <Select
              id={courseId}
              value={form.courseId}
              onChange={(e) => setForm((prev) => ({ ...prev, courseId: e.target.value }))}
            >
              <option value="">— Nessuno —</option>
              {courses?.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </Select>
          </FormField>
        )}

        <button
          type="submit"
          disabled={createLead.isPending}
          className="flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3.5 rounded-2xl hover:bg-lime-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <Save className="w-4 h-4" />
          {createLead.isPending ? 'Invio…' : 'Segnala la prova'}
        </button>
      </form>

      <h2 className="text-zinc-500 text-xs font-semibold uppercase tracking-widest mb-3">I miei amici</h2>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento degli amici segnalati." />}

      {leads && (
        <div className="flex flex-col gap-3">
          {leads.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Non hai ancora segnalato nessun amico.</p>
          ) : (
            leads.map((lead) => (
              <div key={lead.id} className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
                <div className="flex items-start justify-between gap-3 mb-1">
                  <p className="text-zinc-100 font-semibold text-sm truncate min-w-0">
                    {lead.firstName} {lead.lastName}
                  </p>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <Badge variant={leadStatusBadgeVariant(lead.status)}>{myLeadStatusLabel(lead.status)}</Badge>
                    {lead.rewardEarned && <Badge variant="lime">Sconto maturato</Badge>}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-zinc-500">
                  {lead.trialDate && <span>Prova il {formatDayMonthYear(lead.trialDate)}</span>}
                  {lead.interestCourse && (
                    <span className="text-cyan-400 font-semibold" style={{ overflowWrap: 'anywhere' }}>
                      {lead.interestCourse.name}
                    </span>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
