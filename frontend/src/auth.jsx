import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, setUnauthorizedHandler } from './api';

const STORAGE = 'pulsefit.session';
const AuthContext = createContext(null);

function loadSession() {
  try {
    const raw = localStorage.getItem(STORAGE);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session?.accessToken || !session?.user) return null;
    if (session.expiresAt && session.expiresAt <= Date.now()) {
      localStorage.removeItem(STORAGE);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

function persist(session) {
  if (!session) localStorage.removeItem(STORAGE);
  else localStorage.setItem(STORAGE, JSON.stringify(session));
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(loadSession);

  const clearSession = useCallback(() => {
    persist(null);
    setSession(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  useEffect(() => {
    if (!session?.expiresAt) return undefined;
    const delay = Math.max(session.expiresAt - Date.now(), 0);
    const timer = setTimeout(clearSession, delay);
    return () => clearTimeout(timer);
  }, [session?.expiresAt, clearSession]);

  function applyAuth(data) {
    const next = {
      accessToken: data.accessToken,
      tokenType: data.tokenType || 'Bearer',
      expiresAt: Date.now() + Number(data.expiresIn || 900) * 1000,
      user: data.user,
    };
    persist(next);
    setSession(next);
    return next.user;
  }

  async function login(email, password) {
    const data = await api('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: { email, password },
    });
    return applyAuth(data);
  }

  async function register(payload) {
    const data = await api('/api/auth/register', {
      method: 'POST',
      auth: false,
      body: payload,
    });
    return applyAuth(data);
  }

  async function completeClaim(payload) {
    const data = await api('/api/auth/claim/complete', {
      method: 'POST',
      auth: false,
      body: payload,
    });
    return applyAuth(data);
  }

  async function logout() {
    try {
      await api('/api/auth/logout', { method: 'POST' });
    } catch {
      /* Local session is cleared even if the gateway is unreachable. */
    }
    clearSession();
  }

  const value = {
    user: session?.user ?? null,
    expiresAt: session?.expiresAt ?? null,
    login,
    register,
    completeClaim,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
