export type FilterChipOption<T extends string> = { key: T; label: string };

type FilterChipsProps<T extends string> = {
  options: FilterChipOption<T>[];
  value: T;
  onChange: (key: T) => void;
  'aria-label'?: string;
};

/**
 * Horizontally-scrollable row of status-filter pills — the one shape reused for every "Tutti /
 * Attivi / Scaduti / ..." filter in the app (abbonamenti, prove, ...). Always >=44px tall
 * (AUDIT-mobile.md pacchetto A §3: chips were 28px). `-mx-5 px-5` lets the row bleed to the
 * page's own edges so the scrollable area isn't visually clipped by the page padding.
 */
export function FilterChips<T extends string>({
  options,
  value,
  onChange,
  'aria-label': ariaLabel,
}: FilterChipsProps<T>) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className="flex gap-2 overflow-x-auto -mx-5 px-5 sm:mx-0 sm:px-0 sm:flex-wrap pb-1 sm:pb-0"
    >
      {options.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          onClick={() => onChange(key)}
          aria-pressed={value === key}
          className={`shrink-0 inline-flex items-center justify-center min-h-11 px-4 rounded-lg text-xs font-semibold uppercase tracking-wide transition-colors ${
            value === key ? 'bg-lime-400 text-[#0b0b0d]' : 'bg-zinc-800 text-zinc-400 hover:text-zinc-200'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
