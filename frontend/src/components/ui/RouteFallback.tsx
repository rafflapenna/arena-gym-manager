import { LoadingSpinner } from './LoadingSpinner';

/**
 * Suspense fallback for a lazy-loaded route page (see App.tsx — staff/trainer/member areas are
 * each code-split, so navigating into one for the first time awaits its chunk). Centered in the
 * viewport rather than flush under the header, so it doesn't read as a half-loaded page.
 */
export function RouteFallback() {
  return (
    <div className="min-h-[60vh] flex items-center justify-center">
      <LoadingSpinner />
    </div>
  );
}
