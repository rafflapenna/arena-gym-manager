export type BadgeVariant = 'lime' | 'amber' | 'orange' | 'cyan' | 'violet' | 'zinc' | 'red';

type BadgeProps = {
  children: React.ReactNode;
  variant?: BadgeVariant;
};

const variants: Record<BadgeVariant, string> = {
  lime:   'bg-lime-400/10   text-lime-400   border-lime-400/20',
  amber:  'bg-amber-400/10  text-amber-400  border-amber-400/20',
  orange: 'bg-orange-400/10 text-orange-400 border-orange-400/20',
  cyan:   'bg-cyan-400/10   text-cyan-400   border-cyan-400/20',
  violet: 'bg-violet-400/10 text-violet-400 border-violet-400/20',
  zinc:   'bg-zinc-700/50   text-zinc-400   border-zinc-600',
  red:    'bg-red-400/10    text-red-400    border-red-400/20',
};

export function Badge({ children, variant = 'zinc' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold border ${variants[variant]}`}
    >
      {children}
    </span>
  );
}
