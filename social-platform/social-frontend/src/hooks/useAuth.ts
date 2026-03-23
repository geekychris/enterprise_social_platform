import { useAuthStore } from '../stores/authStore';

export function useAuth() {
  const store = useAuthStore();
  return {
    isAuthenticated: !!store.token || !!store.debugUserId,
    userId: store.getEffectiveUserId(),
    username: store.username,
    debugMode: store.debugMode,
    isAdmin: store.isAdmin,
    login: store.login,
    loginDebug: store.loginDebug,
    logout: store.logout,
    setDebugMode: store.setDebugMode,
    setIsAdmin: store.setIsAdmin,
  };
}
