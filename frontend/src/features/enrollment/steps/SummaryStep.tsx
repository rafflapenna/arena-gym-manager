import { Link } from 'react-router-dom';
import { CheckCircle2, UserPlus } from 'lucide-react';
import { subscriptionLabel } from '../../../utils/subscriptionUtils';
import { formatDayMonthYear } from '../../../utils/dateTime';
import { formatCurrency } from '../../../utils/currency';
import { lookupLabel } from '../../../utils/lookupLabels';
import type { MemberResponse, PaymentResponse, SubscriptionResponse } from '../../../types';

type SummaryStepProps = {
  member: MemberResponse;
  subscription: SubscriptionResponse | null;
  payment: PaymentResponse | null;
  onEnrollAnother: () => void;
};

/** Step 4 — pure recap of what was already committed step by step; no API calls here. */
export function SummaryStep({ member, subscription, payment, onEnrollAnother }: SummaryStepProps) {
  const pi = member.personalInfo;

  return (
    <div className="flex flex-col gap-5">
      <div className="bg-zinc-800 rounded-2xl p-6 flex flex-col gap-5">
        <div className="flex items-center gap-3">
          <CheckCircle2 className="w-8 h-8 text-lime-400 shrink-0" />
          <div>
            <p className="text-zinc-100 font-display font-bold text-xl uppercase tracking-tight">Fatto!</p>
            <p className="text-zinc-500 text-sm">Riepilogo dell&apos;iscrizione.</p>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <p className="text-zinc-500 text-xs font-semibold uppercase tracking-wide">Socio</p>
          <p className="text-zinc-100 text-sm font-semibold">{pi.firstName} {pi.lastName}</p>
          <p className="text-zinc-500 text-xs">{pi.email}</p>
        </div>

        <div className="flex flex-col gap-1">
          <p className="text-zinc-500 text-xs font-semibold uppercase tracking-wide">Abbonamento</p>
          {subscription ? (
            <>
              <p className="text-zinc-100 text-sm font-semibold">{subscriptionLabel(subscription)}</p>
              <p className="text-zinc-500 text-xs">
                {formatDayMonthYear(subscription.startDate)} → {formatDayMonthYear(subscription.endDate)} ·{' '}
                <span className="text-cyan-400 font-semibold">{formatCurrency(subscription.price)}</span>
              </p>
            </>
          ) : (
            <p className="text-zinc-500 text-sm">Nessun abbonamento creato.</p>
          )}
        </div>

        {subscription && (
          <div className="flex flex-col gap-1">
            <p className="text-zinc-500 text-xs font-semibold uppercase tracking-wide">Pagamento</p>
            {payment ? (
              <>
                <p className="text-zinc-100 text-sm font-semibold">
                  {lookupLabel(payment.paymentMethod)} · {formatDayMonthYear(payment.paymentDate)}
                </p>
                <p className="text-cyan-400 text-xs font-semibold">{formatCurrency(payment.amount)}</p>
              </>
            ) : (
              <span className="self-start inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-400/13 text-amber-400 text-xs font-semibold mt-0.5">
                Da incassare
              </span>
            )}
          </div>
        )}
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <Link
          to={`/members/${member.id}`}
          className="flex-1 flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-4 rounded-2xl hover:bg-lime-300 transition-colors"
        >
          Vai alla scheda socio
        </Link>
        <button
          type="button"
          onClick={onEnrollAnother}
          className="flex-1 flex items-center justify-center gap-2 bg-zinc-800 hover:bg-zinc-700/80 text-zinc-100 font-semibold py-4 rounded-2xl transition-colors"
        >
          <UserPlus className="w-4 h-4" />
          Iscrivi un altro
        </button>
      </div>
    </div>
  );
}
