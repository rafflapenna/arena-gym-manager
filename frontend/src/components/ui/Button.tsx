import { forwardRef } from 'react';
import type { ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  /** Solid fill instead of the default subtle/outlined look — needed for a destructive confirm
   * step ("Sì, elimina") that has to read as heavier than its own trigger button, which is
   * already a (subtle) `danger`. */
  solid?: boolean;
  loading?: boolean;
  children: ReactNode;
};

const base =
  'inline-flex items-center justify-center gap-2 min-h-11 px-4 rounded-xl text-[14.5px] font-semibold whitespace-nowrap transition-colors active:scale-[0.98] duration-150 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-lime-400/60';

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-lime-400 text-[#0b0b0d] font-bold hover:bg-lime-300',
  secondary: 'bg-[#1c1c1f] border border-white/9 text-zinc-100 hover:bg-zinc-800',
  danger: 'bg-red-400/8 border border-red-400/25 text-red-400 hover:bg-red-400/15',
  ghost: 'text-zinc-400 hover:text-zinc-200',
};

const solidVariants: Partial<Record<ButtonVariant, string>> = {
  primary: variants.primary,
  danger: 'bg-red-500 text-white font-bold hover:bg-red-400 border border-transparent',
};

/**
 * Shared action button — 44px min hit height plus consistent active/disabled/loading states.
 * Every page used to hand-roll its own "primary button" Tailwind string (see AUDIT-mobile.md
 * pacchetto B §1); this is the one to reuse instead of adding a new variant.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  function Button({ variant = 'primary', solid = false, loading = false, disabled, className = '', children, ...props }, ref) {
    const variantClass = (solid && solidVariants[variant]) || variants[variant];
    return (
      <button
        ref={ref}
        type={props.type ?? 'button'}
        disabled={disabled || loading}
        aria-busy={loading || undefined}
        className={`${base} ${variantClass} ${className}`}
        {...props}
      >
        {loading && (
          <span
            aria-hidden="true"
            className="w-4 h-4 border-2 border-current/30 border-t-current rounded-full animate-spin shrink-0"
          />
        )}
        {children}
      </button>
    );
  }
);
