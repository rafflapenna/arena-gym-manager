import type { ReactNode } from 'react';

type StickyActionsProps = {
  children: ReactNode;
  className?: string;
};

/**
 * Sticky action bar for long forms/wizards (AUDIT-mobile.md pacchetto B §2): below `md` it
 * sticks above the bottom-nav once the page scrolls past it, instead of forcing a scroll-to-
 * bottom hunt for "Continua"/"Salva". Plain `position: sticky; bottom: 0` inside the page's own
 * scroll container (`<main>` in AppLayout/MemberLayout, whose bottom padding already reserves
 * room for the fixed bottom-nav + safe-area — see those layouts) parks it right above the nav
 * with no per-layout offset math needed here. `-mx-5 px-5` bleeds to the page's own mobile
 * gutter (the `px-5` convention every page below `md` uses) so the backdrop reaches the screen
 * edges. On desktop (no bottom-nav) it reverts to a plain static block.
 */
export function StickyActions({ children, className = '' }: StickyActionsProps) {
  return (
    <div
      className={`sticky bottom-0 z-10 -mx-5 px-5 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:static md:mx-0 md:px-0 md:pt-0 md:pb-0 bg-[#0b0b0d]/97 backdrop-blur-sm md:bg-transparent md:backdrop-blur-none border-t border-white/6 md:border-0 ${className}`}
    >
      {children}
    </div>
  );
}
