import { Check } from 'lucide-react';
import type { StepKey } from './useEnrollmentWizard';

const STEP_LABELS: Record<StepKey, string> = {
  member: 'Anagrafica',
  subscription: 'Abbonamento',
  payment: 'Pagamento',
  summary: 'Riepilogo',
};

type StepperProps = {
  steps: StepKey[];
  current: StepKey;
};

/**
 * Pill indicator for the wizard's visible steps — lime = active, check = already passed.
 * At 375px four full-word pills sharing the row equally don't fit — even the active step's own
 * word got clipped to "AN…". Below `sm`, only the ACTIVE step is a wide labelled pill (it takes
 * the row's spare width via `flex-1`); the others collapse to a small fixed-size circle with
 * just a number (or a check once done) — illegible labels at that width are worse than no label.
 */
export function Stepper({ steps, current }: StepperProps) {
  const currentIndex = steps.indexOf(current);

  return (
    <div className="flex items-center gap-2 mb-8" role="list" aria-label="Passaggi iscrizione">
      {steps.map((step, i) => {
        const done = i < currentIndex;
        const active = step === current;
        return (
          <div key={step} className={`flex items-center gap-2 min-w-0 ${active ? 'flex-1' : 'flex-none sm:flex-1'}`}>
            {active ? (
              <span
                role="listitem"
                aria-current="step"
                className="flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-xl text-xs sm:text-sm font-semibold uppercase tracking-wide whitespace-nowrap bg-lime-400 text-[#0b0b0d] w-full"
              >
                <span
                  aria-hidden="true"
                  className="sm:hidden flex items-center justify-center w-4 h-4 rounded-full bg-[#0b0b0d]/15 text-[10px] leading-none shrink-0"
                >
                  {i + 1}
                </span>
                <span className="truncate">{STEP_LABELS[step]}</span>
              </span>
            ) : (
              <span
                role="listitem"
                className={`flex items-center justify-center shrink-0 gap-1.5 rounded-full sm:rounded-xl w-9 h-9 sm:w-full sm:h-auto sm:px-3 sm:py-2.5 text-xs sm:text-sm font-semibold uppercase tracking-wide whitespace-nowrap transition-colors ${
                  done ? 'bg-lime-400/15 text-lime-400' : 'bg-zinc-800 text-zinc-500'
                }`}
              >
                {done ? (
                  <Check className="w-3.5 h-3.5 shrink-0" />
                ) : (
                  <span className="sm:hidden text-[11px]">{i + 1}</span>
                )}
                <span className="hidden sm:inline truncate">{STEP_LABELS[step]}</span>
              </span>
            )}
            {i < steps.length - 1 && (
              <div className={`h-px w-3 sm:w-6 shrink-0 ${done ? 'bg-lime-400/40' : 'bg-zinc-800'}`} />
            )}
          </div>
        );
      })}
    </div>
  );
}
