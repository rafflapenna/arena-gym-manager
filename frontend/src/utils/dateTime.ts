// Locale-aware (it-IT) formatting for ISO date / LocalDateTime strings from the backend.
// A LocalDateTime without timezone (e.g. "2026-06-23T19:02:00") is parsed by `new Date(...)`
// as the browser's local time, which is what we want for displaying check-in times.

const timeFmt = new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit' });
const dateTimeFmt = new Intl.DateTimeFormat('it-IT', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const dayMonthFmt = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'short' });
const monthShortFmt = new Intl.DateTimeFormat('it-IT', { month: 'short' });
const monthLongFmt = new Intl.DateTimeFormat('it-IT', { month: 'long' });
const dayMonthLongFmt = new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long' });
const dayMonthYearFmt = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});
const weekdayFmt = new Intl.DateTimeFormat('it-IT', { weekday: 'long' });
const weekdayLongDateFmt = new Intl.DateTimeFormat('it-IT', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

const EMPTY = '—'; // em dash

function parse(iso?: string | null): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** "HH:MM" or "—" when invalid. */
export function formatTime(iso?: string | null): string {
  const d = parse(iso);
  return d ? timeFmt.format(d) : EMPTY;
}

/** "GG/MM/AAAA, HH:MM" or "—" when invalid. */
export function formatDateTime(iso?: string | null): string {
  const d = parse(iso);
  return d ? dateTimeFmt.format(d) : EMPTY;
}

/** "20 giu" or "—" when invalid. */
export function formatDayMonth(iso?: string | null): string {
  const d = parse(iso);
  return d ? dayMonthFmt.format(d) : EMPTY;
}

/** "12 gen 2026" or "—" when invalid. */
export function formatDayMonthYear(iso?: string | null): string {
  const d = parse(iso);
  return d ? dayMonthYearFmt.format(d) : EMPTY;
}

/** "giovedì" (lowercase weekday) or "—" when invalid. */
export function formatWeekday(iso?: string | null): string {
  const d = parse(iso);
  return d ? weekdayFmt.format(d) : EMPTY;
}

/** "Giovedì 3 luglio" (capitalized weekday + day + month) or "—" when invalid. */
export function formatWeekdayLongDate(date: Date): string {
  const s = weekdayLongDateFmt.format(date);
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** "il " or "l'" — Italian elides "il" before 8/11 (otto/undici start with a vowel sound). */
function italianDayArticle(day: number): string {
  return day === 8 || day === 11 ? "l'" : 'il ';
}

/** "il 20 giu" / "l'11 lug" or "—" when invalid. */
export function formatDayMonthWithArticle(iso?: string | null): string {
  const d = parse(iso);
  if (!d) return EMPTY;
  return `${italianDayArticle(d.getDate())}${d.getDate()} ${monthShortFmt.format(d)}`;
}

/** Full lowercase month name for the given date, e.g. "luglio". */
export function formatMonthLong(date: Date): string {
  return monthLongFmt.format(date);
}

/** "3 luglio" (day + full month name), parsed as a local YYYY-MM-DD date (no time component). */
export function formatDayMonthLong(isoDate: string): string {
  return dayMonthLongFmt.format(new Date(`${isoDate}T00:00:00`));
}

/** "Oggi, 3 luglio" / "Ieri, 2 luglio" / "Lunedì, 29 giugno" for a YYYY-MM-DD date string. */
export function formatRelativeDayLabel(isoDate: string): string {
  const d = new Date(`${isoDate}T00:00:00`);
  const today = startOfToday();
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  if (isSameDay(d, today)) return `Oggi, ${formatDayMonthLong(isoDate)}`;
  if (isSameDay(d, yesterday)) return `Ieri, ${formatDayMonthLong(isoDate)}`;
  const weekday = weekdayFmt.format(d);
  return `${weekday.charAt(0).toUpperCase()}${weekday.slice(1)}, ${formatDayMonthLong(isoDate)}`;
}

/** Start-of-day Date for "today", used for day-diff math. */
export function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * Local "today" as YYYY-MM-DD. `new Date().toISOString().split('T')[0]` is UTC-based and
 * rolls over at midnight UTC, which after 21:00-01:00 CET/CEST is still "yesterday" for the
 * user — this stays on the browser's local calendar day instead.
 */
export function todayIsoLocal(): string {
  return dateToIsoLocal(new Date());
}

/** Any Date as a local YYYY-MM-DD string (no UTC shift). */
export function dateToIsoLocal(d: Date): string {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

/** True if the two dates fall on the same calendar day. */
export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** "45 s" under a minute, "1 min 30 s" (or "2 min" on an exact minute) from there up. */
export function formatCountdown(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds));
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return rest === 0 ? `${minutes} min` : `${minutes} min ${rest} s`;
}
