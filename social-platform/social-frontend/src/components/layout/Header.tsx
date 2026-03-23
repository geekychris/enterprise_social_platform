import { useState, useRef, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import SearchBar from '../search/SearchBar';
import api from '../../api/client';

export default function Header() {
  const { username, userId, debugMode, logout } = useAuth();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const { data: unreadCount } = useQuery<number>({
    queryKey: ['unread-count'],
    queryFn: async () => {
      const { data } = await api.get('/messages/unread-count');
      return data?.unreadCount ?? data ?? 0;
    },
    refetchInterval: 15000,
  });

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node))
        setMenuOpen(false);
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
          <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold">
            {username?.[0]?.toUpperCase() ?? '?'}
          </div>
          <span className="hidden sm:inline text-sm font-medium text-gray-700">
            {username}
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
