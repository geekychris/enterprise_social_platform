import { useState, useRef } from 'react';
import { NavLink, Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../hooks/useAuth';
import api from '../../api/client';
import type { GroupDto, PageDto, AuthorDto } from '../../api/types';

const navItems = [
  { to: '/', label: 'Feed', icon: HomeIcon },
  { to: '/search', label: 'Search', icon: SearchIcon },
  { to: '/messages', label: 'Messages', icon: MessageIcon },
  { to: '/about', label: 'About', icon: AboutIcon },
];

export default function Sidebar() {
  const { userId } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [showCreateGroup, setShowCreateGroup] = useState(false);
  const [showCreatePage, setShowCreatePage] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newAvatarUrl, setNewAvatarUrl] = useState<string | null>(null);
  const [uploadingImage, setUploadingImage] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const resetForm = () => {
    setNewName('');
    setNewDesc('');
    setNewAvatarUrl(null);
  };

  const handleImageUpload = async (file: File) => {
    setUploadingImage(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const { data } = await api.post('/attachments/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setNewAvatarUrl(data.fileUrl);
    } catch {
      // silently fail
    } finally {
      setUploadingImage(false);
    }
  };

  const createGroup = useMutation({
    mutationFn: () =>
      api.post('/groups', {
        name: newName,
        description: newDesc,
        visibility: 'PUBLIC',
        ...(newAvatarUrl ? { avatarUrl: newAvatarUrl } : {}),
      }),
    onSuccess: (res) => {
      setShowCreateGroup(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['my-groups'] });
      navigate(`/group/${res.data.id}`);
    },
  });

  const createPage = useMutation({
    mutationFn: () =>
      api.post('/pages', {
        name: newName,
        description: newDesc,
        visibility: 'PUBLIC',
        ...(newAvatarUrl ? { avatarUrl: newAvatarUrl } : {}),
      }),
    onSuccess: (res) => {
      setShowCreatePage(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['my-pages'] });
      navigate(`/page/${res.data.id}`);
    },
  });

  const { data: unreadCount } = useQuery<number>({
    queryKey: ['unread-count'],
    queryFn: async () => {
      const { data } = await api.get('/messages/unread-count');
      return data?.unreadCount ?? data ?? 0;
    },
    refetchInterval: 15000,
  });

  const { data: myGroups } = useQuery<GroupDto[]>({
    queryKey: ['my-groups'],
    queryFn: async () => {
      const { data } = await api.get('/groups/mine');
      return data;
    },
  });

  const { data: myPages } = useQuery<PageDto[]>({
    queryKey: ['my-pages'],
    queryFn: async () => {
      const { data } = await api.get('/pages/mine');
      return data;
    },
  });

  const { data: following } = useQuery<AuthorDto[]>({
    queryKey: ['following', userId],
    queryFn: async () => {
      const { data } = await api.get(`/users/${userId}/following`);
      return data;
    },
    enabled: !!userId,
  });

  const { data: currentUser } = useQuery<{ admin?: boolean }>({
    queryKey: ['current-user', userId],
    queryFn: () => api.get(`/users/${userId}`).then((r) => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  });
  const isAdmin = currentUser?.admin;

  const allItems = [
    ...navItems,
    ...(userId
      ? [{ to: `/profile/${userId}`, label: 'Profile', icon: UserIcon }]
      : []),
    ...(isAdmin
      ? [{ to: '/admin', label: 'Admin', icon: ShieldIcon }]
      : []),
  ];

  const imageUploadSection = (
    <>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) handleImageUpload(file);
          e.target.value = '';
        }}
      />
      {newAvatarUrl ? (
        <div className="flex items-center gap-2">
          <img src={newAvatarUrl} alt="" className="w-8 h-8 rounded object-cover" />
          <button
            type="button"
            onClick={() => setNewAvatarUrl(null)}
            className="text-xs text-red-500 hover:text-red-600"
          >
            Remove
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadingImage}
          className="text-xs text-primary-500 hover:text-primary-600 flex items-center gap-1"
        >
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          {uploadingImage ? 'Uploading...' : 'Add image'}
        </button>
      )}
    </>
  );

  return (
    <aside className="fixed left-0 top-14 bottom-0 w-60 bg-white border-r border-gray-100 pt-4 hidden lg:block z-20 overflow-y-auto">
      <nav className="px-2 space-y-1">
        {allItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-50 text-primary-500'
                  : 'text-gray-600 hover:bg-gray-50'
              }`
            }
          >
            <Icon />
            <span className="flex-1">{label}</span>
            {label === 'Messages' && unreadCount != null && unreadCount > 0 && (
              <span className="w-5 h-5 bg-primary-500 text-white rounded-full text-[10px] flex items-center justify-center font-bold">
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </NavLink>
        ))}
      </nav>

      {/* My Groups */}
      <div className="px-4 mt-6">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
            My Groups
          </h3>
          <button
            onClick={() => { setShowCreateGroup(!showCreateGroup); setShowCreatePage(false); resetForm(); }}
            className="text-xs text-primary-500 hover:text-primary-600 font-medium"
          >
            + New
          </button>
        </div>
        {showCreateGroup && (
          <div className="mb-2 p-2 bg-gray-50 rounded-lg space-y-1.5">
            <input
              type="text"
              placeholder="Group name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="w-full text-sm border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
            <input
              type="text"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={(e) => setNewDesc(e.target.value)}
              className="w-full text-sm border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
            {imageUploadSection}
            <div className="flex gap-1">
              <button
                onClick={() => createGroup.mutate()}
                disabled={!newName.trim() || createGroup.isPending || uploadingImage}
                className="btn-primary text-xs px-3 py-1"
              >
                {createGroup.isPending ? 'Creating...' : 'Create'}
              </button>
              <button
                onClick={() => { setShowCreateGroup(false); resetForm(); }}
                className="text-xs text-gray-500 px-2 py-1 hover:bg-gray-200 rounded"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
        {myGroups && myGroups.length > 0 ? (
          <div className="space-y-0.5">
            {myGroups.map((group) => (
              <Link
                key={group.id}
                to={`/group/${group.id}`}
                className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-gray-50 transition-colors"
              >
                {group.avatarUrl ? (
                  <img
                    src={group.avatarUrl}
                    alt=""
                    className="w-7 h-7 rounded-md object-cover shrink-0"
                  />
                ) : (
                  <div className="w-7 h-7 bg-emerald-500 text-white rounded-md flex items-center justify-center text-[10px] font-bold shrink-0">
                    {group.name?.[0]?.toUpperCase() ?? 'G'}
                  </div>
                )}
                <span className="text-sm text-gray-700 truncate">
                  {group.name}
                </span>
              </Link>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 px-1">No groups yet</p>
        )}
      </div>

      {/* My Pages */}
      <div className="px-4 mt-5">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
            My Pages
          </h3>
          <button
            onClick={() => { setShowCreatePage(!showCreatePage); setShowCreateGroup(false); resetForm(); }}
            className="text-xs text-primary-500 hover:text-primary-600 font-medium"
          >
            + New
          </button>
        </div>
        {showCreatePage && (
          <div className="mb-2 p-2 bg-gray-50 rounded-lg space-y-1.5">
            <input
              type="text"
              placeholder="Page name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              className="w-full text-sm border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
            <input
              type="text"
              placeholder="Description (optional)"
              value={newDesc}
              onChange={(e) => setNewDesc(e.target.value)}
              className="w-full text-sm border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
            {imageUploadSection}
            <div className="flex gap-1">
              <button
                onClick={() => createPage.mutate()}
                disabled={!newName.trim() || createPage.isPending || uploadingImage}
                className="btn-primary text-xs px-3 py-1"
              >
                {createPage.isPending ? 'Creating...' : 'Create'}
              </button>
              <button
                onClick={() => { setShowCreatePage(false); resetForm(); }}
                className="text-xs text-gray-500 px-2 py-1 hover:bg-gray-200 rounded"
              >
                Cancel
              </button>
            </div>
          </div>
        )}
        {myPages && myPages.length > 0 ? (
          <div className="space-y-0.5">
            {myPages.map((page) => (
              <Link
                key={page.id}
                to={`/page/${page.id}`}
                className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-gray-50 transition-colors"
              >
                {page.avatarUrl ? (
                  <img
                    src={page.avatarUrl}
                    alt=""
                    className="w-7 h-7 rounded-md object-cover shrink-0"
                  />
                ) : (
                  <div className="w-7 h-7 bg-violet-500 text-white rounded-md flex items-center justify-center text-[10px] font-bold shrink-0">
                    {page.name?.[0]?.toUpperCase() ?? 'P'}
                  </div>
                )}
                <span className="text-sm text-gray-700 truncate">
                  {page.name}
                </span>
              </Link>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 px-1">No pages yet</p>
        )}
      </div>

      {/* Friends */}
      <div className="px-4 mt-5 pb-4">
        <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
          Friends
        </h3>
        {following && following.length > 0 ? (
          <div className="space-y-0.5">
            {following.slice(0, 10).map((user) => (
              <div
                key={user.id}
                className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <Link
                  to={`/messages/${user.id}`}
                  className="flex items-center gap-2.5 flex-1 min-w-0"
                >
                  {user.avatarUrl ? (
                    <img
                      src={user.avatarUrl}
                      alt=""
                      className="w-7 h-7 rounded-full object-cover shrink-0"
                    />
                  ) : (
                    <div className="w-7 h-7 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold shrink-0">
                      {user.displayName?.[0]?.toUpperCase() ??
                        user.username?.[0]?.toUpperCase() ??
                        '?'}
                    </div>
                  )}
                  <span className="text-sm text-gray-700 truncate flex-1">
                    {user.displayName || user.username}
                  </span>
                </Link>
                <Link
                  to={`/profile/${user.id}`}
                  className="shrink-0 p-1 text-gray-400 hover:text-primary-500 rounded transition-colors"
                  title="View profile"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                  </svg>
                </Link>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-gray-400 px-1">No friends yet</p>
        )}
      </div>
    </aside>
  );
}

function HomeIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
    </svg>
  );
}

function MessageIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
    </svg>
  );
}

function UserIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
    </svg>
  );
}

function AboutIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  );
}

function ShieldIcon() {
  return (
    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
    </svg>
  );
}
