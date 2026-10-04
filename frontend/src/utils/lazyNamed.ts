import { lazy, type ComponentType } from 'react';

/**
 * `React.lazy` works with a module's `default` export; every feature here exports its page as a
 * named export from a barrel `index.ts` instead (`export { DashboardPage } from './DashboardPage'`).
 * This adapts one without having to add a `default` export everywhere just for code-splitting.
 */
export function lazyNamed<T extends ComponentType<unknown>>(
  // The barrel `index.ts` factory's inferred return type is the *whole* module (every named
  // export, hooks included) — too wide to constrain to "a map of components". `unknown` here
  // and the cast below are the deliberate boundary; `name` is always one of that module's own
  // component exports, checked at the call site by the string literal matching an actual export.
  factory: () => Promise<Record<string, unknown>>,
  name: string
) {
  return lazy(() =>
    factory().then((module) => ({ default: module[name] as T }))
  );
}
