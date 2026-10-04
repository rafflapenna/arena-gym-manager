import { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ChevronLeft, Check } from 'lucide-react';
import { useMember } from '../members/useMembers';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { subscriptionLabel } from '../../utils/subscriptionUtils';
import { formatDayMonthYear } from '../../utils/dateTime';
import { formatCurrency } from '../../utils/currency';
import { lookupLabel } from '../../utils/lookupLabels';
import { Stepper } from './Stepper';
import { useEnrollmentWizard, type StepKey } from './useEnrollmentWizard';
import { MemberStep } from './steps/MemberStep';
import { SubscriptionStep } from './steps/SubscriptionStep';
import { PaymentStep } from './steps/PaymentStep';
import { SummaryStep } from './steps/SummaryStep';
import type { MemberResponse, PaymentResponse, SubscriptionResponse } from '../../types';

const FULL_STEPS: StepKey[] = ['member', 'subscription', 'payment', 'summary'];
const FROM_MEMBER_STEPS: StepKey[] = ['subscription', 'payment', 'summary'];

/**
 * "Iscrizione & Incasso" wizard — commit-per-step (see docs/DESIGN-wizard-iscrizione.it.md).
 * `/enroll` starts fresh at the member step; `/members/:memberId/enroll` starts at the
 * subscription step for an already-known member (rinnovo / primo abbonamento).
 */
export function EnrollmentWizardPage() {
  const { memberId: memberIdParam } = useParams<{ memberId?: string }>();
  const navigate = useNavigate();
  const startFromMember = !!memberIdParam;
  const mid = memberIdParam ? Number(memberIdParam) : 0;

  const { data: existingMember, isLoading: loadingMember, isError: memberError } = useMember(
    startFromMember ? mid : 0
  );

  const { state, dispatch, step } = useEnrollmentWizard();

  // Seed the wizard with the known member exactly once — a later refetch of the same query
  // must not reset subscription/payment progress already committed in this session.
  useEffect(() => {
    if (startFromMember && existingMember && !state.member) {
      dispatch({ type: 'SET_MEMBER', member: existingMember });
    }
  }, [startFromMember, existingMember, state.member, dispatch]);

  const visibleSteps = startFromMember ? FROM_MEMBER_STEPS : FULL_STEPS;

  const backLink = startFromMember ? `/members/${mid}` : '/members';
  const backLabel =
    startFromMember && existingMember
      ? `${existingMember.personalInfo.firstName} ${existingMember.personalInfo.lastName}`
      : 'Soci';

  const handleEnrollAnother = () => {
    dispatch({ type: 'RESET' });
    if (startFromMember) navigate('/enroll');
  };

  if (startFromMember && loadingMember) return <LoadingSpinner />;
  if (startFromMember && (memberError || !existingMember)) {
    return <div className="p-8"><ErrorMessage message="Socio non trovato." /></div>;
  }

  return (
    <div className="px-5 py-8 md:px-8 md:py-10 max-w-2xl mx-auto">
      <Link
        to={backLink}
        className="inline-flex items-center gap-1.5 -ml-2 px-2 min-h-11 text-zinc-500 hover:text-zinc-300 text-sm mb-4 transition-colors"
      >
        <ChevronLeft className="w-4 h-4" />
        {backLabel}
      </Link>

      <h1 className="font-display font-bold text-4xl text-zinc-100 uppercase tracking-tight mb-8">
        {startFromMember ? 'Nuovo Abbonamento' : 'Iscrizione'}
      </h1>

      <Stepper steps={visibleSteps} current={step} />

      <div className="flex flex-col gap-5">
        {state.member && step !== 'member' && <MemberSummaryCard member={state.member} />}

        {step === 'member' && (
          <MemberStep onCommit={(member) => dispatch({ type: 'SET_MEMBER', member })} />
        )}

        {state.subscription && step !== 'subscription' && (
          <SubscriptionSummaryCard subscription={state.subscription} />
        )}
        {state.subscriptionSkipped && <SkippedCard label="Abbonamento saltato" />}

        {step === 'subscription' && state.member && (
          <SubscriptionStep
            member={state.member}
            onCommit={(subscription) => dispatch({ type: 'COMMIT_SUBSCRIPTION', subscription })}
            onSkip={() => dispatch({ type: 'SKIP_SUBSCRIPTION' })}
          />
        )}

        {state.payment && step !== 'payment' && <PaymentSummaryCard payment={state.payment} />}
        {state.paymentSkipped && <SkippedCard label="Pagamento da incassare" />}

        {step === 'payment' && state.subscription && (
          <PaymentStep
            subscription={state.subscription}
            onCommit={(payment) => dispatch({ type: 'COMMIT_PAYMENT', payment })}
            onSkip={() => dispatch({ type: 'SKIP_PAYMENT' })}
          />
        )}

        {step === 'summary' && state.member && (
          <SummaryStep
            member={state.member}
            subscription={state.subscription}
            payment={state.payment}
            onEnrollAnother={handleEnrollAnother}
          />
        )}
      </div>
    </div>
  );
}

function CommittedCardShell({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="bg-zinc-800/60 border border-lime-400/20 rounded-2xl p-4 flex items-center gap-3">
      <span className="w-8 h-8 rounded-full bg-lime-400/15 text-lime-400 flex items-center justify-center shrink-0">
        <Check className="w-4 h-4" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-zinc-500 text-xs font-semibold uppercase tracking-wide">{label}</p>
        {children}
      </div>
    </div>
  );
}

function MemberSummaryCard({ member }: { member: MemberResponse }) {
  const pi = member.personalInfo;
  return (
    <CommittedCardShell label="Socio">
      <p className="text-zinc-100 text-sm font-semibold truncate">{pi.firstName} {pi.lastName}</p>
      <p className="text-zinc-500 text-xs truncate">{pi.email}</p>
    </CommittedCardShell>
  );
}

function SubscriptionSummaryCard({ subscription }: { subscription: SubscriptionResponse }) {
  return (
    <CommittedCardShell label="Abbonamento">
      <p className="text-zinc-100 text-sm font-semibold truncate">{subscriptionLabel(subscription)}</p>
      <p className="text-zinc-500 text-xs">
        {formatDayMonthYear(subscription.startDate)} → {formatDayMonthYear(subscription.endDate)} ·{' '}
        <span className="text-cyan-400 font-semibold">{formatCurrency(subscription.price)}</span>
      </p>
    </CommittedCardShell>
  );
}

function PaymentSummaryCard({ payment }: { payment: PaymentResponse }) {
  return (
    <CommittedCardShell label="Pagamento">
      <p className="text-zinc-100 text-sm font-semibold truncate">
        {lookupLabel(payment.paymentMethod)} · {formatDayMonthYear(payment.paymentDate)}
      </p>
      <p className="text-cyan-400 text-xs font-semibold">{formatCurrency(payment.amount)}</p>
    </CommittedCardShell>
  );
}

function SkippedCard({ label }: { label: string }) {
  return (
    <div className="bg-zinc-800/40 border border-white/5 rounded-2xl p-4 flex items-center gap-3">
      <span className="w-8 h-8 rounded-full bg-white/7 text-zinc-500 flex items-center justify-center shrink-0 text-xs font-bold">
        —
      </span>
      <p className="text-zinc-400 text-sm">{label}</p>
    </div>
  );
}
