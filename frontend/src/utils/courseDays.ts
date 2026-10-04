/**
 * ISO weekday labels and formatting for a course's schedule.
 *
 * A course runs on one to seven days, so every screen that shows a schedule needs the same
 * label table — it used to be copy-pasted in five components, each indexing `dayOfWeek - 1`
 * on its own array.
 */

/** Index 0 = Monday, matching ISO day 1..7 minus one. */
export const DAY_LABELS_LONG = [
  'Lunedì',
  'Martedì',
  'Mercoledì',
  'Giovedì',
  'Venerdì',
  'Sabato',
  'Domenica',
] as const;

export const DAY_LABELS_SHORT = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'] as const;

export const ALL_DAYS = [1, 2, 3, 4, 5, 6, 7] as const;

/** Label for a single ISO day (1 = Monday). Empty string for anything out of range. */
export function dayLabel(day: number, variant: 'short' | 'long' = 'long'): string {
  const table = variant === 'short' ? DAY_LABELS_SHORT : DAY_LABELS_LONG;
  return table[day - 1] ?? '';
}

/**
 * Renders a course's days: "Lun · Mer · Ven", collapsing the full week to "Tutti i giorni"
 * so a 7-day course doesn't blow up a narrow column.
 *
 * Accepts `undefined` because a backend deployed before this frontend still answers with only
 * the legacy single `dayOfWeek`; callers pass `course.daysOfWeek ?? [course.dayOfWeek]`.
 */
export function formatCourseDays(
  days: number[] | undefined | null,
  variant: 'short' | 'long' = 'short',
): string {
  if (!days || days.length === 0) return '';
  if (days.length === 7) return 'Tutti i giorni';
  return [...days]
    .sort((a, b) => a - b)
    .map((d) => dayLabel(d, variant))
    .filter(Boolean)
    .join(' · ');
}
