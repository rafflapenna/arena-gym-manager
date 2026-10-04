import { forwardRef } from 'react';
import type { ChangeEvent, InputHTMLAttributes } from 'react';
import { TextInput } from './TextInput';

type MoneyInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'inputMode' | 'pattern'>;

/**
 * Normalizes typed text to a plain "123.45"-shaped decimal string: the Italian comma is
 * accepted as a decimal separator (same as the dot), everything else is stripped, and only
 * the first separator survives — so downstream `parseFloat`/`Number` never silently truncates
 * "12,50" to 12.
 */
function normalizeDecimal(raw: string): string {
  let value = raw.replace(/,/g, '.').replace(/[^0-9.]/g, '');
  const firstDot = value.indexOf('.');
  if (firstDot !== -1) {
    value = value.slice(0, firstDot + 1) + value.slice(firstDot + 1).replace(/\./g, '');
  }
  return value;
}

/**
 * Amount input pre-configured for currency values. Plain text, not `type="number"`: the
 * native number input rejects the Italian decimal comma outright (typing "12,50" leaves just
 * "12"), and some Android keyboards clip it differently. `inputMode="decimal"` still brings up
 * a numeric keypad with a separator key on mobile.
 */
export const MoneyInput = forwardRef<HTMLInputElement, MoneyInputProps>(
  function MoneyInput({ placeholder, onChange, ...props }, ref) {
    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
      e.target.value = normalizeDecimal(e.target.value);
      onChange?.(e);
    };
    return (
      <TextInput
        ref={ref}
        type="text"
        inputMode="decimal"
        pattern="[0-9]*[.,]?[0-9]*"
        placeholder={placeholder ?? '0.00'}
        onChange={handleChange}
        {...props}
      />
    );
  }
);
