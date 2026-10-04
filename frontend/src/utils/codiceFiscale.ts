// Pure, synchronous decoding of the Italian codice fiscale (no network, no React).
// Values verified by hand against the official check-character algorithm — run
// `node` on the snippet below (or see DESIGN-inserimento-rapido-soci.it.md §3) to
// reproduce:
//   RSSMRA80A01H501U — Mario Rossi, M, 01/01/1980, Roma (belfiore H501)
//   VRDMRA95B42F205I — female (day 02 + 40 = 42), 02/02/1995, Milano (F205)
//   RSSMRA80ALMH501X — same as Mario Rossi, but the day "01" written in omocodia
//                      (L=0, M=1) — decodes to the same birth date
//   PPSNIO90L15Z129H — foreign-born, 15/07/1990, Romania (belfiore Z129)

/** 12 month letters in calendar order (index 0 = gennaio … 11 = dicembre). */
const MONTH_LETTERS = ['A', 'B', 'C', 'D', 'E', 'H', 'L', 'M', 'P', 'R', 'S', 'T'] as const;

// Omocodia: when a numeric digit collides with an existing code, Agenzia delle Entrate
// replaces digits (in the year/day/belfiore-digits slots only) with these letters,
// preserving the same value. Decoding just reverses the substitution.
const OMOCODE_LETTER_TO_DIGIT: Record<string, string> = {
  L: '0', M: '1', N: '2', P: '3', Q: '4', R: '5', S: '6', T: '7', U: '8', V: '9',
};

function decodeDigit(ch: string): string {
  return /[0-9]/.test(ch) ? ch : (OMOCODE_LETTER_TO_DIGIT[ch] ?? ch);
}

// Loose structural check: 6 letters, then digit-or-omocode-letter slots for the parts
// that are normally numeric, a month letter, and a final check letter. Deliberately
// not stricter than this — a wrong check character is a warning, not a rejection.
const CF_SHAPE = /^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/;

// Official check-character tables (odd/even position value, 1-indexed position over
// the first 15 characters), per the Agenzia delle Entrate algorithm.
const ODD_POSITION_VALUE: Record<string, number> = {
  '0': 1, '1': 0, '2': 5, '3': 7, '4': 9, '5': 13, '6': 15, '7': 17, '8': 19, '9': 21,
  A: 1, B: 0, C: 5, D: 7, E: 9, F: 13, G: 15, H: 17, I: 19, J: 21,
  K: 2, L: 4, M: 18, N: 20, O: 11, P: 3, Q: 6, R: 8, S: 12, T: 14,
  U: 16, V: 10, W: 22, X: 25, Y: 24, Z: 23,
};
const EVEN_POSITION_VALUE: Record<string, number> = {
  '0': 0, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9,
  A: 0, B: 1, C: 2, D: 3, E: 4, F: 5, G: 6, H: 7, I: 8, J: 9, K: 10, L: 11, M: 12,
  N: 13, O: 14, P: 15, Q: 16, R: 17, S: 18, T: 19, U: 20, V: 21, W: 22, X: 23, Y: 24, Z: 25,
};

function computeCheckChar(first15: string): string {
  let sum = 0;
  for (let i = 0; i < 15; i++) {
    const c = first15[i];
    sum += i % 2 === 0 ? ODD_POSITION_VALUE[c] : EVEN_POSITION_VALUE[c];
  }
  return String.fromCharCode(65 + (sum % 26));
}

export interface FiscalCodeInfo {
  /** YYYY-MM-DD, or null if the day/month decode to an impossible calendar date. */
  birthDate: string | null;
  gender: 'M' | 'F' | null;
  /** Belfiore cadastral code, e.g. "H501" (comune) or "Z129" (foreign state). */
  belfioreCode: string | null;
  isForeign: boolean;
  /** False means the 16th character doesn't match what the first 15 compute to. */
  checksumValid: boolean;
}

/**
 * Decodes a codice fiscale into its embedded birth date / gender / birthplace code.
 * Returns null when the string doesn't even have the right shape (too short, wrong
 * character classes) — too early to say anything useful, not an error to surface.
 */
export function parseFiscalCode(raw: string): FiscalCodeInfo | null {
  const cf = raw.trim().toUpperCase();
  if (!CF_SHAPE.test(cf)) return null;

  const year2 = Number(decodeDigit(cf[6]) + decodeDigit(cf[7]));
  const monthIdx = MONTH_LETTERS.indexOf(cf[8] as (typeof MONTH_LETTERS)[number]);
  const dayRaw = Number(decodeDigit(cf[9]) + decodeDigit(cf[10]));

  let gender: 'M' | 'F' | null = null;
  let day: number | null = null;
  if (dayRaw >= 1 && dayRaw <= 31) {
    gender = 'M';
    day = dayRaw;
  } else if (dayRaw >= 41 && dayRaw <= 71) {
    gender = 'F';
    day = dayRaw - 40;
  }

  let birthDate: string | null = null;
  if (monthIdx >= 0 && day !== null) {
    const currentYear2 = new Date().getFullYear() % 100;
    const year = (year2 > currentYear2 ? 1900 : 2000) + year2;
    const mm = String(monthIdx + 1).padStart(2, '0');
    const dd = String(day).padStart(2, '0');
    const candidate = new Date(`${year}-${mm}-${dd}T00:00:00`);
    // Round-trip check catches impossible dates (31 Feb, …) that the shape regex allows.
    if (
      !Number.isNaN(candidate.getTime()) &&
      candidate.getFullYear() === year &&
      candidate.getMonth() === monthIdx &&
      candidate.getDate() === day
    ) {
      birthDate = `${year}-${mm}-${dd}`;
    }
  }

  const belfioreLetter = cf[11];
  const belfioreDigits = decodeDigit(cf[12]) + decodeDigit(cf[13]) + decodeDigit(cf[14]);
  const belfioreCode = belfioreLetter + belfioreDigits;

  return {
    birthDate,
    gender,
    belfioreCode,
    isForeign: belfioreLetter === 'Z',
    checksumValid: computeCheckChar(cf.slice(0, 15)) === cf[15],
  };
}

/** Minimal shape needed from a comune to resolve a belfiore code back to a name. */
export interface ComuneLookup {
  nome: string;
  codiceCatastale?: string | null;
}

/**
 * Resolves the birthplace name for a decoded fiscal code: a comune lookup by belfiore
 * code for Italian-born, or the static stati-esteri table for codes starting with Z.
 * Returns null when nothing matches (suppressed/merged comune not in the dataset,
 * unlisted foreign state) — the field is then left free for manual entry.
 */
export function resolveBirthPlace(
  info: Pick<FiscalCodeInfo, 'belfioreCode' | 'isForeign'>,
  comuni: ComuneLookup[] | null | undefined,
  statiEsteri: Record<string, string>,
): string | null {
  if (!info.belfioreCode) return null;
  if (info.isForeign) return statiEsteri[info.belfioreCode] ?? null;
  return comuni?.find((c) => c.codiceCatastale === info.belfioreCode)?.nome ?? null;
}
