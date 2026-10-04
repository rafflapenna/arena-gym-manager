import { useReducer } from 'react';
import type { MemberResponse, PaymentResponse, SubscriptionResponse } from '../../types';

export type StepKey = 'member' | 'subscription' | 'payment' | 'summary';

export interface WizardState {
  member: MemberResponse | null;
  subscription: SubscriptionResponse | null;
  subscriptionSkipped: boolean;
  payment: PaymentResponse | null;
  paymentSkipped: boolean;
}

export type WizardAction =
  | { type: 'SET_MEMBER'; member: MemberResponse }
  | { type: 'COMMIT_SUBSCRIPTION'; subscription: SubscriptionResponse }
  | { type: 'SKIP_SUBSCRIPTION' }
  | { type: 'COMMIT_PAYMENT'; payment: PaymentResponse }
  | { type: 'SKIP_PAYMENT' }
  | { type: 'RESET' };

const initialState: WizardState = {
  member: null,
  subscription: null,
  subscriptionSkipped: false,
  payment: null,
  paymentSkipped: false,
};

function reducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'SET_MEMBER':
      // Setting the member (re)starts the wizard: any subscription/payment progress belongs
      // to a previous member and must not leak into the new one.
      return { ...initialState, member: action.member };
    case 'COMMIT_SUBSCRIPTION':
      return { ...state, subscription: action.subscription, subscriptionSkipped: false };
    case 'SKIP_SUBSCRIPTION':
      return { ...state, subscriptionSkipped: true };
    case 'COMMIT_PAYMENT':
      return { ...state, payment: action.payment, paymentSkipped: false };
    case 'SKIP_PAYMENT':
      return { ...state, paymentSkipped: true };
    case 'RESET':
      return initialState;
    default:
      return state;
  }
}

/**
 * The active step is derived purely from what's been committed/skipped so far (commit-per-step
 * model, see docs/DESIGN-wizard-iscrizione.it.md §2) — there's no separate "current step" state
 * to keep in sync. Skipping the subscription ends the wizard at the summary immediately: there's
 * nothing left to pay for.
 */
function deriveStep(state: WizardState): StepKey {
  if (!state.member) return 'member';
  if (!state.subscription && !state.subscriptionSkipped) return 'subscription';
  if (state.subscriptionSkipped) return 'summary';
  if (!state.payment && !state.paymentSkipped) return 'payment';
  return 'summary';
}

export function useEnrollmentWizard() {
  const [state, dispatch] = useReducer(reducer, initialState);
  return { state, dispatch, step: deriveStep(state) };
}
