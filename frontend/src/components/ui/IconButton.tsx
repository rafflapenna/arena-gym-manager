import { forwardRef } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'aria-label'> & {
  /** Required, not optional: an icon-only button has no visible text for assistive tech to
   * read, and this is the one place the audit kept finding it missing. */
  'aria-label': string;
  children: ReactNode;
};

/**
 * 44×44 icon-only button — the minimum comfortable tap target (AUDIT-mobile.md pacchetto A §3:
 * header icons, modal close, list-row edit/delete, etc. were as small as 16–24px).
 */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  function IconButton({ className = '', children, ...props }, ref) {
    return (
      <button
        ref={ref}
        type={props.type ?? 'button'}
        className={`inline-flex items-center justify-center w-11 h-11 shrink-0 rounded-lg text-zinc-400 hover:text-zinc-100 transition-colors disabled:opacity-50 disabled:cursor-not-allowed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60 ${className}`}
        {...props}
      >
        {children}
      </button>
    );
  }
);
