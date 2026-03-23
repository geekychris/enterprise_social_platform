import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  userId: number | null;
  username: string | null;
  debugUserId: number | null;
  debugMode: boolean;
  isAdmin: boolean;

  login: (token: string, userId: number, username: string, isAdmin: boolean) => void;
  loginDebug: (userId: number) => void;
  logout: () => void;
  setDebugMode: (on: boolean) => void;
  setIsAdmin: (admin: boolean) => void;
  getEffectiveUserId: () => number | null;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      userId: null,
      username: null,
      debugUserId: null,
      debugMode: false,
      isAdmin: false,

      login: (token, userId, username, isAdmin) =>
        set({ token, userId, username, debugUserId: null, isAdmin }),

      loginDebug: (userId) =>
        set({
          debugUserId: userId,
          userId,
          username: `user-${userId}`,
          token: null,
          debugMode: true,
          isAdmin: false,
        }),

      logout: () =>
        set({
          token: null,
          userId: null,
          username: null,
          debugUserId: null,
          isAdmin: false,
        }),

      setDebugMode: (on) => set({ debugMode: on }),

      setIsAdmin: (admin) => set({ isAdmin: admin }),

      getEffectiveUserId: () => {
        const s = get();
        return s.debugUserId ?? s.userId;
      },
    }),
    { name: 'social-auth' },
  ),
);
