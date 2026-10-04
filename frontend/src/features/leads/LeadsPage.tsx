import { useId, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Plus, Search, ChevronDown, X, Save } from 'lucide-react';
import { useLeads, useCreateLead, useConvertLead, useConfirmLead, useDeclineLead } from './useLeads';
import { useCourses } from '../courses/useCourses';
import { useDebounce } from '../../hooks/useDebounce';
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError';
import { leadStatusLabel, leadStatusBadgeVariant } from '../../utils/leadUtils';
import { formatDayMonthYear, todayIsoLocal } from '../../utils/dateTime';
import { Badge } from '../../components/ui/Badge';
import { MemberPicker } from '../members/MemberPicker';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import { Textarea } from '../../components/ui/Textarea';
import { Button } from '../../components/ui/Button';
import { IconButton } from '../../components/ui/IconButton';
import { FilterChips } from '../../components/ui/FilterChips';
import type { LeadRequest, LeadResponse, LeadStatus, MemberRef } from '../../types';

type StatusFilter = 'all' | LeadStatus;

const STATUS_TABS: { key: StatusFilter; label: string }[] = [
  { key: 'all', label: 'Tutte' },
  { key: 'REQUESTED', label: leadStatusLabel('REQUESTED') },
  { key: 'NEW', label: leadStatusLabel('NEW') },
  { key: 'TRIAL_SCHEDULED', label: leadStatusLabel('TRIAL_SCHEDULED') },
  { key: 'TRIED', label: leadStatusLabel('TRIED') },
  { key: 'CONVERTED', label: leadStatusLabel('CONVERTED') },
  { key: 'LOST', label: leadStatusLabel('LOST') },
];

/** Which inline form (if any) is open, and for which lead — only one at a time across the list. */
type ActiveAction = { id: number; mode: 'convert' | 'confirm' } | null;

export function LeadsPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [q, setQ] = useState('');
  const debouncedQ = useDebounce(q, 300);
  const [showForm, setShowForm] = useState(false);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);

  const { data: leads, isLoading, isError } = useLeads({
    status: statusFilter === 'all' ? undefined : statusFilter,
    q: debouncedQ || undefined,
  });

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <div className="flex items-center justify-between mb-6 gap-3">
        <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight">
          Prove
        </h1>
        {!showForm && (
          <Button onClick={() => setShowForm(true)} aria-label="Nuova prova">
            <Plus className="w-4 h-4" />
            {/* Icon-only below sm: the title + full label together risk overflow at 375px. */}
            <span className="hidden sm:inline">Nuova prova</span>
          </Button>
        )}
      </div>

      {showForm && <LeadQuickAddForm onClose={() => setShowForm(false)} />}

      <div className="relative mb-4">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-500 pointer-events-none" />
        <input
          type="text"
          aria-label="Cerca prove"
          placeholder="Cerca per nome, cognome, telefono…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="w-full bg-[#1c1c1f] border border-white/7 rounded-xl pl-10 pr-4 py-3 text-zinc-100 text-sm placeholder-zinc-500 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
        />
      </div>

      <div className="mb-6">
        <FilterChips options={STATUS_TABS} value={statusFilter} onChange={setStatusFilter} aria-label="Filtra per stato" />
      </div>

      {isLoading && <LoadingSpinner />}
      {isError && <ErrorMessage message="Errore nel caricamento delle prove." />}

      {leads && (
        <div className="flex flex-col gap-3">
          {leads.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">Nessuna prova trovata.</p>
          ) : (
            leads.map((lead) => (
              <LeadRow
                key={lead.id}
                lead={lead}
                isConverting={activeAction?.id === lead.id && activeAction.mode === 'convert'}
                isConfirming={activeAction?.id === lead.id && activeAction.mode === 'confirm'}
                onStartConvert={() => setActiveAction({ id: lead.id, mode: 'convert' })}
                onStartConfirm={() => setActiveAction({ id: lead.id, mode: 'confirm' })}
                onCancelAction={() => setActiveAction(null)}
                onActionDone={() => setActiveAction(null)}
              />
            ))
          )}
        </div>
      )}
    </div>
  );
}

type FormData = {
  firstName: string;
  lastName: string;
  birthDate: string;
  phone: string;
  trialDate: string;
  email: string;
  courseId: string;
  source: string;
  notes: string;
  referredBy: MemberRef | null;
};

// A fresh factory (not a module-level const) so `trialDate` always seeds to "today" at the
// moment the form is opened, not at module load — matters if the tab stays open past midnight.
function buildEmptyForm(): FormData {
  return {
    firstName: '',
    lastName: '',
    birthDate: '',
    phone: '',
    trialDate: todayIsoLocal(),
    email: '',
    courseId: '',
    source: '',
    notes: '',
    referredBy: null,
  };
}

