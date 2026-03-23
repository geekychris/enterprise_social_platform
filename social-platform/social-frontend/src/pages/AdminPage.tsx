import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import api from '../api/client';

type Tab = 'dashboard' | 'engagement' | 'users' | 'content' | 'groups-pages';

const tabs: { key: Tab; label: string }[] = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'engagement', label: 'Engagement' },
  { key: 'users', label: 'Users' },
  { key: 'content', label: 'Content' },
  { key: 'groups-pages', label: 'Groups & Pages' },
];

export default function AdminPage() {
  const { userId } = useAuth();
  const [activeTab, setActiveTab] = useState<Tab>('dashboard');

  const { data: currentUser, isLoading: loadingUser } = useQuery<{ admin?: boolean }>({
    queryKey: ['current-user', userId],
    queryFn: () => api.get(`/users/${userId}`).then((r) => r.data),
    enabled: !!userId,
    staleTime: 5 * 60 * 1000,
  });

  if (loadingUser) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  if (!currentUser?.admin) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <div className="text-4xl mb-4">&#128683;</div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Access Denied</h2>
          <p className="text-gray-500">You do not have admin privileges.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-4">Admin Dashboard</h1>

      {/* Tab bar */}
      <div className="flex border-b border-gray-200 mb-6">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-3 text-sm font-medium transition-colors -mb-px ${
              activeTab === tab.key
                ? 'border-b-2 border-primary-500 text-primary-500'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'dashboard' && <DashboardTab />}
      {activeTab === 'engagement' && <EngagementTab />}
      {activeTab === 'users' && <UsersTab />}
      {activeTab === 'content' && <ContentTab />}
      {activeTab === 'groups-pages' && <GroupsPagesTab />}
    </div>
  );
}

/* ────────────────────────── Dashboard Tab ────────────────────────── */

