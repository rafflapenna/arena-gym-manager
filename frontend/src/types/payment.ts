import type { Lookup } from './common';

export interface PaymentRequest {
  amount: number;
  paymentDate: string;
  paymentMethodId: number;
  description?: string | null;
}

export interface PaymentResponse {
  id: number;
  memberId: number;
  memberFullName: string;
  subscriptionId: number;
  amount: number;
  paymentDate: string;
  paymentMethod: Lookup;
  description: string | null;
  createdAt: string;
}