/** Reception quick-add: 4 required fields + a prefilled, editable trial date; the rest collapses away. */
function LeadQuickAddForm({ onClose }: { onClose: () => void }) {
  const createLead = useCreateLead();
  const { data: courses } = useCourses();
  const [form, setForm] = useState<FormData>(buildEmptyForm);
  const [showOptional, setShowOptional] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const setInput = (field: keyof FormData) => (e: ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setApiError(null);

    const data: LeadRequest = {
      firstName: form.firstName,
      lastName: form.lastName,
      birthDate: form.birthDate,
      phone: form.phone,
      trialDate: form.trialDate || null,
      email: form.email || null,
      courseId: form.courseId ? Number(form.courseId) : null,
      source: form.source || null,
      notes: form.notes || null,
      referredByMemberId: form.referredBy?.id ?? null,
    };

    try {
      await createLead.mutateAsync(data);
      onClose();
    } catch (err) {
      setApiError(getApiErrorMessage(err));
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-zinc-800 rounded-2xl p-5 flex flex-col gap-4 mb-6">
      <div className="flex items-center justify-between -mr-2">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-widest">Nuova prova</p>
        <IconButton onClick={onClose} aria-label="Chiudi">
          <X className="w-4 h-4" />
        </IconButton>
      </div>

      {apiError && (
        <div role="alert">
          <ErrorMessage message={apiError} />
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label="Nome *">
          <TextInput value={form.firstName} onChange={setInput('firstName')} required autoFocus />
        </FormField>
        <FormField label="Cognome *">
          <TextInput value={form.lastName} onChange={setInput('lastName')} required />
        </FormField>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <FormField label="Data di nascita *">
          <TextInput type="date" value={form.birthDate} onChange={setInput('birthDate')} required />
        </FormField>
        <FormField label="Cellulare *">
          <TextInput type="tel" value={form.phone} onChange={setInput('phone')} required maxLength={20} />
        </FormField>
      </div>
      <FormField label="Data prova">
        <TextInput type="date" value={form.trialDate} onChange={setInput('trialDate')} />
      </FormField>

      <div>
        <button
          type="button"
          onClick={() => setShowOptional((v) => !v)}
          aria-expanded={showOptional}
          className="flex items-center gap-1.5 text-zinc-400 hover:text-zinc-200 text-xs font-semibold uppercase tracking-wide transition-colors"
        >
          <ChevronDown
            className={`w-3.5 h-3.5 transition-transform duration-150 ${showOptional ? 'rotate-180' : ''}`}
          />
          Altri dettagli (opzionali)
        </button>

        {showOptional && (
          <div className="flex flex-col gap-4 mt-4">
            <FormField label="Email">
              <TextInput type="email" value={form.email} onChange={setInput('email')} />
            </FormField>
            <FormField label="Corso di interesse">
              <Select
                value={form.courseId}
                onChange={(e) => setForm((prev) => ({ ...prev, courseId: e.target.value }))}
              >
                <option value="">— Nessuno —</option>
                {courses?.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </Select>
            </FormField>
            <FormField label="Portato da">
              {/* Promozione passaparola: chi porta qualcuno che poi si iscrive matura uno
                  sconto. Il legame si raccoglie qui, alla reception, finche' e' fresco. */}
              <MemberPicker
                value={form.referredBy}
                onChange={(referredBy) => setForm((prev) => ({ ...prev, referredBy }))}
                placeholder="Chi l'ha portata?"
              />
            </FormField>
            <FormField label="Provenienza">
              <TextInput value={form.source} onChange={setInput('source')} placeholder="Passaparola, social, …" />
            </FormField>
            <FormField label="Note">
              <Textarea
                value={form.notes}
                onChange={(e) => setForm((prev) => ({ ...prev, notes: e.target.value }))}
                rows={3}
              />
            </FormField>
          </div>
        )}
      </div>

      <Button type="submit" loading={createLead.isPending}>
        <Save className="w-4 h-4" />
        {createLead.isPending ? 'Salvataggio…' : 'Salva prova'}
      </Button>
    </form>
  );
}

type LeadRowProps = {
  lead: LeadResponse;
  isConverting: boolean;
  isConfirming: boolean;
  onStartConvert: () => void;
  onStartConfirm: () => void;
  onCancelAction: () => void;
  onActionDone: () => void;
};

/**
 * One row = the read-only summary (name, phone, birth date, ...) plus the CTA area. For a
 * REQUESTED lead (submitted by a member, not yet reviewed) the CTA is Confirm/Decline instead
 * of Converti — a REQUESTED lead can't be converted yet (§SPEC "prima la conferma, poi la
 * conversione"). Otherwise it's one of: "Converti in socio" button, the inline convert form, or
 * the two post-conversion links once `status === 'CONVERTED'`.
 */
function LeadRow({
  lead,
  isConverting,
  isConfirming,
  onStartConvert,
  onStartConfirm,
  onCancelAction,
  onActionDone,
}: LeadRowProps) {
  const navigate = useNavigate();
  const convertLead = useConvertLead();
  const confirmLead = useConfirmLead();
  const declineLead = useDeclineLead();
  const [email, setEmail] = useState(lead.email ?? '');
  const [birthDate, setBirthDate] = useState('');
  const [convertError, setConvertError] = useState<string | null>(null);
  const [confirmTrialDate, setConfirmTrialDate] = useState(lead.trialDate ?? todayIsoLocal());
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [declineError, setDeclineError] = useState<string | null>(null);

  const emailId = useId();
  const birthDateId = useId();
  const confirmTrialDateId = useId();

  const handleConvert = async (e: FormEvent) => {
    e.preventDefault();
    setConvertError(null);
    try {
      const { member } = await convertLead.mutateAsync({
        id: lead.id,
        email,
        birthDate: lead.birthDate ? undefined : birthDate,
      });
      onActionDone();
      // A un socio nuovo corrisponde sempre un abbonamento, quindi si va dritti sul form
      // invece di lasciare un link da cliccare: /members/:id/enroll apre il wizard allo step
      // abbonamento e prosegue col pagamento. Il link resta sulle prove GIA' convertite, dove
      // l'operatore sta solo consultando la lista e non vuole essere portato via.
      if (member?.id) navigate(`/members/${member.id}/enroll`);
    } catch (err) {
      const status = err instanceof AxiosError ? err.response?.status : undefined;
      if (status === 409) {
        const code = getApiErrorCode(err);
        setConvertError(
          code === 'LEAD_NOT_CONFIRMED'
            ? 'La prova deve essere confermata dalla reception prima dell’iscrizione.'
            : 'Questa email è già usata da un altro socio.'
        );
      } else {
        setConvertError(getApiErrorMessage(err));
      }
    }
  };

  const handleConfirm = async (e: FormEvent) => {
    e.preventDefault();
    setConfirmError(null);
    try {
      await confirmLead.mutateAsync({ id: lead.id, trialDate: confirmTrialDate });
      onActionDone();
    } catch (err) {
      setConfirmError(getApiErrorMessage(err));
    }
  };

  const handleDecline = async () => {
    if (!window.confirm(`Rifiutare la prova di ${lead.firstName} ${lead.lastName}?`)) return;
    setDeclineError(null);
    try {
      await declineLead.mutateAsync(lead.id);
    } catch (err) {
      setDeclineError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="bg-zinc-800 rounded-2xl p-5 border-t-2 border-t-zinc-700">
      <div className="flex items-start justify-between mb-2 gap-3">
        <div className="min-w-0">
          <p className="text-zinc-100 font-semibold text-sm truncate">
            {lead.firstName} {lead.lastName}
          </p>
          <a href={`tel:${lead.phone}`} className="text-cyan-400 text-xs hover:underline">{lead.phone}</a>
        </div>
        <span className="shrink-0">
          <Badge variant={leadStatusBadgeVariant(lead.status)}>{leadStatusLabel(lead.status)}</Badge>
        </span>
      </div>

      {lead.origin === 'MEMBER' && lead.referredBy && (
        <p className="text-cyan-400 text-xs font-semibold mb-2 truncate" style={{ overflowWrap: 'anywhere' }}>
          Inserita da {lead.referredBy.name}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-zinc-500 mb-3">
        {lead.birthDate ? (
          <span>Nato il {formatDayMonthYear(lead.birthDate)}</span>
        ) : (
          <span className="px-2 py-0.5 rounded-full bg-amber-400/10 text-amber-400 border border-amber-400/20">
            Data di nascita da completare
          </span>
        )}
        {lead.trialDate && <span>Prova il {formatDayMonthYear(lead.trialDate)}</span>}
        {lead.interestCourse && (
          <span className="text-cyan-400 font-semibold" style={{ overflowWrap: 'anywhere' }}>
            {lead.interestCourse.name}
          </span>
        )}
        {lead.source && <span style={{ overflowWrap: 'anywhere' }}>Da: {lead.source}</span>}
        {/* origin === 'MEMBER' already says this above as "Inserita da {name}" — no need to repeat it. */}
        {lead.origin !== 'MEMBER' && lead.referredBy && (
          <span style={{ overflowWrap: 'anywhere' }}>Portata da {lead.referredBy.name}</span>
        )}
      </div>
      {lead.notes && (
        <p className="text-zinc-400 text-xs mb-3" style={{ overflowWrap: 'anywhere' }}>
          {lead.notes}
        </p>
      )}

      {lead.matchedMemberId && (
        <Link
          to={`/members/${lead.matchedMemberId}`}
          className="inline-flex items-center gap-1.5 text-xs font-semibold mb-3 text-amber-400 hover:text-amber-300 transition-colors"
        >
          Numero già di un socio
        </Link>
      )}

      {lead.status === 'CONVERTED' && lead.convertedMemberId ? (
        <div className="flex items-center gap-2 flex-wrap">
          <Link
            to={`/members/${lead.convertedMemberId}/enroll`}
            className="inline-flex items-center gap-1.5 text-xs font-semibold min-h-11 px-3 rounded-lg bg-lime-400 text-[#0b0b0d] transition-colors"
          >
            Aggiungi abbonamento
          </Link>
          <Link
            to={`/members/${lead.convertedMemberId}`}
            className="inline-flex items-center gap-1.5 text-xs font-semibold min-h-11 px-3 rounded-lg border border-white/10 text-zinc-300 hover:text-zinc-100 transition-colors"
          >
            Vai al socio
          </Link>
        </div>
      ) : lead.status === 'REQUESTED' ? (
        isConfirming ? (
          <form
            onSubmit={handleConfirm}
            className="bg-zinc-900 border border-white/5 rounded-xl p-4 flex flex-col gap-3"
          >
            {confirmError && (
              <div role="alert">
                <ErrorMessage message={confirmError} />
              </div>
            )}
            <FormField label="Data prova *" htmlFor={confirmTrialDateId}>
              <TextInput
                id={confirmTrialDateId}
                type="date"
                value={confirmTrialDate}
                onChange={(e) => setConfirmTrialDate(e.target.value)}
                required
                autoFocus
              />
            </FormField>
            <div className="flex items-center gap-2">
              <Button type="submit" loading={confirmLead.isPending} className="flex-1">
                {confirmLead.isPending ? 'Conferma…' : 'Conferma prova'}
              </Button>
              <Button type="button" variant="secondary" onClick={onCancelAction}>
                Annulla
              </Button>
            </div>
          </form>
        ) : (
          <div className="flex flex-col gap-2">
            {declineError && (
              <div role="alert">
                <ErrorMessage message={declineError} />
              </div>
            )}
            <div className="flex items-center gap-2 flex-wrap">
              <button
                onClick={onStartConfirm}
                className="inline-flex items-center gap-1.5 text-xs font-semibold min-h-11 px-3 rounded-lg bg-lime-400/10 border border-lime-400/25 text-lime-400 hover:bg-lime-400/15 transition-colors"
              >
                Conferma
              </button>
              <Button variant="danger" onClick={handleDecline} loading={declineLead.isPending}>
                Rifiuta
              </Button>
            </div>
          </div>
        )
      ) : isConverting ? (
        <form
          onSubmit={handleConvert}
          className="bg-zinc-900 border border-white/5 rounded-xl p-4 flex flex-col gap-3"
        >
          {convertError && (
            <div role="alert">
              <ErrorMessage message={convertError} />
            </div>
          )}
          {!lead.birthDate && (
            <FormField label="Data di nascita *" htmlFor={birthDateId}>
              <TextInput
                id={birthDateId}
                type="date"
                value={birthDate}
                onChange={(e) => setBirthDate(e.target.value)}
                required
                max={todayIsoLocal()}
              />
            </FormField>
          )}
          <FormField label="Email *" htmlFor={emailId}>
            <TextInput
              id={emailId}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </FormField>
          <div className="flex items-center gap-2">
            <Button type="submit" loading={convertLead.isPending} className="flex-1">
              {convertLead.isPending ? 'Conversione…' : 'Conferma conversione'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                onCancelAction();
                setConvertError(null);
              }}
            >
              Annulla
            </Button>
          </div>
        </form>
      ) : (
        <button
          onClick={onStartConvert}
          className="inline-flex items-center gap-1.5 text-xs font-semibold min-h-11 px-3 rounded-lg bg-lime-400/10 border border-lime-400/25 text-lime-400 hover:bg-lime-400/15 transition-colors"
        >
          Converti in socio
        </button>
      )}
    </div>
  );
}
