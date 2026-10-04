import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Dumbbell } from 'lucide-react';
import type { AxiosError } from 'axios';
import apiClient from '../../services/apiClient';
import { useAuthStore } from '../../stores/authStore';
import type { AuthResponse } from '../../types/auth';

export function SetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  if (!token) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center px-4">
        <p className="text-red-400 text-sm">Link non valido.</p>
      </div>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword.length < 6) {
      setError('La password deve essere di almeno 6 caratteri');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Le password non coincidono');
      return;
    }

    setIsLoading(true);
    try {
      const res = await apiClient.post<AuthResponse>('/auth/set-password', { token, newPassword });
      login(res.data);
      navigate('/me', { replace: true });
    } catch (err) {
      const axErr = err as AxiosError<{ detail?: string }>;
      if (axErr.response?.status === 400) {
        setError('Il link è scaduto o non valido. Contatta la palestra per un nuovo invito.');
      } else {
        setError('Errore durante il salvataggio. Riprova.');
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
          <h1 className="text-zinc-100 font-bold text-xl mb-1">Crea la tua password</h1>
          <p className="text-zinc-500 text-sm mb-6">
            Scegli una password sicura per accedere all'app.
          </p>

          {error && (
            <div className="mb-4 px-4 py-3 bg-red-500/10 border border-red-500/20 rounded-xl text-red-400 text-sm">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <div>
              <label className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5">
                Nuova password
              </label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                autoComplete="new-password"
                className="w-full bg-zinc-800 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
              />
            </div>

            <div>
              <label className="block text-zinc-500 text-xs font-semibold uppercase tracking-wide mb-1.5">
                Conferma password
              </label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
                className="w-full bg-zinc-800 border border-white/5 rounded-xl px-4 py-3 text-zinc-100 text-sm focus:outline-none focus:border-lime-400/50 focus:ring-1 focus:ring-lime-400/30 transition-colors"
              />
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="mt-2 flex items-center justify-center gap-2 bg-lime-400 text-[#0b0b0d] font-bold py-3.5 rounded-xl hover:bg-lime-300 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {isLoading ? 'Salvataggio…' : 'Accedi all\'app'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
