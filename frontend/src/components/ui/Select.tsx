import { forwardRef } from 'react';
import type { SelectHTMLAttributes } from 'react';

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className = '', children, ...props }, ref) {
    return (
      <select
        ref={ref}
        {...props}
        className={`w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors ${className}`}
      >
        {children}
      </select>
    );
  }
);