function DashboardTab() {
  const { data: stats, isLoading: loadingStats } = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: () => api.get('/admin/dashboard').then((r) => r.data),
  });

  const { data: dauMau } = useQuery({
    queryKey: ['admin-dau-mau'],
    queryFn: () => api.get('/admin/analytics/dau-mau').then((r) => r.data),
  });

  const { data: activity } = useQuery({
    queryKey: ['admin-activity'],
    queryFn: () => api.get('/admin/analytics/user-activity').then((r) => r.data),
  });

  const { data: topUsers } = useQuery({
    queryKey: ['admin-top-users'],
    queryFn: () => api.get('/admin/analytics/top-users?period=7d&limit=10').then((r) => r.data),
  });

  const { data: topGroups } = useQuery({
    queryKey: ['admin-top-groups'],
    queryFn: () => api.get('/admin/analytics/top-groups?limit=10').then((r) => r.data),
  });

  const { data: topPages } = useQuery({
    queryKey: ['admin-top-pages'],
    queryFn: () => api.get('/admin/analytics/top-pages?limit=10').then((r) => r.data),
  });

  const { data: growth } = useQuery({
    queryKey: ['admin-growth'],
    queryFn: () => api.get('/admin/analytics/growth').then((r) => r.data),
  });

  const { data: system } = useQuery({
    queryKey: ['admin-system'],
    queryFn: () => api.get('/admin/system').then((r) => r.data),
  });

  if (loadingStats) {
    return <div className="text-gray-400 py-10 text-center">Loading dashboard...</div>;
  }

  return (
    <div className="space-y-6">
      {/* Stats Cards */}
      {stats && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            label="Total Users"
            value={stats.totalUsers}
            badge={stats.newUsersLast7d != null ? `+${stats.newUsersLast7d} last 7d` : undefined}
          />
          <StatCard
            label="Total Posts"
            value={stats.totalPosts}
            badge={stats.postsLast24h != null ? `${stats.postsLast24h} last 24h` : undefined}
          />
          <StatCard label="Total Groups" value={stats.totalGroups} />
          <StatCard label="Total Messages" value={stats.totalMessages} />
        </div>
      )}

      {/* DAU / MAU */}
      {dauMau && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="DAU (Today)" value={dauMau.dau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.dau) / Number(dauMau.totalUsers)) * 100) : 0}% of users`} />
            <StatCard label="WAU (7 days)" value={dauMau.wau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.wau) / Number(dauMau.totalUsers)) * 100) : 0}% of users`} />
            <StatCard label="MAU (30 days)" value={dauMau.mau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.mau) / Number(dauMau.totalUsers)) * 100) : 0}% of users`} />
            <StatCard label="Stickiness" value={dauMau.mau && Number(dauMau.mau) > 0 ? `${Math.round((Number(dauMau.dau) / Number(dauMau.mau)) * 100)}%` : '0%'} badge="DAU/MAU ratio" />
          </div>
          {dauMau.dauTrend && Array.isArray(dauMau.dauTrend) && dauMau.dauTrend.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Daily Active Users (14 days)</h3>
              <BarChart data={dauMau.dauTrend} valueKey="active_users" labelKey="date" color="bg-emerald-500" />
            </div>
          )}
        </>
      )}

      {/* Activity Chart */}
      {activity && Array.isArray(activity) && activity.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Post Activity (Last 30 Days)</h3>
          <BarChart data={activity} valueKey="postCount" labelKey="date" />
        </div>
      )}

      {/* Top Users */}
      {topUsers && Array.isArray(topUsers) && topUsers.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Users (7d)</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2 w-10">#</th>
                <th className="pb-2">User</th>
                <th className="pb-2 text-right">Posts</th>
              </tr>
            </thead>
            <tbody>
              {topUsers.map((u: any, i: number) => (
                <tr key={String(u.userId ?? u.id ?? i)} className="even:bg-gray-50">
                  <td className="py-2 text-gray-400">{i + 1}</td>
                  <td className="py-2">
                    <Link
                      to={`/profile/${String(u.userId ?? u.id)}`}
                      className="flex items-center gap-2 text-primary-600 hover:underline"
                    >
                      {u.avatarUrl ? (
                        <img src={u.avatarUrl} alt="" className="w-6 h-6 rounded-full object-cover" />
                      ) : (
                        <div className="w-6 h-6 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-bold">
                          {(u.displayName ?? u.username ?? '?')[0]?.toUpperCase()}
                        </div>
                      )}
                      {u.displayName || u.username}
                    </Link>
                  </td>
                  <td className="py-2 text-right font-medium">{u.postCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Top Groups */}
      {topGroups && Array.isArray(topGroups) && topGroups.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Groups</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2 w-10">#</th>
                <th className="pb-2">Name</th>
                <th className="pb-2 text-right">Members</th>
                <th className="pb-2 text-right">Posts</th>
              </tr>
            </thead>
            <tbody>
              {topGroups.map((g: any, i: number) => (
                <tr key={String(g.groupId ?? g.id ?? i)} className="even:bg-gray-50">
                  <td className="py-2 text-gray-400">{i + 1}</td>
                  <td className="py-2">
                    <Link to={`/group/${String(g.groupId ?? g.id)}`} className="text-primary-600 hover:underline">
                      {g.name}
                    </Link>
                  </td>
                  <td className="py-2 text-right">{g.memberCount}</td>
                  <td className="py-2 text-right font-medium">{g.postCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Top Pages */}
      {topPages && Array.isArray(topPages) && topPages.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Pages</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2 w-10">#</th>
                <th className="pb-2">Name</th>
                <th className="pb-2 text-right">Followers</th>
                <th className="pb-2 text-right">Posts</th>
              </tr>
            </thead>
            <tbody>
              {topPages.map((p: any, i: number) => (
                <tr key={String(p.pageId ?? p.id ?? i)} className="even:bg-gray-50">
                  <td className="py-2 text-gray-400">{i + 1}</td>
                  <td className="py-2">
                    <Link to={`/page/${String(p.pageId ?? p.id)}`} className="text-primary-600 hover:underline">
                      {p.name}
                    </Link>
                  </td>
                  <td className="py-2 text-right">{p.followerCount}</td>
                  <td className="py-2 text-right font-medium">{p.postCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Growth Chart */}
      {growth && Array.isArray(growth) && growth.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">User Growth (Weekly Signups)</h3>
          <BarChart data={growth} valueKey="signups" labelKey="week" />
        </div>
      )}

      {/* System Health */}
      {system && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">System Health</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <MiniCard label="Upload Dir Size" value={system.uploadDirSize ?? 'N/A'} />
            <MiniCard label="File Count" value={system.fileCount ?? 'N/A'} />
            <MiniCard label="DB Size" value={system.dbSize ?? 'N/A'} />
            <MiniCard label="Duplicate Attachments" value={system.duplicateAttachments ?? 'N/A'} />
          </div>
        </div>
      )}
    </div>
  );
}

/* ────────────────────────── Engagement Tab ────────────────────────── */

function EngagementTab() {
  const [entityType, setEntityType] = useState('group');
  const [entityId, setEntityId] = useState('');
  const [days, setDays] = useState(30);
  const [selectedEntity, setSelectedEntity] = useState<{ type: string; id: string } | null>(null);

  // Fetch groups and pages for the selector
  const { data: groups } = useQuery({
    queryKey: ['admin-all-groups'],
    queryFn: () => api.get('/admin/groups?size=100').then((r) => r.data),
  });
  const { data: pages } = useQuery({
    queryKey: ['admin-all-pages'],
    queryFn: () => api.get('/admin/pages?size=100').then((r) => r.data),
  });
  const { data: topUsers } = useQuery({
    queryKey: ['admin-top-users-engagement'],
    queryFn: () => api.get('/admin/analytics/top-users?period=90d&limit=20').then((r) => r.data),
  });

  // Fetch engagement data
  const { data: engagement, isLoading } = useQuery({
    queryKey: ['admin-engagement', selectedEntity?.type, selectedEntity?.id, days],
    queryFn: () =>
      api
        .get('/admin/analytics/engagement', {
          params: { entityType: selectedEntity!.type, entityId: selectedEntity!.id, days },
        })
        .then((r) => r.data),
    enabled: !!selectedEntity,
  });

  const handleSelect = () => {
    if (entityId) setSelectedEntity({ type: entityType, id: entityId });
  };

  const quickSelect = (type: string, id: string) => {
    setEntityType(type);
    setEntityId(id);
    setSelectedEntity({ type, id });
  };

  return (
    <div className="space-y-6">
      {/* Selector */}
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-sm font-semibold text-gray-700 mb-3">
          View Engagement Over Time
        </h3>
        <div className="flex items-center gap-3 flex-wrap">
          <select
            value={entityType}
            onChange={(e) => { setEntityType(e.target.value); setEntityId(''); }}
            className="text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-1 focus:ring-primary-500"
          >
            <option value="group">Group</option>
            <option value="page">Page</option>
            <option value="user">User</option>
          </select>

          {entityType === 'group' && groups && (
            <select
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              className="text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white flex-1 min-w-[200px] focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              <option value="">Select a group...</option>
              {groups.map((g: any) => (
                <option key={String(g.id)} value={String(g.id)}>
                  {g.name} ({g.member_count ?? g.memberCount ?? 0} members)
                </option>
              ))}
            </select>
          )}
          {entityType === 'page' && pages && (
            <select
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              className="text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white flex-1 min-w-[200px] focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              <option value="">Select a page...</option>
              {pages.map((p: any) => (
                <option key={String(p.id)} value={String(p.id)}>
                  {p.name} ({p.follower_count ?? p.followerCount ?? 0} followers)
                </option>
              ))}
            </select>
          )}
          {entityType === 'user' && topUsers && (
            <select
              value={entityId}
              onChange={(e) => setEntityId(e.target.value)}
              className="text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white flex-1 min-w-[200px] focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              <option value="">Select a user...</option>
              {topUsers.map((u: any) => (
                <option key={String(u.author_id)} value={String(u.author_id)}>
                  {u.display_name || u.username} ({u.post_count} posts)
                </option>
              ))}
            </select>
          )}

          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="text-sm border border-gray-200 rounded-lg px-3 py-2 bg-white focus:outline-none focus:ring-1 focus:ring-primary-500"
          >
            <option value={7}>7 days</option>
            <option value={14}>14 days</option>
            <option value={30}>30 days</option>
            <option value={60}>60 days</option>
            <option value={90}>90 days</option>
          </select>

          <button onClick={handleSelect} disabled={!entityId} className="btn-primary text-sm px-4">
            View
          </button>
        </div>
      </div>

      {/* Quick links */}
      {groups && Array.isArray(groups) && groups.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Quick: Top Groups</h3>
          <div className="flex flex-wrap gap-2">
            {groups.slice(0, 8).map((g: any) => (
              <button
                key={String(g.id)}
                onClick={() => quickSelect('group', String(g.id))}
                className={`text-xs px-3 py-1.5 rounded-full border transition-colors ${
                  selectedEntity?.type === 'group' && selectedEntity?.id === String(g.id)
                    ? 'bg-primary-500 text-white border-primary-500'
                    : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                }`}
              >
                {g.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Loading */}
      {isLoading && (
        <div className="text-center py-10 text-gray-400">Loading engagement data...</div>
      )}

      {/* Results */}
      {engagement && (
        <div className="space-y-4">
          {/* Entity info */}
          {engagement.info && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-bold text-gray-900">
                    {engagement.info.name ?? engagement.info.display_name ?? engagement.info.username}
                  </h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {engagement.entityType === 'group' && `${engagement.info.members} members \u00b7 ${engagement.info.total_posts} total posts`}
                    {engagement.entityType === 'page' && `${engagement.info.followers} followers \u00b7 ${engagement.info.total_posts} total posts`}
                    {engagement.entityType === 'user' && `${engagement.info.followers} followers \u00b7 ${engagement.info.total_posts} posts \u00b7 ${engagement.info.total_comments} comments`}
                  </p>
                </div>
                {engagement.entityType === 'group' && (
                  <Link to={`/group/${engagement.entityId}`} className="text-xs text-primary-500 hover:underline">
                    View Group
                  </Link>
                )}
                {engagement.entityType === 'page' && (
                  <Link to={`/page/${engagement.entityId}`} className="text-xs text-primary-500 hover:underline">
                    View Page
                  </Link>
                )}
                {engagement.entityType === 'user' && (
                  <Link to={`/profile/${engagement.entityId}`} className="text-xs text-primary-500 hover:underline">
                    View Profile
                  </Link>
                )}
              </div>
            </div>
          )}

          {/* Activity chart */}
          {engagement.activity && Array.isArray(engagement.activity) && engagement.activity.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">
                {engagement.entityType === 'user' ? 'User Activity' : 'Posts'} ({days} days)
              </h3>
              <BarChart
                data={engagement.activity}
                valueKey={engagement.entityType === 'user' ? 'posts' : (engagement.activity[0]?.posts !== undefined ? 'posts' : 'cnt')}
                labelKey="date"
                color={engagement.entityType === 'group' ? 'bg-emerald-500' : engagement.entityType === 'page' ? 'bg-violet-500' : 'bg-primary-500'}
              />
              {engagement.entityType === 'user' && (
                <div className="mt-4 grid grid-cols-3 gap-4 text-center">
                  <div>
                    <div className="text-lg font-bold text-gray-900">
                      {engagement.activity.reduce((sum: number, d: any) => sum + Number(d.posts || 0), 0)}
                    </div>
                    <div className="text-xs text-gray-500">Posts</div>
                  </div>
                  <div>
                    <div className="text-lg font-bold text-gray-900">
                      {engagement.activity.reduce((sum: number, d: any) => sum + Number(d.comments || 0), 0)}
                    </div>
                    <div className="text-xs text-gray-500">Comments</div>
                  </div>
                  <div>
                    <div className="text-lg font-bold text-gray-900">
                      {engagement.activity.reduce((sum: number, d: any) => sum + Number(d.reactions || 0), 0)}
                    </div>
                    <div className="text-xs text-gray-500">Reactions</div>
                  </div>
                </div>
              )}
              {engagement.entityType === 'group' && engagement.activity[0]?.unique_posters !== undefined && (
                <div className="mt-4">
                  <h4 className="text-xs font-semibold text-gray-500 mb-2">Unique Posters</h4>
                  <BarChart
                    data={engagement.activity}
                    valueKey="unique_posters"
                    labelKey="date"
                    color="bg-amber-500"
                  />
                </div>
              )}
            </div>
          )}

          {/* Member growth (groups) */}
          {engagement.memberGrowth && Array.isArray(engagement.memberGrowth) && engagement.memberGrowth.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Member Growth</h3>
              <BarChart data={engagement.memberGrowth} valueKey="new_members" labelKey="date" color="bg-teal-500" />
            </div>
          )}
        </div>
      )}

      {!selectedEntity && !isLoading && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center text-gray-400 text-sm">
          Select a group, page, or user above to view their engagement metrics over time.
        </div>
      )}
    </div>
  );
}

/* ────────────────────────── Users Tab ────────────────────────── */

function UsersTab() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', page, search],
    queryFn: () =>
      api
        .get('/admin/users', { params: { page, size: 20, q: search || undefined } })
        .then((r) => r.data),
    placeholderData: (prev: any) => prev,
  });

  const toggleAdmin = useMutation({
    mutationFn: (userId: string) => api.put(`/admin/users/${userId}/admin`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
  });

  const users = data?.content ?? data?.users ?? (Array.isArray(data) ? data : []);
  const totalPages = data?.totalPages ?? 1;

  return (
    <div className="space-y-4">
      <input
        type="text"
        placeholder="Search users..."
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
          setPage(0);
        }}
        className="input-field max-w-sm"
      />

      {isLoading ? (
        <div className="text-gray-400 py-10 text-center">Loading users...</div>
      ) : (
        <>
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b bg-gray-50">
                  <th className="px-4 py-3">User</th>
                  <th className="px-4 py-3">Display Name</th>
                  <th className="px-4 py-3">Email</th>
                  <th className="px-4 py-3 text-right">Posts</th>
                  <th className="px-4 py-3 text-center">Admin</th>
                  <th className="px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u: any) => (
                  <tr key={String(u.id)} className="even:bg-gray-50 border-b border-gray-100">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {u.avatarUrl ? (
                          <img src={u.avatarUrl} alt="" className="w-7 h-7 rounded-full object-cover" />
                        ) : (
                          <div className="w-7 h-7 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-bold">
                            {(u.displayName ?? u.username ?? '?')[0]?.toUpperCase()}
                          </div>
                        )}
                        <span className="font-medium text-gray-900">{u.username}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-600">{u.displayName}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{u.email}</td>
                    <td className="px-4 py-3 text-right">{u.postCount ?? '-'}</td>
                    <td className="px-4 py-3 text-center">
                      {u.admin && (
                        <span className="inline-block bg-yellow-100 text-yellow-800 text-xs font-medium px-2 py-0.5 rounded">
                          Admin
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => toggleAdmin.mutate(String(u.id))}
                          className="text-xs text-primary-600 hover:underline"
                        >
                          {u.admin ? 'Remove Admin' : 'Make Admin'}
                        </button>
                        <Link
                          to={`/profile/${String(u.id)}`}
                          className="text-xs text-gray-500 hover:underline"
                        >
                          View
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                      No users found
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
            >
              Previous
            </button>
            <span className="text-sm text-gray-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={page + 1 >= totalPages}
              className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/* ────────────────────────── Content Tab ────────────────────────── */

function ContentTab() {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-posts', page],
    queryFn: () => api.get('/admin/posts', { params: { page, size: 20 } }).then((r) => r.data),
    placeholderData: (prev: any) => prev,
  });

  const deletePost = useMutation({
    mutationFn: (postId: string) => api.delete(`/admin/posts/${postId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-posts'] });
    },
  });

  const posts = data?.content ?? data?.posts ?? (Array.isArray(data) ? data : []);
  const totalPages = data?.totalPages ?? 1;

  const handleDelete = (postId: string) => {
    if (window.confirm('Are you sure you want to delete this post?')) {
      deletePost.mutate(postId);
    }
  };

  return (
    <div className="space-y-4">
      {isLoading ? (
        <div className="text-gray-400 py-10 text-center">Loading posts...</div>
      ) : (
        <>
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b bg-gray-50">
                  <th className="px-4 py-3">Author</th>
                  <th className="px-4 py-3">Content</th>
                  <th className="px-4 py-3">Target</th>
                  <th className="px-4 py-3 text-right">Reactions</th>
                  <th className="px-4 py-3 text-right">Comments</th>
                  <th className="px-4 py-3">Created</th>
                  <th className="px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {posts.map((p: any) => {
                  const totalReactions = p.reactionCounts
                    ? Object.values(p.reactionCounts as Record<string, number>).reduce(
                        (a: number, b: number) => a + b,
                        0,
                      )
                    : 0;
                  return (
                    <tr key={String(p.id)} className="even:bg-gray-50 border-b border-gray-100">
                      <td className="px-4 py-3">
                        <Link
                          to={`/profile/${String(p.author?.id ?? p.authorId)}`}
                          className="text-primary-600 hover:underline"
                        >
                          {p.author?.displayName ?? p.author?.username ?? 'Unknown'}
                        </Link>
                      </td>
                      <td className="px-4 py-3 text-gray-600 max-w-xs truncate">
                        {(p.content ?? '').slice(0, 80)}
                        {(p.content ?? '').length > 80 ? '...' : ''}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {p.targetType
                          ? `${p.targetType}:${String(p.targetId)}`
                          : 'Feed'}
                      </td>
                      <td className="px-4 py-3 text-right">{totalReactions}</td>
                      <td className="px-4 py-3 text-right">{p.commentCount ?? 0}</td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}
                      </td>
                      <td className="px-4 py-3">
                        <button
                          onClick={() => handleDelete(String(p.id))}
                          className="text-xs text-red-600 hover:underline"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {posts.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-gray-400">
                      No posts found
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
            >
              Previous
            </button>
            <span className="text-sm text-gray-500">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => p + 1)}
              disabled={page + 1 >= totalPages}
              className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/* ────────────────────────── Groups & Pages Tab ────────────────────────── */

function GroupsPagesTab() {
  const [groupPage, setGroupPage] = useState(0);
  const [pagePage, setPagePage] = useState(0);
  const queryClient = useQueryClient();

  const { data: groupsData, isLoading: loadingGroups } = useQuery({
    queryKey: ['admin-groups', groupPage],
    queryFn: () =>
      api.get('/admin/groups', { params: { page: groupPage, size: 20 } }).then((r) => r.data),
    placeholderData: (prev: any) => prev,
  });

  const { data: pagesData, isLoading: loadingPages } = useQuery({
    queryKey: ['admin-pages', pagePage],
    queryFn: () =>
      api.get('/admin/pages', { params: { page: pagePage, size: 20 } }).then((r) => r.data),
    placeholderData: (prev: any) => prev,
  });

  const deleteGroup = useMutation({
    mutationFn: (id: string) => api.delete(`/admin/groups/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-groups'] }),
  });

  const deletePage = useMutation({
    mutationFn: (id: string) => api.delete(`/admin/pages/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-pages'] }),
  });

  const groups = groupsData?.content ?? groupsData?.groups ?? (Array.isArray(groupsData) ? groupsData : []);
  const groupsTotalPages = groupsData?.totalPages ?? 1;
  const pages = pagesData?.content ?? pagesData?.pages ?? (Array.isArray(pagesData) ? pagesData : []);
  const pagesTotalPages = pagesData?.totalPages ?? 1;

  const handleDeleteGroup = (id: string) => {
    if (window.confirm('Are you sure you want to delete this group?')) {
      deleteGroup.mutate(id);
    }
  };

  const handleDeletePage = (id: string) => {
    if (window.confirm('Are you sure you want to delete this page?')) {
      deletePage.mutate(id);
    }
  };

  return (
    <div className="space-y-8">
      {/* Groups */}
      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-3">Groups</h3>
        {loadingGroups ? (
          <div className="text-gray-400 py-6 text-center">Loading groups...</div>
        ) : (
          <>
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b bg-gray-50">
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3 text-right">Members</th>
                    <th className="px-4 py-3 text-right">Posts</th>
                    <th className="px-4 py-3">Visibility</th>
                    <th className="px-4 py-3">Created</th>
                    <th className="px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {groups.map((g: any) => (
                    <tr key={String(g.id)} className="even:bg-gray-50 border-b border-gray-100">
                      <td className="px-4 py-3 font-medium text-gray-900">{g.name}</td>
                      <td className="px-4 py-3 text-right">{g.memberCount ?? '-'}</td>
                      <td className="px-4 py-3 text-right">{g.postCount ?? '-'}</td>
                      <td className="px-4 py-3 text-xs text-gray-500">{g.visibility}</td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {g.createdAt ? new Date(g.createdAt).toLocaleDateString() : '-'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <Link
                            to={`/group/${String(g.id)}`}
                            className="text-xs text-primary-600 hover:underline"
                          >
                            View
                          </Link>
                          <button
                            onClick={() => handleDeleteGroup(String(g.id))}
                            className="text-xs text-red-600 hover:underline"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {groups.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                        No groups found
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between mt-3">
              <button
                onClick={() => setGroupPage((p) => Math.max(0, p - 1))}
                disabled={groupPage === 0}
                className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-gray-500">
                Page {groupPage + 1} of {groupsTotalPages}
              </span>
              <button
                onClick={() => setGroupPage((p) => p + 1)}
                disabled={groupPage + 1 >= groupsTotalPages}
                className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>

      {/* Pages */}
      <div>
        <h3 className="text-lg font-semibold text-gray-900 mb-3">Pages</h3>
        {loadingPages ? (
          <div className="text-gray-400 py-6 text-center">Loading pages...</div>
        ) : (
          <>
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-gray-500 border-b bg-gray-50">
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3 text-right">Followers</th>
                    <th className="px-4 py-3 text-right">Posts</th>
                    <th className="px-4 py-3">Owner</th>
                    <th className="px-4 py-3">Created</th>
                    <th className="px-4 py-3">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pages.map((p: any) => (
                    <tr key={String(p.id)} className="even:bg-gray-50 border-b border-gray-100">
                      <td className="px-4 py-3 font-medium text-gray-900">{p.name}</td>
                      <td className="px-4 py-3 text-right">{p.followerCount ?? '-'}</td>
                      <td className="px-4 py-3 text-right">{p.postCount ?? '-'}</td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {p.ownerName ?? (p.ownerId ? String(p.ownerId) : '-')}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '-'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <Link
                            to={`/page/${String(p.id)}`}
                            className="text-xs text-primary-600 hover:underline"
                          >
                            View
                          </Link>
                          <button
                            onClick={() => handleDeletePage(String(p.id))}
                            className="text-xs text-red-600 hover:underline"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {pages.length === 0 && (
                    <tr>
                      <td colSpan={6} className="px-4 py-8 text-center text-gray-400">
                        No pages found
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between mt-3">
              <button
                onClick={() => setPagePage((p) => Math.max(0, p - 1))}
                disabled={pagePage === 0}
                className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-gray-500">
                Page {pagePage + 1} of {pagesTotalPages}
              </span>
              <button
                onClick={() => setPagePage((p) => p + 1)}
                disabled={pagePage + 1 >= pagesTotalPages}
                className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50"
              >
                Next
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/* ────────────────────────── Shared Components ────────────────────────── */

function StatCard({
  label,
  value,
  badge,
}: {
  label: string;
  value: number | string;
  badge?: string;
}) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <div className="text-xs font-medium text-gray-500 uppercase tracking-wider">{label}</div>
      <div className="mt-1 flex items-baseline gap-2">
        <span className="text-2xl font-bold text-gray-900">
          {typeof value === 'number' ? value.toLocaleString() : value}
        </span>
        {badge && (
          <span className="text-xs bg-green-100 text-green-700 px-1.5 py-0.5 rounded font-medium">
            {badge}
          </span>
        )}
      </div>
    </div>
  );
}

function MiniCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-gray-50 rounded-lg p-3 text-center">
      <div className="text-xs text-gray-500 mb-1">{label}</div>
      <div className="text-sm font-semibold text-gray-900">{String(value)}</div>
    </div>
  );
}

function BarChart({
  data,
  valueKey,
  labelKey,
  color = 'bg-primary-500',
}: {
  data: any[];
  valueKey: string;
  labelKey: string;
  color?: string;
}) {
  const maxVal = Math.max(...data.map((d) => Number(d[valueKey]) || 0), 1);

  return (
    <div className="flex items-end gap-[2px] h-40 overflow-x-auto">
      {data.map((d, i) => {
        const val = Number(d[valueKey]) || 0;
        const heightPct = (val / maxVal) * 100;
        return (
          <div
            key={i}
            className="group relative flex-1 min-w-[8px] flex flex-col justify-end h-full"
          >
            <div
              className={`${color} rounded-t transition-all opacity-80 hover:opacity-100 cursor-pointer`}
              style={{ height: `${Math.max(heightPct, 1)}%` }}
            />
            <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 hidden group-hover:block bg-gray-800 text-white text-[10px] px-2 py-1 rounded whitespace-nowrap z-10 pointer-events-none">
              {d[labelKey]}: {val}
            </div>
          </div>
        );
      })}
    </div>
  );
}
