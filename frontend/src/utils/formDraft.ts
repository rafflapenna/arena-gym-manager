// Tiny sessionStorage-backed draft persistence for long forms (e.g. the new-member
// form): survives an accidental tab close / back-navigation during data entry, but
// never outlives the tab and is cleared as soon as the form is saved. Every call is
// wrapped in try/catch — private browsing, blocked storage or a stale/corrupt shape
// must never break the form, draft recovery is a convenience, not a requirement.

/** Reads a previously saved draft, or null if there isn't one / it can't be read. */
export function loadDraft<T>(key: string): Partial<T> | null {
  try {
    const raw = sessionStorage.getItem(key);
    return raw ? (JSON.parse(raw) as Partial<T>) : null;
  } catch {
    return null;
  }
}

/** Saves the current form state as the draft for `key`, overwriting any previous one. */
export function saveDraft<T>(key: string, value: T): void {
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore — quota exceeded / storage disabled
  }
}

/** Removes the draft for `key` (call after a successful save). */
export function clearDraft(key: string): void {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // ignore
  }
}
