import type { Lookup } from './common';

/** Minimal package reference as embedded in a subscription. */
export interface SubscriptionPackageRef {
  id: number;
  name: string;
}

export interface SubscriptionRequest {
  /** Exactly one of `typeId` / `packageId` must be set. */
  typeId?: number;
  packageId?: number;
  startDate: string;
  /** Only for the `typeId` (generic type) branch — the BE snapshots the price for `packageId`. */
  price?: number;
  notes?: string | null;
  /** Credito "porta un amico" da spendere su questo abbonamento. Uno solo, non cumulabile. */
  rewardId?: number | null;
}

export interface SubscriptionResponse {
  id: number;
  memberId: number;
  memberFullName: string;
  /** Null when the subscription was created from a package instead of a generic type. */
  type: Lookup | null;
  /** Set when the subscription was created from a package. */
  package: SubscriptionPackageRef | null;
  startDate: string;
  endDate: string;
  price: number;
  /** Price snapshotted from the package at purchase time; null for generic-type subscriptions. */
  purchasePrice: number | null;
  status: Lookup;
  notes: string | null;
  createdAt: string;
}
