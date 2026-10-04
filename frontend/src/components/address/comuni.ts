import apiClient from '../../services/apiClient';

// `codiceCatastale` (the Belfiore code, e.g. "A001") isn't exposed by the backend yet —
// optional so the fiscal-code-derivation feature degrades gracefully (no birthplace
// resolved) until `ComuneDTO` grows the field.
export type Comune = {
  nome: string;
  sigla: string;
  provincia: string;
  regione: string;
  cap: string[];
  codiceCatastale?: string | null;
};

// Loaded once from the backend (GET /comuni), then cached for the session. The
// backend keeps the list fresh (monthly CDN refresh), so it's always up to date
// without bundling the dataset into the app. The shared promise dedupes concurrent
// callers; on failure the cache is cleared so a later mount can retry.
//
// Lives in its own module (not AddressFields.tsx) so non-component code can import it
// without breaking that file's Fast Refresh (react-refresh/only-export-components).
let cache: Promise<Comune[]> | null = null;
export function loadComuni(): Promise<Comune[]> {
  if (!cache) {
    cache = apiClient
      .get<Comune[]>('/comuni')
      .then((r) => r.data)
      .catch((e) => {
        cache = null;
        throw e;
      });
  }
  return cache;
}
