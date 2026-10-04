import { useState, useEffect, useRef, useCallback } from 'react';

/**
 * Ticks a "retry after" countdown down to 0 in whole seconds, one tick per second.
 * `start(seconds)` (re)starts it; the interval is cleared on unmount and whenever
 * it reaches 0.
 */
export function useRetryCountdown(): [number, (seconds: number) => void] {
  const [secondsLeft, setSecondsLeft] = useState(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, []);

  const start = useCallback((seconds: number) => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    setSecondsLeft(seconds);
    intervalRef.current = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          if (intervalRef.current) clearInterval(intervalRef.current);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  return [secondsLeft, start];
}
