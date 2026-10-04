import { useState } from 'react';
import { useParams, useNavigate, Link, Navigate } from 'react-router-dom';
import { type AxiosError } from 'axios';
import { Pencil, UserX, UserCheck, Trash2, ChevronLeft, Smartphone, Plus, QrCode, RotateCw } from 'lucide-react';
import { getApiErrorMessage } from '../../utils/apiError';
import { useMemberRewards } from '../referrals';
import { MemberFeesSection } from '../fees';
import { useMember, useToggleMemberStatus, useDeleteMember, useCreateMemberAccount } from './useMembers';
import { useSubscriptionsByMember, useCancelSubscription } from '../subscriptions/useSubscriptions';
import { usePaymentsByMember } from '../payments/usePayments';
import { useMemberCheckIns, CheckInButton } from '../checkins';
import { useMemberQr, useRotateMemberQr } from '../qr';
import { MemberDocumentsSection } from '../documents';
import { QrCodeDisplay } from '../../components/qr/QrCodeDisplay';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Button } from '../../components/ui/Button';
import {
  subscriptionLabel,
  subscriptionBadgeVariant,
  pickCurrentSubscription,
  memberStatus,
  daysRemaining,
  memberStatusDotClass,
  memberStatusPillClass,
  memberStatusAvatarClass,
  memberStatusLabel,
  needsRenewal,
} from '../../utils/subscriptionUtils';
import { formatDateTime, formatDayMonthYear } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/currency';
import { lookupLabel } from '../../utils/lookupLabels';
import { usePermissions } from '../../hooks/usePermissions';

const subPillClasses: Record<'lime' | 'amber' | 'zinc', string> = {
  lime: 'bg-lime-400/12 text-lime-400',
  amber: 'bg-amber-400/13 text-amber-400',
  zinc: 'bg-white/7 text-zinc-300',
};
const subDotClasses: Record<'lime' | 'amber' | 'zinc', string> = {
  lime: 'bg-lime-400',
  amber: 'bg-amber-400',
  zinc: 'bg-zinc-500',
};

function InfoRow({ label, value, href }: { label: string; value: string; href?: string }) {
  return (
    <div className="min-w-0">
      <p className="text-[#8b8b94] text-[13px] font-semibold mb-[3px]">{label}</p>
      {href ? (
        <a
          href={href}
          className="text-lime-400 text-[15.5px] hover:underline"
          style={{ overflowWrap: 'anywhere' }}
        >
          {value}
        </a>
      ) : (
        <p className="text-zinc-100 text-[15.5px]" style={{ overflowWrap: 'anywhere' }}>{value}</p>
      )}
    </div>
  );
}

const ACTION_BTN =
  'inline-flex items-center gap-2 text-[14.5px] font-semibold py-3 px-5 rounded-xl bg-[#1c1c1f] border border-white/9 transition-colors';

