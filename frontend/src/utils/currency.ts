const currencyFmt = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' });

/**
 * "1.234,50 €" (it-IT grouping/decimal + trailing symbol) — the one place every page formats a
 * price, instead of each hand-rolling `€ {x.toFixed(2)}` (wrong decimal separator for it-IT,
 * and the € sign goes after the amount in this locale, not before).
 */
export function formatCurrency(amount: number): string {
  return currencyFmt.format(amount);
}
