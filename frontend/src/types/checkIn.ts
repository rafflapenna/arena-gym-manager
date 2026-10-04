export type CheckInSource = 'RECEPTION' | 'SELF' | 'QR';

export type CheckInOutcome = 'OK' | 'WARNING';

export interface CheckInResponse {
  id: number;
  memberId: number;
  memberFullName: string;
  /** ISO LocalDateTime (no timezone), e.g. "2026-06-23T19:02:00". */
  checkInAt: string;
  source: CheckInSource;
  outcome: CheckInOutcome;
  /** Non-blocking eligibility notices (e.g. expired subscription). Empty on list endpoints. */
  warnings: string[];
  /** True when the check-in already existed for the day (idempotent re-click). */
  alreadyCheckedIn: boolean;
}