export function MemberDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const memberId = Number(id);

  const { data: member, isLoading, isError } = useMember(memberId);
  const { data: rewards } = useMemberRewards(memberId);
  const { data: subscriptions = [], isError: subsError } = useSubscriptionsByMember(memberId);
  const { data: payments = [], isError: paymentsError } = usePaymentsByMember(memberId);
  const { data: checkIns = [], isError: checkInsError } = useMemberCheckIns(memberId);
  const toggleStatus = useToggleMemberStatus();
  const deleteMember = useDeleteMember();
  const createAccount = useCreateMemberAccount();
  const cancelSubscription = useCancelSubscription();
  const { canCreateMemberAccount } = usePermissions();
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [inviteSent, setInviteSent] = useState(false);
  const [pendingAction, setPendingAction] = useState<'deactivate' | 'delete' | null>(null);
  const [cancelingSubId, setCancelingSubId] = useState<number | null>(null);
  const [cancelSubError, setCancelSubError] = useState<string | null>(null);

  // Fix 5: redirect se id non è un numero valido
  if (!id || isNaN(memberId)) {
    return <Navigate to="/members" replace />;
  }

  const handleCreateAccount = () => {
    setMutationError(null);
    setInviteSent(false);
    createAccount.mutate(memberId, {
      onSuccess: () => setInviteSent(true),
      onError: (err) => {
        const axErr = err as AxiosError<{ detail?: string }>;
        if (axErr.response?.status === 409) {
          setMutationError('Questo socio ha già un account app.');
        } else if (axErr.response?.status === 403) {
          setMutationError('Non hai i permessi per creare account socio.');
        } else {
          setMutationError(getApiErrorMessage(err));
        }
      },
    });
  };

  const handleToggleStatus = () => {
    // Deactivating is disruptive (blocks check-in/login) — confirm; reactivating isn't.
    if (member?.active) {
      setPendingAction('deactivate');
      return;
    }
    setMutationError(null);
    toggleStatus.mutate(
      { id: memberId, active: false },
      { onError: (err) => setMutationError(getApiErrorMessage(err)) }
    );
  };

  const confirmDeactivate = () => {
    setMutationError(null);
    toggleStatus.mutate(
      { id: memberId, active: true },
      {
        onSuccess: () => setPendingAction(null),
        onError: (err) => {
          setMutationError(getApiErrorMessage(err));
          setPendingAction(null);
        },
      }
    );
  };

  const confirmCancelSubscription = (subId: number) => {
    setCancelSubError(null);
    cancelSubscription.mutate(subId, {
      onSuccess: () => setCancelingSubId(null),
      onError: (err) => {
        setCancelSubError(getApiErrorMessage(err));
        setCancelingSubId(null);
      },
    });
  };

  const confirmDelete = () => {
    setMutationError(null);
    deleteMember.mutate(memberId, {
      onSuccess: () => navigate('/members'),
      onError: (err) => {
        setMutationError(getApiErrorMessage(err));
        setPendingAction(null);
      },
    });
  };

  if (isLoading) return <LoadingSpinner />;
  if (isError || !member) return <div className="p-8"><ErrorMessage message="Socio non trovato." /></div>;

  const pi = member.personalInfo;
  const currentSub = pickCurrentSubscription(subscriptions);
  const status = memberStatus(member, currentSub);
  const days = daysRemaining(currentSub?.endDate);
  const showRenew = needsRenewal(status, days);

  return (
    <div className="px-5 py-8 md:px-10 md:py-10 max-w-4xl mx-auto font-body">
      <Link
        to="/members"
        className="inline-flex items-center gap-[7px] -ml-2 px-2 min-h-11 text-zinc-400 hover:text-zinc-200 text-[14.5px] font-semibold mb-3 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" strokeWidth={2.2} />
        Torna a Soci
      </Link>

      <div className="flex items-center gap-4 mb-5">
        <div className={`w-[60px] h-[60px] rounded-2xl flex items-center justify-center shrink-0 font-display font-bold text-[22px] ${memberStatusAvatarClass[status]}`}>
          {pi.firstName[0]}{pi.lastName[0]}
        </div>
        <div className="min-w-0">
          <h1 className="font-display font-bold text-[34px] text-zinc-100 uppercase leading-none truncate">
            {pi.firstName} {pi.lastName}
          </h1>
          <div className="flex items-center gap-2.5 mt-[7px] flex-wrap">
            <span className={`inline-flex items-center gap-[7px] px-[13px] py-[6px] rounded-full text-[13px] font-semibold ${memberStatusPillClass[status]}`}>
              <span className={`w-[7px] h-[7px] rounded-full ${memberStatusDotClass[status]}`} />
              {memberStatusLabel(status, currentSub)}
            </span>
            {pi.beltGrade && <span className="text-zinc-400 text-sm">{pi.beltGrade.label}</span>}
          </div>
        </div>
      </div>

      <div className="flex gap-2.5 mb-6 flex-wrap">
        {showRenew && (
          <Link
            to={`/members/${memberId}/enroll`}
            className="inline-flex items-center gap-2 text-[14.5px] font-bold py-3 px-5 rounded-xl bg-lime-400 text-[#0b0b0d] active:scale-[0.98] transition-transform duration-150"
          >
            <Plus className="w-4 h-4" strokeWidth={2.2} />
            Rinnova abbonamento
          </Link>
        )}
        <Link to={`/members/${memberId}/edit`} className={`${ACTION_BTN} text-zinc-100`}>
          <Pencil className="w-[15px] h-[15px]" strokeWidth={1.9} />
          Modifica
        </Link>
        {canCreateMemberAccount && (
          <Button variant="secondary" onClick={handleCreateAccount} loading={createAccount.isPending}>
            <Smartphone className="w-[15px] h-[15px]" strokeWidth={1.9} />
            Abilita accesso app
          </Button>
        )}
        <button
          onClick={handleToggleStatus}
          disabled={toggleStatus.isPending}
          className={`${ACTION_BTN} disabled:opacity-50 ${member.active ? 'text-amber-400' : 'text-lime-400'}`}
        >
          {member.active ? <UserX className="w-[15px] h-[15px]" strokeWidth={1.9} /> : <UserCheck className="w-[15px] h-[15px]" strokeWidth={1.9} />}
          {member.active ? 'Disattiva' : 'Attiva'}
        </button>
        <Button variant="danger" onClick={() => setPendingAction('delete')} loading={deleteMember.isPending} className="md:ml-auto">
          <Trash2 className="w-[15px] h-[15px]" strokeWidth={1.9} />
          Elimina
        </Button>
      </div>

      {pendingAction && (
        <div
          className={`mb-5 px-4 py-3 rounded-xl text-[14px] flex items-center justify-between gap-3 flex-wrap border ${
            pendingAction === 'delete'
              ? 'bg-red-400/10 border-red-400/20 text-red-400'
              : 'bg-amber-400/10 border-amber-400/20 text-amber-400'
          }`}
        >
          <span>
            {pendingAction === 'delete'
              ? "Eliminare questo socio? L'operazione non è reversibile."
              : 'Disattivare questo socio? Non potrà più fare check-in né accedere all\'app.'}
          </span>
          <div className="flex gap-2 shrink-0">
            <button
              type="button"
              onClick={pendingAction === 'delete' ? confirmDelete : confirmDeactivate}
              disabled={pendingAction === 'delete' ? deleteMember.isPending : toggleStatus.isPending}
              className={`px-3 py-1.5 rounded-lg font-bold text-[13px] disabled:opacity-50 ${
                pendingAction === 'delete'
                  ? 'bg-red-500 hover:bg-red-400 text-white'
                  : 'bg-amber-400 hover:bg-amber-300 text-[#0b0b0d]'
              }`}
            >
              {pendingAction === 'delete'
                ? (deleteMember.isPending ? 'Eliminazione…' : 'Elimina')
                : (toggleStatus.isPending ? 'Disattivazione…' : 'Disattiva')}
            </button>
            <button
              type="button"
              onClick={() => setPendingAction(null)}
              className="px-3 py-1.5 rounded-lg bg-white/8 text-zinc-200 font-semibold text-[13px]"
            >
              Annulla
            </button>
          </div>
        </div>
      )}

      {mutationError && (
        <div className="mb-5">
          <ErrorMessage message={mutationError} />
        </div>
      )}

      {inviteSent && (
        <div className="mb-5 px-4 py-3 bg-lime-400/10 border border-lime-400/20 rounded-xl text-lime-400 text-sm">
          Email di invito inviata a <strong>{pi.email}</strong>. Il link è valido per 48 ore.
        </div>
      )}

      {/* Sempre visibile, anche con abbonamento scaduto: alla reception serve poter registrare
          l'ingresso comunque, e CheckInButton mostra già gli avvisi non bloccanti del BE. */}
      <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px] mb-4">
        <p className="text-zinc-100 text-[15.5px] font-bold mb-3">Check-in</p>
        <CheckInButton memberId={memberId} />
      </div>

      <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl py-[22px] px-6 mb-4 grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-[18px]">
        <InfoRow label="Email" value={pi.email} href={`mailto:${pi.email}`} />
        {pi.phone && <InfoRow label="Telefono" value={pi.phone} href={`tel:${pi.phone}`} />}
        {pi.fiscalCode && <InfoRow label="Codice fiscale" value={pi.fiscalCode} />}
        {pi.birthDate && (
          <InfoRow
            label="Data di nascita"
            value={pi.birthPlace ? `${formatDayMonthYear(pi.birthDate)} · ${pi.birthPlace}` : formatDayMonthYear(pi.birthDate)}
          />
        )}
        {pi.city && (
          <InfoRow label="Città" value={`${pi.city}${pi.province ? ` (${pi.province})` : ''}`} />
        )}
        <InfoRow label="Iscritto il" value={formatDayMonthYear(member.registrationDate)} />
      </div>

      {member.notes && (
        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-5 mb-4">
          <p className="text-[#8b8b94] text-[13px] font-semibold mb-2">Note</p>
          <p className="text-zinc-300 text-[14.5px] whitespace-pre-line">{member.notes}</p>
        </div>
      )}

      <MemberFeesSection memberId={memberId} />

      {/* Passaparola: chi l'ha portato e gli sconti che ha maturato portando altri. Mostrato
          solo quando c'e' qualcosa da dire, per non aggiungere una card vuota a ogni socio. */}
      {(member.referredBy || (rewards && rewards.length > 0)) && (
        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-5 mb-4">
          <p className="text-[#8b8b94] text-[13px] font-semibold mb-2">Passaparola</p>
          {member.referredBy && (
            <p className="text-zinc-300 text-[14.5px] mb-2">
              Portato da{' '}
              <Link to={`/members/${member.referredBy.id}`} className="text-lime-400 hover:underline">
                {member.referredBy.name}
              </Link>
            </p>
          )}
          {rewards?.map((r) => (
            <p key={r.id} className="text-zinc-400 text-[13.5px]">
              &minus;{r.percent}% per aver portato {r.referredMember.name} &middot;{' '}
              <span className={r.status === 'EARNED' ? 'text-lime-400 font-semibold' : 'text-zinc-500'}>
                {r.status === 'EARNED' ? 'da usare' : r.status === 'USED' ? 'usato' : 'scaduto'}
              </span>
            </p>
          ))}
        </div>
      )}

      {subsError ? (
        <ErrorMessage message="Errore nel caricamento degli abbonamenti." />
      ) : currentSub ? (
        <div className={`bg-[#161618] border rounded-2xl p-[18px] mb-4 ${status === 'expired' ? 'border-amber-400/20' : 'border-white/6'}`}>
          <div className="flex items-center justify-between mb-2.5 gap-3">
            <p className="text-zinc-100 text-base font-bold truncate">{subscriptionLabel(currentSub)}</p>
            <span className={`inline-flex items-center gap-[7px] px-[13px] py-[6px] rounded-full text-[13px] font-semibold whitespace-nowrap ${subPillClasses[subscriptionBadgeVariant(currentSub.status.code)]}`}>
              <span className={`w-[7px] h-[7px] rounded-full ${subDotClasses[subscriptionBadgeVariant(currentSub.status.code)]}`} />
              {lookupLabel(currentSub.status)}
            </span>
          </div>
          <div className="flex items-center gap-4 text-zinc-400 text-[14.5px] flex-wrap">
            <span>{formatDayMonthYear(currentSub.startDate)} → {formatDayMonthYear(currentSub.endDate)}</span>
            <span className="text-cyan-400 font-bold">{formatCurrency(currentSub.price)}</span>
            <div className="md:ml-auto flex items-center gap-2 flex-wrap">
              {status !== 'active' && (
                <Link
                  to={`/members/${memberId}/enroll`}
                  className="inline-flex items-center gap-[7px] bg-amber-400/13 text-amber-400 text-[13.5px] font-bold py-[9px] px-4 rounded-[10px] active:scale-[0.98] transition-transform duration-150"
                >
                  Rinnova ora
                </Link>
              )}
              <Link
                to={`/members/${memberId}/subscriptions/${currentSub.id}/payments/new`}
                className="inline-flex items-center gap-[7px] bg-lime-400 text-[#0b0b0d] text-[13.5px] font-bold py-[9px] px-4 rounded-[10px] active:scale-[0.98] transition-transform duration-150"
              >
                Registra pagamento
              </Link>
              {currentSub.status.code === 'ACTIVE' && cancelingSubId !== currentSub.id && (
                <Button
                  variant="danger"
                  onClick={() => { setCancelSubError(null); setCancelingSubId(currentSub.id); }}
                >
                  Annulla abbonamento
                </Button>
              )}
            </div>
          </div>

          {cancelingSubId === currentSub.id && (
            <div className="mt-3 px-4 py-3 bg-red-400/10 border border-red-400/20 rounded-xl text-red-400 text-[13.5px] flex items-center justify-between gap-3 flex-wrap">
              <span>Annullare l&apos;abbonamento di {pi.firstName} {pi.lastName}?</span>
              <div className="flex gap-2 shrink-0">
                <Button
                  variant="danger"
                  solid
                  onClick={() => confirmCancelSubscription(currentSub.id)}
                  loading={cancelSubscription.isPending}
                >
                  {cancelSubscription.isPending ? 'Annullamento…' : 'Sì, annulla'}
                </Button>
                <Button variant="secondary" onClick={() => setCancelingSubId(null)}>
                  No, tieni
                </Button>
              </div>
            </div>
          )}
          {cancelSubError && (
            <div className="mt-3">
              <ErrorMessage message={cancelSubError} />
            </div>
          )}
        </div>
      ) : (
        <div className="bg-[#161618] border border-white/6 rounded-2xl p-[18px] mb-4 flex items-center justify-between gap-4 flex-wrap">
          <p className="text-zinc-400 text-[14.5px]">Nessun abbonamento per questo socio.</p>
          <Link
            to={`/members/${memberId}/enroll`}
            className="inline-flex items-center gap-[7px] bg-lime-400 text-[#0b0b0d] text-[13.5px] font-bold py-[9px] px-4 rounded-[10px] active:scale-[0.98] transition-transform duration-150"
          >
            Crea abbonamento
          </Link>
        </div>
      )}

      {!subsError && subscriptions.length > 0 && (
        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px] mb-4">
          <p className="text-zinc-100 text-[15.5px] font-bold mb-3">Storico abbonamenti</p>
          <div className="flex flex-col">
            {[...subscriptions]
              .sort((a, b) => b.startDate.localeCompare(a.startDate))
              .map((sub, i, arr) => (
                <div
                  key={sub.id}
                  className={`flex items-center justify-between gap-3 flex-wrap py-3 ${i < arr.length - 1 ? 'border-b border-white/5' : ''}`}
                >
                  <div className="min-w-0">
                    <p className="text-zinc-100 text-[14.5px] font-semibold truncate">{subscriptionLabel(sub)}</p>
                    <p className="text-zinc-500 text-[13px]">
                      {formatDayMonthYear(sub.startDate)} → {formatDayMonthYear(sub.endDate)} ·{' '}
                      <span className="text-cyan-400 font-semibold">{formatCurrency(sub.price)}</span>
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span
                      className={`inline-flex items-center gap-[6px] px-[10px] py-[4px] rounded-full text-[12px] font-semibold whitespace-nowrap ${subPillClasses[subscriptionBadgeVariant(sub.status.code)]}`}
                    >
                      <span className={`w-[6px] h-[6px] rounded-full ${subDotClasses[subscriptionBadgeVariant(sub.status.code)]}`} />
                      {lookupLabel(sub.status)}
                    </span>
                    <Link
                      to={`/members/${memberId}/subscriptions/${sub.id}/payments/new`}
                      className="inline-flex items-center gap-1.5 bg-lime-400/12 text-lime-400 text-[12.5px] font-semibold py-[6px] px-3 rounded-lg hover:bg-lime-400/20 transition-colors whitespace-nowrap"
                    >
                      Registra pagamento
                    </Link>
                  </div>
                </div>
              ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
          <p className="text-zinc-100 text-[15.5px] font-bold mb-3">Ultime presenze</p>
          {checkInsError ? (
            <ErrorMessage message="Errore nel caricamento delle presenze." />
          ) : checkIns.length === 0 ? (
            <p className="text-zinc-500 text-[14.5px] py-1">Nessuna presenza registrata.</p>
          ) : (
            <div className="flex flex-col gap-2.5 text-[14.5px]">
              {checkIns.slice(0, 5).map((c) => (
                <div key={c.id} className="flex items-center justify-between gap-3">
                  <span className="text-zinc-300">{formatDateTime(c.checkInAt)}</span>
                  <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/6 text-zinc-400 text-[13px] font-semibold whitespace-nowrap">
                    {c.source === 'SELF' ? 'App' : c.source === 'QR' ? 'QR' : 'Reception'}
                    {c.outcome === 'WARNING' && <span className="text-amber-400 ml-0.5">·</span>}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px]">
          <p className="text-zinc-100 text-[15.5px] font-bold mb-3">Ultimi pagamenti</p>
          {paymentsError ? (
            <ErrorMessage message="Errore nel caricamento dei pagamenti." />
          ) : payments.length === 0 ? (
            <p className="text-zinc-500 text-[14.5px] py-1">Nessun pagamento registrato.</p>
          ) : (
            <div className="flex flex-col gap-2.5 text-[14.5px]">
              {payments.slice(0, 5).map((p) => (
                <div key={p.id} className="flex items-center justify-between gap-3">
                  <span className="text-zinc-300">{formatDayMonthYear(p.paymentDate)} · {lookupLabel(p.paymentMethod)}</span>
                  <span className="text-cyan-400 font-bold shrink-0">{formatCurrency(p.amount)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <MemberQrSection memberId={memberId} memberFullName={`${pi.firstName} ${pi.lastName}`} />

      <MemberDocumentsSection memberId={memberId} />
    </div>
  );
}

function MemberQrSection({ memberId, memberFullName }: { memberId: number; memberFullName: string }) {
  const [revealed, setRevealed] = useState(false);
  const [confirmingRotate, setConfirmingRotate] = useState(false);
  const { data: qr, isLoading, isError } = useMemberQr(memberId, revealed);
  const rotateQr = useRotateMemberQr(memberId);

  const handleRotate = () => {
    rotateQr.mutate(undefined, { onSuccess: () => setConfirmingRotate(false) });
  };

  return (
    <div className="bg-[#1c1c1f] border border-white/6 rounded-2xl p-[18px] mt-4">
      <div className="flex items-center justify-between gap-3 mb-1">
        <p className="text-zinc-100 text-[15.5px] font-bold flex items-center gap-2">
          <QrCode className="w-[18px] h-[18px] text-lime-400" strokeWidth={1.9} />
          Tessera QR
        </p>
        {revealed && !isLoading && qr && (
          <button
            type="button"
            onClick={() => setConfirmingRotate((v) => !v)}
            className="inline-flex items-center gap-[7px] text-[13.5px] font-semibold text-amber-400 hover:text-amber-300 transition-colors"
          >
            <RotateCw className="w-[14px] h-[14px]" strokeWidth={2} />
            Rigenera
          </button>
        )}
      </div>
      <p className="text-zinc-500 text-[13.5px] mb-4">
        Codice identificativo del socio, da mostrare alla reception per un check-in rapido.
      </p>

      {confirmingRotate && (
        <div className="mb-4 px-4 py-3 bg-amber-400/10 border border-amber-400/20 rounded-xl text-amber-400 text-[13.5px] flex items-center justify-between gap-3 flex-wrap">
          <span>La tessera attuale smetterà di funzionare. Continuare?</span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleRotate}
              disabled={rotateQr.isPending}
              className="min-h-11 px-3 rounded-lg bg-amber-400 text-[#0b0b0d] font-bold text-[13px] disabled:opacity-50"
            >
              {rotateQr.isPending ? 'Rigenero…' : 'Conferma'}
            </button>
            <Button variant="secondary" onClick={() => setConfirmingRotate(false)}>
              Annulla
            </Button>
          </div>
        </div>
      )}

      {rotateQr.isError && (
        <div className="mb-4">
          <ErrorMessage message={getApiErrorMessage(rotateQr.error)} />
        </div>
      )}

      {!revealed ? (
        <Button variant="secondary" onClick={() => setRevealed(true)}>
          <QrCode className="w-[15px] h-[15px]" strokeWidth={1.9} />
          Mostra tessera QR
        </Button>
      ) : isLoading ? (
        <LoadingSpinner />
      ) : isError || !qr ? (
        <ErrorMessage message="Errore nel caricamento della tessera QR." />
      ) : (
        <QrCodeDisplay token={qr.token} memberFullName={memberFullName} />
      )}
    </div>
  );
}
