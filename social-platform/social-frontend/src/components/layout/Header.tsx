import { useState, useRef, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import { useAuthStore } from '../../stores/authStore';
import { useTenantBranding } from '../../hooks/useTenantBranding';
import SearchBar from '../search/SearchBar';
import api from '../../api/client';

export default function Header() {
  const { username, userId, debugMode, logout } = useAuth();
  const branding = useTenantBranding();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [menuOpen, setMenuOpen] = useState(false);
  const [switcherOpen, setSwitcherOpen] = useState(false);
  const [switchSearch, setSwitchSearch] = useState('');
  const [adminsOnly, setAdminsOnly] = useState(false);
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
    queryFn: () => api.get('/users/search', { params: { q: switchSearch || '' } }).then(r => {
      const users = r.data as any[];
      // Sort admins first, then take top results
      users.sort((a: any, b: any) => (a.admin === b.admin ? 0 : a.admin ? -1 : 1));
      return users.slice(0, 20);
    }),
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
    <header className="fixed top-0 left-0 right-0 h-20 bg-white shadow-sm border-b border-gray-100 z-30 flex items-center px-4 gap-4">
      {/* Logo */}
      <Link
        to="/"
        className="flex items-center shrink-0"
      >
        {branding.logoUrl ? (
          <img src={branding.logoUrl} alt={branding.companyName} className="h-16 w-auto object-contain" />
        ) : (
          <>
            <img src="/worksphere-logo.jpg" alt={branding.companyName} className="h-10 w-auto object-contain" />
            <span className="ml-2 text-lg font-bold hidden sm:inline" style={{ color: branding.primaryColor }}>
              {branding.companyName}
            </span>
          </>
        )}
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
              <div className="p-2 border-b border-gray-100 space-y-2">
                <input
                  value={switchSearch}
                  onChange={e => setSwitchSearch(e.target.value)}
                  placeholder="Search users..."
                  autoFocus
                  className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                />
                <label className="flex items-center gap-2 px-1 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={adminsOnly}
                    onChange={e => setAdminsOnly(e.target.checked)}
                    className="rounded border-gray-300 text-primary-500 focus:ring-primary-500"
                  />
                  <span className="text-xs text-gray-600">Admins only</span>
                </label>
              </div>
              <div className="max-h-80 overflow-y-auto">
                {(switcherUsers ?? [])
                  .filter((user: any) => !adminsOnly || user.admin)
                  .sort((a: any, b: any) => (a.admin === b.admin ? 0 : a.admin ? -1 : 1))
                  .map((user: any) => (
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
                      <div className="text-sm font-medium text-gray-900 truncate flex items-center gap-1.5">
                        {user.displayName}
                        {user.admin && (
                          <span className="text-[9px] bg-red-100 text-red-700 px-1 py-0.5 rounded font-bold leading-none">ADMIN</span>
                        )}
                      </div>
                      <div className="text-xs text-gray-400">@{user.username}</div>
                    </div>
                    {user.id === userId && (
                      <span className="text-[10px] bg-primary-100 text-primary-600 px-1.5 py-0.5 rounded font-medium shrink-0">
                        current
                      </span>
                    )}
                  </button>
                ))}
                {((switcherUsers ?? []).filter((u: any) => !adminsOnly || u.admin).length === 0) && (
                  <div className="px-4 py-6 text-center text-sm text-gray-400">
                    {adminsOnly ? 'No admin users found' : 'No users found'}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Notifications bell */}
      <NotificationBell />

      {/* Messages icon */}
      <Link
        to="/messages"
        className="relative p-2 text-white/70 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
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
          <span className="hidden sm:inline text-sm font-semibold text-white">
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

/* ────────────────────────── Notification Bell ────────────────────────── */

function NotificationBell() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: notifUnread } = useQuery<{ count: number }>({
    queryKey: ['notif-unread'],
    queryFn: () => api.get('/notifications/unread-count').then(r => r.data),
    refetchInterval: 10000,
  });

  const { data: notifications } = useQuery<any[]>({
    queryKey: ['notifications'],
    queryFn: () => api.get('/notifications?limit=20').then(r => r.data),
    enabled: open,
    staleTime: 5000,
  });

  const markRead = useMutation({
    mutationFn: () => api.post('/notifications/mark-read'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notif-unread'] });
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  const handleOpen = () => {
    setOpen(!open);
    if (!open && (notifUnread?.count ?? 0) > 0) {
      markRead.mutate();
    }
  };

  const getNotifLink = (n: any): string => {
    switch (n.type) {
      case 'MENTION':
      case 'COMMENT':
        if (n.targetType === 'POST' && n.targetId) return `/post/${n.targetId}`;
        return '/';
      case 'REACTION':
        if (n.targetType === 'POST' && n.targetId) return `/post/${n.targetId}`;
        return '/';
      case 'FRIEND_REQUEST':
      case 'FRIEND_ACCEPTED':
        return n.actorId ? `/profile/${n.actorId}` : '/';
      default:
        return '/';
    }
  };

  const getNotifIcon = (type: string) => {
    switch (type) {
      case 'MENTION': return '@';
      case 'COMMENT': return '\uD83D\uDCAC';
      case 'REACTION': return '\u2764\uFE0F';
      case 'FRIEND_REQUEST': return '\uD83D\uDC64';
      case 'FRIEND_ACCEPTED': return '\u2705';
      default: return '\uD83D\uDD14';
    }
  };

  const count = notifUnread?.count ?? 0;

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={handleOpen}
        className="relative p-2 text-white/70 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
      >
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
        </svg>
        {count > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] bg-red-500 text-white rounded-full text-[10px] flex items-center justify-center font-bold px-1">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 w-80 bg-white rounded-xl shadow-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
            <span className="text-sm font-semibold text-gray-900">Notifications</span>
            {count > 0 && (
              <button
                onClick={() => markRead.mutate()}
                className="text-xs text-primary-500 hover:underline"
              >
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {notifications && notifications.length > 0 ? (
              notifications.map((n: any) => (
                <button
                  key={String(n.id)}
                  onClick={() => {
                    setOpen(false);
                    navigate(getNotifLink(n));
                  }}
                  className={`w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors ${
                    !n.read ? 'bg-primary-50/50' : ''
                  }`}
                >
                  {n.actorAvatarUrl ? (
                    <img src={n.actorAvatarUrl} className="w-9 h-9 rounded-full object-cover shrink-0" alt="" />
                  ) : (
                    <div className="w-9 h-9 bg-gray-200 rounded-full flex items-center justify-center text-sm shrink-0">
                      {getNotifIcon(n.type)}
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-gray-900">{n.message}</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {formatTime(n.createdAt)}
                    </p>
                  </div>
                  {!n.read && (
                    <div className="w-2 h-2 bg-primary-500 rounded-full shrink-0 mt-2" />
                  )}
                </button>
              ))
            ) : (
              <div className="px-4 py-8 text-center text-sm text-gray-400">
                No notifications yet
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - d.getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return d.toLocaleDateString();
}
