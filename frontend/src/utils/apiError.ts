import { AxiosError } from 'axios';

interface ProblemDetail {
  detail?: string;
  message?: string;
  errors?: Record<string, string>;
  status?: number;
  /** Machine-readable error code some 409s carry (e.g. LEAD_DUPLICATE_PHONE), for branching UI copy. */
  code?: string;
  /** Seconds to wait before retrying, carried by 429 rate-limit responses. */
  retryAfterSeconds?: number;
}

/**
 * Extracts a human-readable Italian error message from an API error.
 * Handles RFC 9457 ProblemDetail responses from the backend:
 *   - reads `detail`, then `message`
 *   - for 422 validation errors concatenates field-level `errors` map messages
 *   - falls back to a generic Italian message
 */
export function getApiErrorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ProblemDetail | undefined;
    const status = err.response?.status;

    // 422: concatenate validation field errors if present
    if (status === 422 && data?.errors) {
      const fieldMessages = Object.values(data.errors).filter(Boolean);
      if (fieldMessages.length > 0) {
        return fieldMessages.join('. ');
      }
    }

    if (data?.detail) return data.detail;
    if (data?.message) return data.message;

    if (status !== undefined) {
      return `Errore ${status}. Riprova più tardi.`;
    }
  }

  return 'Errore imprevisto. Riprova più tardi.';
}

/** Extracts the backend's machine-readable `code` property, when the response carries one. */
export function getApiErrorCode(err: unknown): string | undefined {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ProblemDetail | undefined;
    return data?.code;
  }
  return undefined;
}

/**
 * Extracts how many seconds to wait before retrying a 429 response: reads the
 * ProblemDetail's `retryAfterSeconds` first, then falls back to the standard
 * `Retry-After` header. Returns `undefined` when neither is present (older BE
 * that doesn't send either yet).
 */
export function getRetryAfterSeconds(err: unknown): number | undefined {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ProblemDetail | undefined;
    if (typeof data?.retryAfterSeconds === 'number' && data.retryAfterSeconds > 0) {
      return data.retryAfterSeconds;
    }

    const header = err.response?.headers?.['retry-after'];
    const parsed = typeof header === 'string' ? parseInt(header, 10) : undefined;
    if (parsed !== undefined && !Number.isNaN(parsed) && parsed > 0) {
      return parsed;
    }
  }
  return undefined;
}
