import { useState, useRef, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { useAuthStore } from '../../stores/authStore';
import SearchBar from '../search/SearchBar';
import api from '../../api/client';

export default function Header() {
  const { username, userId, debugMode, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [menuOpen, setMenuOpen] = useState(false);
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const [switchSearch, setSwitchSearch] = useState('');
  const menuRef = useRef<HTMLDivElement>(null);
  const switcherRef = useRef<HTMLDivElement>(null);

  const { data: unreadCount } = useQuery<number>({
    queryKey: ['unread-count'],
    queryFn: async () => {
      const { data } = await api.get('/messages/unread-count');
      return data?.unreadCount ?? data ?? 0;
    },
    refetchInterval: 15000,
  });

  // Current user details (avatar, display name)
  const { data: currentUser } = useQuery<{
    id: number; username: string; displayName: string; avatarUrl: string | null; admin: boolean;
  }>({
    queryKey: ['current-user-header', userId],
    queryFn: () => api.get(`/users/${userId}`).then(r => r.data),
    enabled: !!userId,
    staleTime: 60000,
  });

  // Fetch users for account switcher (debug mode)
  const { data: switcherUsers } = useQuery<{
    id: number; username: string; displayName: string; avatarUrl: string | null;
  }[]>({
    queryKey: ['switcher-users', switchSearch],
    queryFn: () => api.get('/users/search', { params: { q: switchSearch || '' } }).then(r =>
      (r.data as any[]).slice(0, 12)
    ),
    enabled: debugMode && switcherOpen,
    staleTime: 30000,
  });

  const switchAccount = (targetUserId: number) => {
    useAuthStore.getState().loginDebug(targetUserId);
    setSwitcherOpen(false);
    setSwitchSearch('');
    // Clear all cached queries so everything reloads for the new user
    queryClient.clear();
    navigate('/');
  };

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node))
        setMenuOpen(false);
      if (switcherRef.current && !switcherRef.current.contains(e.target as Node))
        setSwitcherOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  return (
    <header className="fixed top-0 left-0 right-0 h-14 bg-white shadow-sm border-b border-gray-100 z-30 flex items-center px-4 gap-4">
      {/* Logo */}
      <Link
        to="/"
        className="flex items-center gap-2 font-bold text-primary-500 text-xl shrink-0"
      >
        <div className="w-8 h-8 bg-primary-500 text-white rounded-lg flex items-center justify-center text-sm font-bold">
          S
        </div>
        <span className="hidden sm:inline">Social</span>
      </Link>

      {/* Search */}
      <div className="flex-1 max-w-xl">
        <SearchBar
          onSearch={(q) => navigate(`/search?q=${encodeURIComponent(q)}`)}
        />
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Account switcher (debug mode only) */}
      {debugMode && (
        <div className="relative" ref={switcherRef}>
          <button
            onClick={() => setSwitcherOpen(!switcherOpen)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 bg-yellow-50 border border-yellow-200 text-yellow-800 rounded-lg text-xs font-medium hover:bg-yellow-100 transition-colors"
            title="Switch debug account"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
            </svg>
            Switch
          </button>

          {switcherOpen && (
            <div className="absolute right-0 top-full mt-1 w-72 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
              <div className="p-2 border-b border-gray-100">
                <input
                  value={switchSearch}
                  onChange={e => setSwitchSearch(e.target.value)}
                  placeholder="Search users..."
                  autoFocus
                  className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                />
              </div>
              <div className="max-h-80 overflow-y-auto">
                {switcherUsers?.map(user => (
                  <button
                    key={user.id}
                    onClick={() => switchAccount(user.id)}
                    className={`w-full flex items-center gap-3 px-3 py-2.5 hover:bg-gray-50 text-left transition-colors ${
                      user.id === userId ? 'bg-primary-50' : ''
                    }`}
                  >
                    {user.avatarUrl ? (
                      <img src={user.avatarUrl} className="w-8 h-8 rounded-full object-cover shrink-0" alt="" />
                    ) : (
                      <div className="w-8 h-8 bg-primary-400 text-white rounded-full flex items-center justify-center text-xs font-bold shrink-0">
                        {user.displayName?.[0]?.toUpperCase() ?? '?'}
                      </div>
                    )}
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-medium text-gray-900 truncate">{user.displayName}</div>
                      <div className="text-xs text-gray-400">@{user.username}</div>
                    </div>
                    {user.id === userId && (
                      <span className="text-[10px] bg-primary-100 text-primary-600 px-1.5 py-0.5 rounded font-medium shrink-0">
                        current
                      </span>
                    )}
                  </button>
                ))}
                {switcherUsers?.length === 0 && (
                  <div className="px-4 py-6 text-center text-sm text-gray-400">No users found</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Messages icon */}
      <Link
        to="/messages"
        className="relative p-2 text-gray-500 hover:text-primary-500 hover:bg-gray-50 rounded-lg transition-colors"
      >
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
        </svg>
        {unreadCount != null && unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 w-4.5 h-4.5 min-w-[18px] bg-red-500 text-white rounded-full text-[10px] flex items-center justify-center font-bold px-1">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </Link>

      {/* User menu */}
      <div className="relative" ref={menuRef}>
        <button
          onClick={() => setMenuOpen(!menuOpen)}
          className="flex items-center gap-2 hover:bg-gray-50 rounded-full p-1 pr-3 transition-colors"
        >
          {currentUser?.avatarUrl ? (
            <img src={currentUser.avatarUrl} className="w-8 h-8 rounded-full object-cover" alt="" />
          ) : (
            <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold">
              {(currentUser?.displayName ?? username)?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
          <span className="hidden sm:inline text-sm font-medium text-gray-700">
            {currentUser?.displayName ?? username}
          </span>
          {debugMode && (
            <span className="text-xs bg-yellow-100 text-yellow-800 px-1.5 py-0.5 rounded font-medium">
              DEBUG
            </span>
          )}
        </button>

        {menuOpen && (
          <div className="absolute right-0 top-full mt-1 w-56 bg-white rounded-lg shadow-lg border border-gray-100 py-1 z-50">
            {userId && (
              <Link
                to={`/profile/${userId}`}
                onClick={() => setMenuOpen(false)}
                className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
              >
                My Profile
              </Link>
            )}
            <button
              onClick={() => {
                logout();
                queryClient.clear();
                navigate('/login');
              }}
              className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              Log Out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
