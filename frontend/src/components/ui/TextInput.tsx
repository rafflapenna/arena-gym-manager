import { forwardRef } from 'react';
import type { InputHTMLAttributes } from 'react';

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function TextInput({ className = '', type = 'text', ...props }, ref) {
    return (
      <input
        ref={ref}
        type={type}
        {...props}
        className={`w-full bg-zinc-900 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm placeholder-zinc-600 focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors ${className}`}
      />
    );
  }
);
