import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Dumbbell } from 'lucide-react';
import { AxiosError } from 'axios';
import { authService } from '../../services/authService';
import { getApiErrorMessage, getRetryAfterSeconds } from '../../utils/apiError';
import { useRetryCountdown } from '../../hooks/useRetryCountdown';
import { formatCountdown } from '../../utils/dateTime';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [retrySeconds, startRetryCountdown] = useRetryCountdown();
  const isRetrying = retrySeconds > 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      await authService.forgotPassword(email.trim());
      setSubmitted(true);
    } catch (err) {
      const status = err instanceof AxiosError ? err.response?.status : undefined;
      if (status === 429) {
        const seconds = getRetryAfterSeconds(err);
        if (seconds !== undefined) {
          startRetryCountdown(seconds);
        } else {
          setError(getApiErrorMessage(err));
        }
      } else {
        setError(getApiErrorMessage(err));
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center mb-8 gap-3">
          <div className="w-12 h-12 bg-lime-400 rounded-2xl flex items-center justify-center">
            <Dumbbell className="w-7 h-7 text-[#0b0b0d]" strokeWidth={2.5} />
          </div>
          <div className="text-center">
            <p className="font-display font-bold text-2xl tracking-widest text-zinc-100 uppercase leading-none">
              Gym
            </p>
            <p className="font-display font-semibold text-sm tracking-wider text-lime-400 uppercase">
              Manager
            </p>
          </div>
        </div>

        <div className="bg-zinc-900 border border-white/5 rounded-2xl p-7 shadow-xl shadow-black/40">
          <h1 className="text-zinc-100 font-bold text-xl mb-1">Password dimenticata</h1>
          <p className="text-zinc-500 text-sm mb-6">
            Inserisci la tua email e ti invieremo le istruzioni per reimpostare la password.
          </p>

          {submitted ? (
            <div className="px-4 py-3 bg-lime-400/10 border border-lime-400/20 rounded-xl text-lime-400 text-sm">
              Se l&apos;email è registrata, riceverai un link per reimpostare la password.
            </div>
          ) : (
            <>
              {isRetrying ? (
                <div className="mb-4 px-4 py-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
                  <div role="alert">Troppi tentativi.</div>
                  <div aria-live="polite">Riprova tra {formatCountdown(retrySeconds)}</div>
                </div>
              ) : (
                error && (
                  <div
                    role="alert"
                    className="mb-4 px-4 py-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm"
                  >
                    {error}
                  </div>
                )
              )}

              <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div>
                  <label
                    htmlFor="forgot-email"
                    className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5"
                  >
                    Email
                  </label>
                  <input
                    id="forgot-email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                    autoComplete="email"
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                    enterKeyHint="send"
                    className="w-full bg-zinc-800 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
                  />
                </div>

                <button
                  type="submit"
                  disabled={isLoading || isRetrying}
                  className="mt-2 flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3.5 rounded-xl hover:bg-lime-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  {isLoading ? 'Invio in corso…' : 'Invia istruzioni'}
                </button>
              </form>
            </>
          )}

          <div className="mt-5 text-center">
            <Link
              to="/login"
              className="text-zinc-500 text-sm hover:text-zinc-300 transition-colors"
            >
              Torna al login
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
