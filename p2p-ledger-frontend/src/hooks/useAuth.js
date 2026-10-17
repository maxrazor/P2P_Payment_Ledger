import { useCallback, useEffect, useState } from 'react';
import { authApi } from '../api/authApi.js';

export function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState('');

  useEffect(() => {
    let alive = true;
    authApi.me()
      .then((currentUser) => { if (alive) setUser(currentUser); })
      .catch((error) => {
        if (!alive) return;
        if (error.status !== 401 && error.status !== 403) {
          setSessionError(error.message);
        }
        setUser(null);
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, []);

  const login = useCallback(async (credentials) => {
    const currentUser = await authApi.login(credentials);
    setUser(currentUser); // The backend sets the HttpOnly cookie.
    setSessionError('');
    return currentUser;
  }, []);

  const register = useCallback((payload) => authApi.register(payload), []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  return { user, loading, sessionError, login, register, logout };
}
