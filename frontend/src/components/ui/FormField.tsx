import type { ReactNode } from 'react';

type FormFieldProps = {
  label: string;
  htmlFor?: string;
  children: ReactNode;
};

/** Label + control wrapper matching the shared form field style used across create/edit forms. */
export function FormField({ label, htmlFor, children }: FormFieldProps) {
  return (
    <div>
      <label
        htmlFor={htmlFor}
        className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5"
      >
        {label}
      </label>
      {children}
    </div>
  );
}
