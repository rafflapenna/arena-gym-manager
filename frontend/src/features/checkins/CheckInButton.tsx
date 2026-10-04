import { useState } from 'react';
import { DoorOpen } from 'lucide-react';
import { useCreateCheckIn } from './useCheckIns';
import { getApiErrorMessage } from '../../utils/apiError';
import { formatTime } from '../../utils/dateTime';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Button } from '../../components/ui/Button';
import type { CheckInResponse } from '../../types';

/** Operator "Registra presenza" action with inline outcome feedback. */
export function CheckInButton({ memberId }: { memberId: number }) {
  const createCheckIn = useCreateCheckIn(memberId);
  const [result, setResult] = useState<CheckInResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleClick = () => {
    setError(null);
    setResult(null);
    createCheckIn.mutate(undefined, {
      onSuccess: (data) => setResult(data),
      onError: (err) => setError(getApiErrorMessage(err)),
    });
  };

  const isWarning = result?.outcome === 'WARNING';

  return (
    <div className="flex flex-col gap-3">
      <Button onClick={handleClick} loading={createCheckIn.isPending} className="w-full sm:w-auto">
        <DoorOpen className="w-4 h-4" />
        {createCheckIn.isPending ? 'Registrazione…' : 'Registra presenza'}
      </Button>

      <div role="status" aria-live="polite">
        {result && (
          <div
            className={`px-4 py-3 rounded-xl text-sm border ${
              isWarning
                ? 'bg-amber-400/10 border-amber-400/20 text-amber-400'
                : 'bg-lime-400/10 border-lime-400/20 text-lime-400'
            }`}
          >
            <p className="font-semibold">
              {result.alreadyCheckedIn
                ? `Già presente dalle ${formatTime(result.checkInAt)}`
                : `Presenza registrata alle ${formatTime(result.checkInAt)}`}
            </p>
            {result.warnings.length > 0 && (
              <ul className="mt-1 list-disc list-inside text-xs">
                {result.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      {error && (
        <div role="alert">
          <ErrorMessage message={error} />
        </div>
      )}
    </div>
  );
}
