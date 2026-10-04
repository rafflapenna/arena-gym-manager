import { useEffect, useRef, useState } from 'react';

/**
 * Keeps the screen awake while `active` is true (the member's QR page — reception has to be
 * able to scan it, and a phone that locks itself mid-tap defeats the point). Releases the lock
 * automatically when `active` turns false or the component unmounts, and silently no-ops where
 * the Wake Lock API isn't supported (Safari < 16.4, some older Android browsers) — staying awake
 * is a nice-to-have here, never a reason to block showing the code itself.
 */
export function useWakeLock(active: boolean): boolean {
  const lockRef = useRef<WakeLockSentinel | null>(null);
  const [isActive, setIsActive] = useState(false);

  useEffect(() => {
    if (!active || !('wakeLock' in navigator)) return;

    let cancelled = false;

    const acquire = async () => {
      try {
        const lock = await navigator.wakeLock.request('screen');
        if (cancelled) {
          void lock.release();
          return;
        }
        lockRef.current = lock;
        setIsActive(true);
        lock.addEventListener('release', () => setIsActive(false));
      } catch {
        // Browser refused (e.g. tab not visible yet) or the API threw — fine, just no lock.
        setIsActive(false);
      }
    };

    void acquire();

    // The browser auto-releases the lock when the tab is hidden; re-acquire it on return
    // (switching apps briefly to open the camera roll, then back to the QR screen).
    const handleVisibility = () => {
      if (document.visibilityState === 'visible' && !lockRef.current) void acquire();
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', handleVisibility);
      void lockRef.current?.release();
      lockRef.current = null;
      setIsActive(false);
    };
  }, [active]);

  return isActive;
}
