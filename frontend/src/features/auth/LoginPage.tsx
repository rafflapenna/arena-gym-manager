import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Dumbbell } from 'lucide-react';
import type { AxiosError } from 'axios';
import { authService } from '../../services/authService';
import { useAuthStore } from '../../stores/authStore';
import { getRetryAfterSeconds } from '../../utils/apiError';
import { useRetryCountdown } from '../../hooks/useRetryCountdown';
import { formatCountdown } from '../../utils/dateTime';

export function LoginPage() {
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [retrySeconds, startRetryCountdown] = useRetryCountdown();
  const isRetrying = retrySeconds > 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);
    try {
      const response = await authService.login({ username: username.trim(), password });
      if (!login(response)) {
        setError('Account non riconosciuto. Contatta l\'assistenza.');
        return;
      }
      if (response.mustChangePassword) {
        navigate('/change-password', { replace: true });
      } else if (response.role === 'MEMBER') {
        navigate('/me', { replace: true });
      } else {
        navigate('/dashboard', { replace: true });
      }
    } catch (err) {
      const axErr = err as AxiosError;
      const status = axErr.response?.status;

      if (status === 401 || status === 403) {
        setError('Username o password non corretti. Riprova.');
      } else if (status === 429) {
        const seconds = getRetryAfterSeconds(err);
        if (seconds !== undefined) {
          startRetryCountdown(seconds);
        } else {
          setError('Troppi tentativi di accesso. Attendi qualche minuto e riprova.');
        }
      } else if (status && status >= 500) {
        setError('Servizio momentaneamente non disponibile. Riprova tra qualche istante.');
      } else if (!axErr.response) {
        setError('Impossibile raggiungere il server. Controlla la connessione e riprova.');
      } else {
        setError('Si è verificato un errore imprevisto. Riprova più tardi.');
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
          <h1 className="text-zinc-100 font-bold text-xl mb-6">Accedi</h1>

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
              <label className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5">
                Username
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                enterKeyHint="next"
                className="w-full bg-zinc-800 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
              />
            </div>

            <div>
              <label className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5">
                Password
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                enterKeyHint="go"
                className="w-full bg-zinc-800 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
              />
            </div>

            <button
              type="submit"
              disabled={isLoading || isRetrying}
              className="mt-2 flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3.5 rounded-xl hover:bg-lime-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isLoading ? 'Accesso in corso…' : 'Accedi'}
            </button>
          </form>

          <div className="mt-3 text-center">
            <Link
              to="/forgot-password"
              className="inline-flex items-center justify-center min-h-11 px-2 text-zinc-500 text-sm hover:text-zinc-300 transition-colors"
            >
              Password dimenticata?
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
