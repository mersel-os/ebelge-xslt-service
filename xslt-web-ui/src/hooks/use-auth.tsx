import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { AXIOS_INSTANCE } from "@/api/axios-instance";

interface AuthState {
  authenticated: boolean;
  username: string | null;
}

interface AuthContextValue extends AuthState {
  /** Try to login with given credentials. Returns true on success. */
  login: (username: string, password: string) => Promise<boolean>;
  /** Clear stored credentials and log out. */
  logout: () => void;
  /** Whether a login attempt is in progress. */
  loading: boolean;
}

const STORAGE_KEY = "xslt_admin_token";

const AuthContext = createContext<AuthContextValue | null>(null);

function getStoredAuth(): { token: string; username: string } | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ authenticated: false, username: null });
  const [loading, setLoading] = useState(false);
  const [initialized, setInitialized] = useState(false);

  // Set up axios request interceptor — attach Bearer token for admin endpoints
  useEffect(() => {
    const id = AXIOS_INSTANCE.interceptors.request.use((config) => {
      const url = config.url ?? "";
      if (url.includes("/v1/admin") || url.includes("/v1/auth")) {
        const stored = getStoredAuth();
        if (stored) {
          config.headers.Authorization = `Bearer ${stored.token}`;
        }
      }
      return config;
    });
    return () => AXIOS_INSTANCE.interceptors.request.eject(id);
  }, []);

  // Handle 401 responses — auto logout
  useEffect(() => {
    const id = AXIOS_INSTANCE.interceptors.response.use(
      (res) => res,
      (err) => {
        if (
          err?.response?.status === 401 &&
          (err?.config?.url?.includes("/v1/admin") ||
            err?.config?.url?.includes("/v1/auth/check"))
        ) {
          try { localStorage.removeItem(STORAGE_KEY); } catch { /* quota/private */ }
          setState({ authenticated: false, username: null });
        }
        return Promise.reject(err);
      }
    );
    return () => AXIOS_INSTANCE.interceptors.response.eject(id);
  }, []);

  // On mount: validate stored token against backend
  useEffect(() => {
    const stored = getStoredAuth();
    if (!stored) {
      setInitialized(true);
      return;
    }

    const controller = new AbortController();

    AXIOS_INSTANCE.get("/v1/auth/check", {
      headers: { Authorization: `Bearer ${stored.token}` },
      validateStatus: (s) => s < 500,
      signal: controller.signal,
    })
      .then((res) => {
        if (controller.signal.aborted) return;
        if (res.status === 200 && res.data?.authenticated) {
          setState({ authenticated: true, username: stored.username });
        } else {
          try { localStorage.removeItem(STORAGE_KEY); } catch { /* quota/private */ }
        }
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        console.error("Auth check failed:", err);
        try { localStorage.removeItem(STORAGE_KEY); } catch { /* quota/private */ }
      })
      .finally(() => {
        if (!controller.signal.aborted) setInitialized(true);
      });

    return () => controller.abort();
  }, []);

  const login = useCallback(async (username: string, password: string): Promise<boolean> => {
    setLoading(true);
    try {
      const res = await AXIOS_INSTANCE.post(
        "/v1/auth/login",
        { username, password },
        { validateStatus: (s) => s < 500 }
      );

      if (res.status === 200 && res.data?.token) {
        const token = typeof res.data.token === "string" ? res.data.token : String(res.data.token);
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, username })); } catch { /* quota/private */ }
        setState({ authenticated: true, username });
        return true;
      }
      return false;
    } catch (err) {
      console.error("Login failed:", err);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    const stored = getStoredAuth();
    if (stored) {
      // Best-effort server-side logout
      AXIOS_INSTANCE.post("/v1/auth/logout", null, {
        headers: { Authorization: `Bearer ${stored.token}` },
      }).catch((err) => console.error("Logout request failed:", err));
    }
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* quota/private */ }
    setState({ authenticated: false, username: null });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ ...state, login, logout, loading }),
    [state, login, logout, loading]
  );

  // Don't render until we've checked the stored token
  if (!initialized) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
