import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { UserPlus, ShieldCheck, Copy, Check, Power, Pencil, KeyRound, X, AlertCircle } from 'lucide-react';
import { useAuthStore } from '../../stores/authStore';
import { getApiErrorMessage } from '../../utils/apiError';
import {
  useInstructors,
  useStaff,
  useTrainers,
  useCreateInstructor,
  useCreateStaff,
  useCreateTrainer,
  useUpdateUser,
  useResetUserPassword,
  useToggleUserStatus,
} from './useUsers';
import { useGyms } from '../gyms/useGyms';
import { Badge } from '../../components/ui/Badge';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { FormField } from '../../components/ui/FormField';
import { TextInput } from '../../components/ui/TextInput';
import { Select } from '../../components/ui/Select';
import { Button } from '../../components/ui/Button';
import { IconButton } from '../../components/ui/IconButton';
import type { UserInviteResponse, UserResponse } from '../../types/user';

type GymManagerTab = 'staff' | 'trainers';

export function UsersPage() {
  const role = useAuthStore((s) => s.user?.role);
  const isAdmin = role === 'ADMIN';
  const location = useLocation();
  const navigate = useNavigate();

  // GYM_MANAGER manages two separate rosters (staff / trainers) via a tab switch;
  // ADMIN only ever manages instructors, so it has no tab. The active tab is derived
  // from the route (/users vs /users/trainers) so the URL and the UI stay in sync.
  const tab: GymManagerTab = location.pathname.startsWith('/users/trainers') ? 'trainers' : 'staff';
  const scope = isAdmin ? 'instructors' : tab;

  const instructorsQ = useInstructors(isAdmin);
  const staffQ = useStaff(!isAdmin && tab === 'staff');
  const trainersQ = useTrainers(!isAdmin && tab === 'trainers');
  const list = isAdmin ? instructorsQ : tab === 'staff' ? staffQ : trainersQ;

  // Load gyms only for ADMIN (needed for the instructor creation select)
  const gymsQ = useGyms(isAdmin);

  const createInstructor = useCreateInstructor();
  const createStaff = useCreateStaff();
  const createTrainer = useCreateTrainer();
  const updateUser = useUpdateUser(scope);
  const resetPassword = useResetUserPassword(scope);
  const toggle = useToggleUserStatus(scope);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [email, setEmail] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [gymId, setGymId] = useState<number | ''>('');
  const [error, setError] = useState('');
  const [invite, setInvite] = useState<UserInviteResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [pendingRowAction, setPendingRowAction] = useState<{ id: number; kind: 'deactivate' | 'reset' } | null>(null);

  const editing = editingId !== null;
  const title = isAdmin ? 'Capipalestra' : tab === 'staff' ? 'Staff' : 'Insegnanti';
  const subtitle = isAdmin
    ? 'Crea un capopalestra assegnandolo a una palestra esistente.'
    : tab === 'staff'
      ? 'Crea un membro dello staff della tua palestra.'
      : 'Crea un insegnante per i corsi della tua palestra.';

  const submitting =
    createInstructor.isPending || createStaff.isPending || createTrainer.isPending || updateUser.isPending;

  const resetForm = () => {
    setEditingId(null);
    setEmail('');
    setFirstName('');
    setLastName('');
    setGymId('');
  };

  const switchTab = (next: GymManagerTab) => {
    if (next !== tab) {
      navigate(next === 'trainers' ? '/users/trainers' : '/users');
    }
    resetForm();
    setError('');
    setInvite(null);
  };

  const startEdit = (u: UserResponse) => {
    setEditingId(u.id);
    setFirstName(u.firstName ?? '');
    setLastName(u.lastName ?? '');
    setEmail(u.username);
    setGymId('');
    setInvite(null);
    setError('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setInvite(null);
    try {
      if (editing) {
        await updateUser.mutateAsync({ id: editingId!, data: { firstName, lastName, email } });
        resetForm();
      } else {
        if (isAdmin) {
          if (gymId === '') {
            setError('Seleziona una palestra prima di creare il capopalestra.');
            return;
          }
          const result = await createInstructor.mutateAsync({
            email,
            firstName,
            lastName,
            gymId: gymId as number,
          });
          setInvite(result);
        } else if (tab === 'staff') {
          const result = await createStaff.mutateAsync({ email, firstName, lastName });
          setInvite(result);
        } else {
          const result = await createTrainer.mutateAsync({ email, firstName, lastName });
          setInvite(result);
        }
        resetForm();
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const handleReset = async (u: UserResponse) => {
    setError('');
    setInvite(null);
    try {
      const result = await resetPassword.mutateAsync(u.id);
      setInvite(result);
      setPendingRowAction(null);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (err) {
      setError(getApiErrorMessage(err));
      setPendingRowAction(null);
    }
  };

  // Deactivating and resetting a password are both disruptive (lock the account out / invalidate
  // the current one) — confirm both; reactivating isn't, so it stays a direct action.
  const requestToggle = (u: UserResponse) => {
    if (u.active) {
      setPendingRowAction({ id: u.id, kind: 'deactivate' });
      return;
    }
    setError('');
    toggle.mutate(
      { id: u.id, active: false },
      { onError: (err) => setError(getApiErrorMessage(err)) },
    );
  };

  const confirmRowAction = (u: UserResponse) => {
    if (pendingRowAction?.kind === 'reset') {
      handleReset(u);
    } else {
      setError('');
      toggle.mutate(
        { id: u.id, active: true },
        {
          onSettled: () => setPendingRowAction(null),
          onError: (err) => setError(getApiErrorMessage(err)),
        },
      );
    }
  };

  const copyLink = () => {
    if (!invite) return;
    navigator.clipboard.writeText(invite.inviteLink);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const hasNoGyms = isAdmin && !gymsQ.isLoading && (gymsQ.data?.length ?? 0) === 0;

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="font-display font-bold text-3xl sm:text-4xl text-zinc-100 uppercase tracking-tight">
          {title}
        </h1>
        <p className="text-zinc-500 text-sm mt-2">{subtitle}</p>
      </div>

      {/* GYM_MANAGER manages two rosters — switch between them here */}
      {!isAdmin && (
        <div className="inline-flex bg-zinc-900 border border-white/5 rounded-xl p-1 gap-0.5 mb-6">
          <button
            type="button"
            onClick={() => switchTab('staff')}
            className={`min-h-11 px-4 rounded-lg text-sm font-semibold transition-colors ${
              tab === 'staff' ? 'bg-lime-400 text-[#0b0b0d]' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Staff
          </button>
          <button
            type="button"
            onClick={() => switchTab('trainers')}
            className={`min-h-11 px-4 rounded-lg text-sm font-semibold transition-colors ${
              tab === 'trainers' ? 'bg-lime-400 text-[#0b0b0d]' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Insegnanti
          </button>
        </div>
      )}

      {/* Warning when admin has no gyms yet */}
      {hasNoGyms && !editing && (
        <div className="flex items-start gap-3 bg-amber-400/10 border border-amber-400/20 rounded-xl p-4 mb-6 text-sm text-amber-300">
          <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
          <span>
            Nessuna palestra disponibile. Per creare un capopalestra è necessario prima{' '}
            <a href="/gyms" className="underline hover:text-amber-200 transition-colors">
              creare una palestra
            </a>
            .
          </span>
        </div>
      )}

      {/* Create / edit form */}
      <form onSubmit={handleSubmit} className="bg-zinc-800 rounded-2xl p-5 mb-8 flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-zinc-300 font-semibold text-sm">
            {editing ? <Pencil className="w-4 h-4 text-lime-400" /> : <UserPlus className="w-4 h-4 text-lime-400" />}
            {editing
              ? 'Modifica account'
              : `Nuovo ${isAdmin ? 'capopalestra' : tab === 'staff' ? 'membro staff' : 'insegnante'}`}
          </div>
          {editing && (
            <button
              type="button"
              onClick={resetForm}
              className="flex items-center gap-1 text-zinc-500 hover:text-zinc-300 text-xs"
            >
              <X className="w-3.5 h-3.5" /> Annulla
            </button>
          )}
        </div>

        <div className="flex flex-col sm:flex-row gap-3">
          <FormField label="Nome *" htmlFor="userFirstName">
            <TextInput id="userFirstName" required value={firstName} onChange={(e) => setFirstName(e.target.value)} />
          </FormField>
          <FormField label="Cognome *" htmlFor="userLastName">
            <TextInput id="userLastName" required value={lastName} onChange={(e) => setLastName(e.target.value)} />
          </FormField>
        </div>

        <FormField label="Email * (sarà lo username)" htmlFor="userEmail">
          <TextInput id="userEmail" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </FormField>

        {/* Gym select — only for ADMIN creating a new instructor */}
        {isAdmin && !editing && (
          <FormField label="Palestra *" htmlFor="gymSelect">
            {gymsQ.isLoading ? (
              <div className="bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-500 text-sm">
                Caricamento palestre…
              </div>
            ) : hasNoGyms ? (
              <div className="bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-600 text-sm italic">
                Nessuna palestra disponibile
              </div>
            ) : (
              <Select
                id="gymSelect"
                required
                value={gymId}
                onChange={(e) => setGymId(e.target.value === '' ? '' : Number(e.target.value))}
              >
                <option value="" disabled>
                  Seleziona una palestra…
                </option>
                {gymsQ.data?.map((gym) => (
                  <option key={gym.id} value={gym.id}>
                    {gym.name}{gym.city ? ` — ${gym.city}` : ''}
                    {gym.instructorId ? ' (già assegnata)' : ''}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
        )}

        {error && <ErrorMessage message={error} />}

        <Button type="submit" loading={submitting} disabled={isAdmin && !editing && hasNoGyms} className="self-start">
          {submitting ? 'Salvataggio…' : editing ? 'Salva modifiche' : 'Crea e invia invito'}
        </Button>

        {invite && (
          <div className="bg-lime-400/10 border border-lime-400/20 rounded-xl p-4 text-sm">
            <p className="text-lime-300 font-semibold mb-2">
              Link impostazione password per {invite.username}.
            </p>
            <p className="text-zinc-400 mb-2">
              È stata inviata un&apos;email con il link (valido 48 ore). Se non arriva, condividi tu
              questo link:
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 bg-zinc-900 rounded-lg px-3 py-2 text-zinc-300 text-xs break-all">
                {invite.inviteLink}
              </code>
              <IconButton
                onClick={copyLink}
                className="shrink-0 bg-zinc-700 hover:bg-zinc-600 text-zinc-200 rounded-lg"
                aria-label="Copia link invito"
                title="Copia"
              >
                {copied ? <Check className="w-4 h-4 text-lime-400" /> : <Copy className="w-4 h-4" />}
              </IconButton>
            </div>
          </div>
        )}
      </form>

      {/* List */}
      {list.isLoading && <LoadingSpinner />}
      {list.isError && <ErrorMessage message="Errore nel caricamento." />}

      {list.data && (
        <div className="flex flex-col gap-3">
          {list.data.length === 0 ? (
            <p className="text-zinc-500 text-[15px] text-center py-10">
              {isAdmin
                ? 'Nessun capopalestra ancora.'
                : tab === 'staff'
                  ? 'Nessun membro staff ancora.'
                  : 'Nessun insegnante ancora.'}
            </p>
          ) : (
            list.data.map((u) => (
              <div key={u.id} className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 bg-zinc-800 rounded-2xl p-4">
                <div className="flex items-center gap-4 flex-1 min-w-0">
                  <div className="w-10 h-10 bg-lime-400/10 rounded-xl flex items-center justify-center shrink-0">
                    <ShieldCheck className="w-5 h-5 text-lime-400" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-zinc-100 font-semibold text-sm truncate">
                      {[u.firstName, u.lastName].filter(Boolean).join(' ') || u.username}
                    </p>
                    <p className="text-zinc-500 text-xs truncate">
                      {u.username}
                      {isAdmin && u.gymName ? ` · ${u.gymName}` : ''}
                      {u.mustChangePassword ? ' · invito in sospeso' : ''}
                    </p>
                  </div>
                </div>
                {pendingRowAction?.id === u.id ? (
                  <div className="flex items-center gap-2 shrink-0 self-end sm:self-auto flex-wrap justify-end">
                    <span className={`text-xs ${pendingRowAction.kind === 'deactivate' ? 'text-red-400' : 'text-amber-400'}`}>
                      {pendingRowAction.kind === 'deactivate' ? 'Disattivare l\'account?' : 'Reinviare l\'invito password?'}
                    </span>
                    <button
                      onClick={() => confirmRowAction(u)}
                      disabled={toggle.isPending || resetPassword.isPending}
                      className={`font-bold px-3 py-1.5 rounded-lg text-xs transition-colors disabled:opacity-50 ${
                        pendingRowAction.kind === 'deactivate'
                          ? 'bg-red-500 hover:bg-red-400 text-white'
                          : 'bg-amber-400 hover:bg-amber-300 text-[#0b0b0d]'
                      }`}
                    >
                      {pendingRowAction.kind === 'deactivate'
                        ? (toggle.isPending ? 'Disattivazione…' : 'Disattiva')
                        : (resetPassword.isPending ? 'Invio…' : 'Conferma')}
                    </button>
                    <button
                      onClick={() => setPendingRowAction(null)}
                      className="text-zinc-500 hover:text-zinc-300 text-xs px-3 py-1.5 rounded-lg border border-zinc-700 transition-colors"
                    >
                      Annulla
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 shrink-0 self-end sm:self-auto">
                    <Badge variant={u.active ? 'lime' : 'zinc'}>
                      {u.active ? 'Attivo' : 'Disattivato'}
                    </Badge>
                    <IconButton
                      onClick={() => startEdit(u)}
                      className="hover:text-lime-400"
                      aria-label={`Modifica ${u.username}`}
                      title="Modifica"
                    >
                      <Pencil className="w-4 h-4" />
                    </IconButton>
                    <IconButton
                      onClick={() => setPendingRowAction({ id: u.id, kind: 'reset' })}
                      disabled={resetPassword.isPending}
                      className="hover:text-amber-400"
                      aria-label={`Reset password ${u.username}`}
                      title="Reset password"
                    >
                      <KeyRound className="w-4 h-4" />
                    </IconButton>
                    <IconButton
                      onClick={() => requestToggle(u)}
                      disabled={toggle.isPending}
                      className="hover:text-red-400"
                      aria-label={`${u.active ? 'Disattiva' : 'Riattiva'} ${u.username}`}
                      title={u.active ? 'Disattiva' : 'Riattiva'}
                    >
                      <Power className="w-4 h-4" />
                    </IconButton>
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
