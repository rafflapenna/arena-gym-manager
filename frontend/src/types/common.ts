export interface Lookup {
  id: number;
  code: string;
  label: string;
  months: number | null;
  /** List price for this subscription type (nullable); null for lookups other than subscription types. */
  defaultPrice: number | null;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
