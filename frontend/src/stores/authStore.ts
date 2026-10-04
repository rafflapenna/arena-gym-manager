import { create } from 'zustand';
import type { AuthResponse, AuthUser } from '../types/auth';
import { normalizeRole } from '../types/auth';
import { queryClient } from '../lib/queryClient';

interface AuthState {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  memberId: number | null;
  mustChangePassword: boolean;
  /** Returns false (and leaves the store unauthenticated) if the response carries an unknown role. */
  login: (response: AuthResponse) => boolean;
  logout: () => void;
  clearMustChangePassword: () => void;
}

/** Shape of a decoded JWT payload; every field is optional since we can't trust wire data. */
interface JwtPayload {
  sub?: string;
  username?: string;
  name?: string;
  role?: string;
  memberId?: number;
  gymId?: number;
  mustChangePassword?: boolean;
  exp?: number;
}

/** Decodes a JWT payload (base64url + UTF-8), returning null on any malformed input. */
function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const b64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as JwtPayload;
  } catch {
    return null;
  }
}

function parseUserFromToken(token: string): AuthUser | null {
  const payload = decodeJwtPayload(token);
  if (!payload) return null;
  if (payload.exp && payload.exp * 1000 < Date.now()) return null;
  const role = payload.role ? normalizeRole(payload.role) : null;
  if (!role) return null;
  return {
    username: payload.sub ?? payload.username ?? '',
    name: payload.name ?? undefined,
    role,
    memberId: payload.memberId ?? undefined,
    gymId: payload.gymId ?? undefined,
    mustChangePassword: payload.mustChangePassword ?? false,
  };
}

const storedToken = localStorage.getItem('token');
const initialUser = storedToken ? parseUserFromToken(storedToken) : null;

export const useAuthStore = create<AuthState>((set) => ({
  token: storedToken,
  user: initialUser,
  isAuthenticated: !!storedToken && !!initialUser,
  memberId: initialUser?.memberId ?? null,
  mustChangePassword: initialUser?.mustChangePassword ?? false,

  login: (response: AuthResponse) => {
    const role = normalizeRole(response.role);
    if (!role) {
      console.error('Login response has an unknown role:', response.role);
      return false;
    }
    localStorage.setItem('token', response.token);
    set({
      token: response.token,
      user: {
        username: response.username,
        name: response.name,
        role,
        memberId: response.memberId,
        gymId: response.gymId,
        mustChangePassword: response.mustChangePassword,
      },
      isAuthenticated: true,
      memberId: response.memberId ?? null,
      mustChangePassword: response.mustChangePassword ?? false,
    });
    return true;
  },

  logout: () => {
    localStorage.removeItem('token');
    set({ token: null, user: null, isAuthenticated: false, memberId: null, mustChangePassword: false });
    queryClient.clear();
  },

  clearMustChangePassword: () => {
    set((state) => ({
      mustChangePassword: false,
      user: state.user ? { ...state.user, mustChangePassword: false } : null,
    }));
  },
}));
