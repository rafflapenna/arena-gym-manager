export interface BeltGradeRequest {
  code: string;
  label: string;
}

export interface PaymentMethodRequest {
  code: string;
  label: string;
}

export interface SubscriptionTypeRequest {
  code: string;
  label: string;
  months: number;
  /** List price for this type; omitted/null leaves it unset. */
  defaultPrice?: number | null;
}
