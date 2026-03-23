import { useState, useRef, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ForceGraph2D from 'react-force-graph-2d';
import { useAuth } from '../hooks/useAuth';
import api from '../api/client';

type Tab = 'dashboard' | 'engagement' | 'users' | 'content' | 'groups-pages' | 'graph';

const tabs: { key: Tab; label: string }[] = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'engagement', label: 'Engagement' },
  { key: 'users', label: 'Users' },
  { key: 'content', label: 'Content' },
  { key: 'groups-pages', label: 'Groups & Pages' },
  { key: 'graph', label: 'Graph Explorer' },
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
      {activeTab === 'graph' && <GraphExplorerTab />}
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

/* ────────────────────────── Graph Explorer Tab ────────────────────────── */

function UserPicker({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const { data: results } = useQuery<{ id: number; username: string; displayName: string; avatarUrl: string | null }[]>({
    queryKey: ['user-picker-search', search],
    queryFn: () => api.get('/users/search', { params: { q: search } }).then(r => r.data),
    enabled: search.length >= 1 && open,
    staleTime: 10000,
  });

  return (
    <div className="relative flex-1 min-w-[200px]">
      <input
        value={open ? search : (value ? `${value}` : '')}
        onChange={e => { setSearch(e.target.value); setOpen(true); onChange(e.target.value); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 200)}
        placeholder={placeholder}
        className="w-full px-4 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent"
      />
      {open && results && results.length > 0 && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 max-h-60 overflow-y-auto">
          {results.slice(0, 10).map(user => (
            <button key={user.id} type="button"
              onMouseDown={e => { e.preventDefault(); onChange(String(user.id)); setSearch(user.displayName + ' (' + user.username + ')'); setOpen(false); }}
              className="w-full flex items-center gap-2.5 px-3 py-2 hover:bg-gray-50 text-left">
              {user.avatarUrl ? (
                <img src={user.avatarUrl} className="w-7 h-7 rounded-full" alt="" />
              ) : (
                <div className="w-7 h-7 bg-primary-400 text-white rounded-full flex items-center justify-center text-xs font-bold">
                  {user.displayName?.[0]?.toUpperCase() ?? '?'}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium text-gray-900 truncate">{user.displayName}</div>
                <div className="text-xs text-gray-400">@{user.username}</div>
              </div>
              <span className="text-[10px] text-gray-300 font-mono">{user.id}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function GraphExplorerTab() {
  const [nodeId, setNodeId] = useState('');
  const [edgeType, setEdgeType] = useState('FOLLOWS');
  const [activeSection, setActiveSection] = useState<'profile' | 'neighbors' | 'fof' | 'mutual' | 'traverse'>('profile');

  // User search for picking IDs
  const [userSearch, setUserSearch] = useState('');
  const { data: searchResults } = useQuery<{ id: number; username: string; displayName: string; avatarUrl: string | null }[]>({
    queryKey: ['admin-user-search', userSearch],
    queryFn: () => api.get('/users/search', { params: { q: userSearch } }).then(r => r.data),
    enabled: userSearch.length >= 2,
    staleTime: 10000,
  });

  // FOF state
  const [fofSource, setFofSource] = useState('');
  const [fofEdgeType, setFofEdgeType] = useState('FOLLOWS');
  const [fofMaxResults, setFofMaxResults] = useState(20);

  // Mutual friends state
  const [mutualUser1, setMutualUser1] = useState('');
  const [mutualUser2, setMutualUser2] = useState('');
  const [mutualEdgeType, setMutualEdgeType] = useState('FOLLOWS');

  // Traverse state
  const [traverseDepth, setTraverseDepth] = useState(2);

  // Backfill mutation
  const backfill = useMutation({
    mutationFn: () => api.post('/admin/graph/backfill').then(r => r.data),
  });

  const EDGE_TYPES = ['FOLLOWS', 'LIKES', 'AUTHORED', 'MEMBER_OF', 'CONTAINS'];

  // Node profile query
  const { data: profile, isFetching: profileLoading } = useQuery<{
    nodeId: string; edgeCounts: Record<string, number>;
    username?: string; displayName?: string; avatarUrl?: string;
  }>({
    queryKey: ['graph-profile', nodeId],
    queryFn: () => api.get(`/admin/graph/profile/${nodeId}`).then(r => r.data),
    enabled: !!nodeId && activeSection === 'profile',
  });

  // Neighbors query
  const { data: neighbors, isFetching: neighborsLoading } = useQuery<{
    neighbors: { id: string; username?: string; displayName?: string; avatarUrl?: string }[];
    count: number;
  }>({
    queryKey: ['graph-neighbors', nodeId, edgeType],
    queryFn: () => api.get(`/admin/graph/neighbors/${nodeId}/${edgeType}`).then(r => r.data),
    enabled: !!nodeId && activeSection === 'neighbors',
  });

  // FOF query
  const fofQuery = useMutation({
    mutationFn: (body: { sourceId: string; edgeType: string; maxResults: number }) =>
      api.post('/admin/graph/fof', body).then(r => r.data),
  });

  // Mutual friends query
  const mutualQuery = useMutation({
    mutationFn: (body: { userId1: string; userId2: string; edgeType: string }) =>
      api.post('/admin/graph/mutual-friends', body).then(r => r.data),
  });

  // Traverse query
  const { data: traverseData, isFetching: traverseLoading } = useQuery<{
    nodes: { id: string; username?: string; displayName?: string; avatarUrl?: string }[];
    edges: { src: string; dst: string; edgeType: string }[];
    root: string;
  }>({
    queryKey: ['graph-traverse', nodeId, edgeType, traverseDepth],
    queryFn: () => api.get(`/admin/graph/traverse/${nodeId}/${edgeType}`, {
      params: { depth: traverseDepth, maxPerHop: 20 }
    }).then(r => r.data),
    enabled: !!nodeId && activeSection === 'traverse',
  });

  // AOEE stats
  const { data: aoeeStats } = useQuery<{ available: boolean; [key: string]: unknown }>({
    queryKey: ['aoee-stats'],
    queryFn: () => api.get('/admin/graph/stats').then(r => r.data),
    staleTime: 10000,
  });

  const sectionBtns: { key: typeof activeSection; label: string }[] = [
    { key: 'profile', label: 'Node Profile' },
    { key: 'neighbors', label: 'Neighbors' },
    { key: 'fof', label: 'Friend of Friend' },
    { key: 'mutual', label: 'Mutual Friends' },
    { key: 'traverse', label: 'Graph Traverse' },
  ];

  return (
    <div className="space-y-6">
      {/* AOEE Status + Backfill */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className={`px-4 py-2 rounded-lg text-sm font-medium flex-1 ${aoeeStats?.available ? 'bg-green-50 text-green-700' : 'bg-yellow-50 text-yellow-700'}`}>
          AOEE Graph Cache: {aoeeStats?.available ? 'Connected' : 'Unavailable (queries will use DB fallback)'}
        </div>
        <button
          onClick={() => backfill.mutate()}
          disabled={backfill.isPending}
          className="px-4 py-2 bg-indigo-500 text-white rounded-lg text-sm font-medium hover:bg-indigo-600 disabled:opacity-50 shrink-0"
        >
          {backfill.isPending ? 'Loading data...' : 'Load DB into AOEE'}
        </button>
      </div>
      {backfill.data && (
        <div className="bg-indigo-50 border border-indigo-200 rounded-lg p-4 text-sm">
          <div className="font-medium text-indigo-900 mb-2">Backfill complete: {backfill.data.totalEdges} edges loaded</div>
          <div className="flex gap-4 flex-wrap text-indigo-700">
            {Object.entries(backfill.data.edgesByType as Record<string, number>).map(([type, count]) => (
              <span key={type}>{type}: <strong>{count}</strong></span>
            ))}
          </div>
        </div>
      )}

      {/* Section selector */}
      <div className="flex gap-2 flex-wrap">
        {sectionBtns.map(s => (
          <button key={s.key} onClick={() => setActiveSection(s.key)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              activeSection === s.key ? 'bg-primary-500 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}>
            {s.label}
          </button>
        ))}
      </div>

      {/* ── Node Profile ── */}
      {activeSection === 'profile' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Node Profile</h3>
          <p className="text-sm text-gray-500">Look up a user or entity by ID to see their graph connections.</p>
          <div className="flex gap-3">
            <UserPicker value={nodeId} onChange={setNodeId} placeholder="Search user or enter ID..." />
          </div>
          {profileLoading && <div className="text-sm text-gray-400">Loading...</div>}
          {profile && (
            <div className="space-y-4">
              <div className="flex items-center gap-3 p-4 bg-gray-50 rounded-lg">
                {profile.avatarUrl ? (
                  <img src={profile.avatarUrl} className="w-12 h-12 rounded-full" alt="" />
                ) : (
                  <div className="w-12 h-12 bg-primary-500 text-white rounded-full flex items-center justify-center font-bold">
                    {profile.displayName?.[0]?.toUpperCase() ?? '?'}
                  </div>
                )}
                <div>
                  <div className="font-semibold text-gray-900">{profile.displayName ?? 'Unknown Entity'}</div>
                  {profile.username && <div className="text-sm text-gray-500">@{profile.username}</div>}
                  <div className="text-xs text-gray-400 font-mono">ID: {profile.nodeId}</div>
                </div>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
                {Object.entries(profile.edgeCounts).map(([type, count]) => (
                  <button key={type} onClick={() => { setEdgeType(type); setActiveSection('neighbors'); }}
                    className="p-3 bg-white border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all text-center cursor-pointer">
                    <div className="text-2xl font-bold text-primary-600">{count}</div>
                    <div className="text-xs text-gray-500 font-medium">{type}</div>
                  </button>
                ))}
                {Object.keys(profile.edgeCounts).length === 0 && (
                  <div className="col-span-full text-sm text-gray-400 italic">No edges found in AOEE for this node</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Neighbors ── */}
      {activeSection === 'neighbors' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Neighbor Explorer</h3>
          <div className="flex gap-3 flex-wrap">
            <UserPicker value={nodeId} onChange={setNodeId} placeholder="Search user or enter ID..." />
            <select value={edgeType} onChange={e => setEdgeType(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg text-sm bg-white">
              {EDGE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          {neighborsLoading && <div className="text-sm text-gray-400">Loading...</div>}
          {neighbors && (
            <>
              <div className="text-sm text-gray-500">{neighbors.count} neighbor{neighbors.count !== 1 ? 's' : ''} via <span className="font-mono text-primary-600">{edgeType}</span></div>
              <div className="grid gap-2 max-h-96 overflow-y-auto">
                {neighbors.neighbors.map(n => (
                  <div key={n.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                    {n.avatarUrl ? (
                      <img src={n.avatarUrl} className="w-8 h-8 rounded-full" alt="" />
                    ) : (
                      <div className="w-8 h-8 bg-gray-400 text-white rounded-full flex items-center justify-center text-xs font-bold">
                        {n.displayName?.[0]?.toUpperCase() ?? '?'}
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">{n.displayName ?? 'Unknown'}</div>
                      {n.username && <div className="text-xs text-gray-500">@{n.username}</div>}
                    </div>
                    <button onClick={() => { setNodeId(String(n.id)); setActiveSection('profile'); }}
                      className="text-xs text-primary-500 hover:underline shrink-0">Explore</button>
                    <span className="text-xs text-gray-400 font-mono shrink-0">{n.id}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Friend of Friend ── */}
      {activeSection === 'fof' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Friend of Friend</h3>
          <p className="text-sm text-gray-500">Find users connected through mutual connections. Higher scores mean more mutual connections.</p>
          <div className="flex gap-3 flex-wrap">
            <UserPicker value={fofSource} onChange={setFofSource} placeholder="Search source user..." />
            <select value={fofEdgeType} onChange={e => setFofEdgeType(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg text-sm bg-white">
              {EDGE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
            <input type="number" value={fofMaxResults} onChange={e => setFofMaxResults(Number(e.target.value))}
              className="w-24 px-4 py-2 border border-gray-300 rounded-lg text-sm" placeholder="Max" />
            <button onClick={() => fofQuery.mutate({ sourceId: fofSource, edgeType: fofEdgeType, maxResults: fofMaxResults })}
              disabled={!fofSource || fofQuery.isPending}
              className="px-6 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50">
              {fofQuery.isPending ? 'Querying...' : 'Find FOF'}
            </button>
          </div>
          {fofQuery.data && (
            <div className="space-y-3">
              {fofQuery.data.elapsedMs != null && (
                <div className="text-xs text-gray-400">Query completed in {fofQuery.data.elapsedMs}ms</div>
              )}
              {fofQuery.data.error && (
                <div className="text-sm text-red-600 bg-red-50 px-4 py-2 rounded-lg">AOEE Error: {fofQuery.data.error}</div>
              )}
              <div className="grid gap-2 max-h-96 overflow-y-auto">
                {(fofQuery.data.candidates ?? []).map((c: { id: string; score?: number; displayName?: string; username?: string; avatarUrl?: string }) => (
                  <div key={c.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                    {c.avatarUrl ? (
                      <img src={c.avatarUrl} className="w-8 h-8 rounded-full" alt="" />
                    ) : (
                      <div className="w-8 h-8 bg-indigo-400 text-white rounded-full flex items-center justify-center text-xs font-bold">
                        {c.displayName?.[0]?.toUpperCase() ?? '?'}
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">{c.displayName ?? 'Unknown'}</div>
                      {c.username && <div className="text-xs text-gray-500">@{c.username}</div>}
                    </div>
                    {c.score != null && (
                      <div className="px-2 py-1 bg-primary-100 text-primary-700 rounded text-xs font-mono">
                        score: {Number(c.score).toFixed(2)}
                      </div>
                    )}
                    <button onClick={() => { setNodeId(String(c.id)); setActiveSection('profile'); }}
                      className="text-xs text-primary-500 hover:underline">Explore</button>
                  </div>
                ))}
                {(fofQuery.data.candidates ?? []).length === 0 && !fofQuery.data.error && (
                  <div className="text-sm text-gray-400 italic">No friend-of-friend candidates found</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Mutual Friends ── */}
      {activeSection === 'mutual' && (
        <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Mutual Friends</h3>
          <p className="text-sm text-gray-500">Find shared connections between two users.</p>
          <div className="flex gap-3 flex-wrap">
            <UserPicker value={mutualUser1} onChange={setMutualUser1} placeholder="Search user 1..." />
            <UserPicker value={mutualUser2} onChange={setMutualUser2} placeholder="Search user 2..." />
            <select value={mutualEdgeType} onChange={e => setMutualEdgeType(e.target.value)}
              className="px-4 py-2 border border-gray-300 rounded-lg text-sm bg-white">
              {EDGE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
            <button onClick={() => mutualQuery.mutate({ userId1: mutualUser1, userId2: mutualUser2, edgeType: mutualEdgeType })}
              disabled={!mutualUser1 || !mutualUser2 || mutualQuery.isPending}
              className="px-6 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50">
              {mutualQuery.isPending ? 'Querying...' : 'Find Mutual'}
            </button>
          </div>
          {mutualQuery.data && (
            <div className="space-y-3">
              {mutualQuery.data.user1 && mutualQuery.data.user2 && (
                <div className="flex items-center gap-2 text-sm text-gray-600">
                  <span className="font-medium">{mutualQuery.data.user1.displayName}</span>
                  <span className="text-gray-400">&amp;</span>
                  <span className="font-medium">{mutualQuery.data.user2.displayName}</span>
                </div>
              )}
              {mutualQuery.data.error && (
                <div className="text-sm text-red-600 bg-red-50 px-4 py-2 rounded-lg">AOEE Error: {mutualQuery.data.error}</div>
              )}
              <div className="grid gap-2 max-h-96 overflow-y-auto">
                {(mutualQuery.data.mutualFriends ?? []).map((f: { id: string; displayName?: string; username?: string; avatarUrl?: string }) => (
                  <div key={f.id} className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                    {f.avatarUrl ? (
                      <img src={f.avatarUrl} className="w-8 h-8 rounded-full" alt="" />
                    ) : (
                      <div className="w-8 h-8 bg-emerald-400 text-white rounded-full flex items-center justify-center text-xs font-bold">
                        {f.displayName?.[0]?.toUpperCase() ?? '?'}
                      </div>
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">{f.displayName ?? 'Unknown'}</div>
                      {f.username && <div className="text-xs text-gray-500">@{f.username}</div>}
                    </div>
                    <button onClick={() => { setNodeId(String(f.id)); setActiveSection('profile'); }}
                      className="text-xs text-primary-500 hover:underline">Explore</button>
                  </div>
                ))}
                {(mutualQuery.data.mutualFriends ?? []).length === 0 && !mutualQuery.data.error && (
                  <div className="text-sm text-gray-400 italic">No mutual friends found</div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Graph Traverse + Visualization ── */}
      {activeSection === 'traverse' && (
        <GraphVisualizer
          nodeId={nodeId}
          setNodeId={setNodeId}
          edgeType={edgeType}
          setEdgeType={setEdgeType}
          traverseDepth={traverseDepth}
          setTraverseDepth={setTraverseDepth}
          setActiveSection={setActiveSection}
          EDGE_TYPES={EDGE_TYPES}
        />
      )}
    </div>
  );
}

/* ────────────────────────── Graph Visualizer ────────────────────────── */

const EDGE_COLORS: Record<string, string> = {
  FOLLOWS: '#4fc3f7',
  LIKES: '#ff7043',
  AUTHORED: '#ffa726',
  MEMBER_OF: '#ab47bc',
  CONTAINS: '#26a69a',
};

function GraphVisualizer({
  nodeId, setNodeId, edgeType, setEdgeType,
  traverseDepth, setTraverseDepth, setActiveSection, EDGE_TYPES,
}: {
  nodeId: string; setNodeId: (v: string) => void;
  edgeType: string; setEdgeType: (v: string) => void;
  traverseDepth: number; setTraverseDepth: (v: number) => void;
  setActiveSection: (v: 'profile' | 'neighbors' | 'fof' | 'mutual' | 'traverse') => void;
  EDGE_TYPES: string[];
}) {
  const graphRef = useRef<any>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedEdgeTypes, setSelectedEdgeTypes] = useState<string[]>([edgeType]);
  const [graphHeight, setGraphHeight] = useState(700);
  const [graphWidth, setGraphWidth] = useState(800);
  const [graphData, setGraphData] = useState<{ nodes: any[]; links: any[] }>({ nodes: [], links: [] });
  const [selectedNode, setSelectedNode] = useState<any>(null);
  const [fullscreen, setFullscreen] = useState(false);

  // Measure container width
  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver(entries => {
      for (const entry of entries) {
        setGraphWidth(entry.contentRect.width);
      }
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  // Escape key exits fullscreen
  useEffect(() => {
    if (!fullscreen) return;
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') setFullscreen(false); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [fullscreen]);

  // Traverse query
  const { data: traverseData, isFetching: traverseLoading } = useQuery<{
    nodes: { id: string; username?: string; displayName?: string; avatarUrl?: string }[];
    edges: { src: string; dst: string; edgeType: string }[];
    root: string;
  }>({
    queryKey: ['graph-traverse', nodeId, selectedEdgeTypes.join(','), traverseDepth],
    queryFn: async () => {
      // Fetch for each selected edge type and merge
      const allNodes = new Map<string, any>();
      const allEdges: any[] = [];
      const edgeSet = new Set<string>();
      for (const et of selectedEdgeTypes) {
        const { data } = await api.get(`/admin/graph/traverse/${nodeId}/${et}`, {
          params: { depth: traverseDepth, maxPerHop: 30 },
        });
        for (const n of data.nodes) allNodes.set(String(n.id), n);
        for (const e of data.edges) {
          const key = `${e.src}-${e.dst}-${e.edgeType}`;
          if (!edgeSet.has(key)) { edgeSet.add(key); allEdges.push(e); }
        }
      }
      return { nodes: Array.from(allNodes.values()), edges: allEdges, root: nodeId };
    },
    enabled: !!nodeId && selectedEdgeTypes.length > 0,
  });

  // Convert traverse data to force graph format
  useEffect(() => {
    if (!traverseData) return;
    const nodes = traverseData.nodes.map(n => ({
      id: String(n.id),
      label: n.displayName || n.username || String(n.id),
      username: n.username,
      displayName: n.displayName,
      avatarUrl: n.avatarUrl,
      isRoot: String(n.id) === String(traverseData.root),
    }));
    const links = traverseData.edges.map(e => ({
      source: String(e.src),
      target: String(e.dst),
      type: e.edgeType,
    }));
    setGraphData({ nodes, links });
  }, [traverseData]);

  // Zoom to fit when graph data changes
  useEffect(() => {
    if (graphData.nodes.length > 0 && graphRef.current) {
      const timer = setTimeout(() => graphRef.current?.zoomToFit(400, 60), 600);
      return () => clearTimeout(timer);
    }
  }, [graphData]);

  const handleNodeClick = useCallback((node: any) => {
    setSelectedNode(node);
  }, []);

  const toggleEdgeType = (type: string) => {
    setSelectedEdgeTypes(prev =>
      prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type]
    );
  };

  const edgeCounts = graphData.links.reduce((acc: Record<string, number>, link: any) => {
    acc[link.type] = (acc[link.type] || 0) + 1;
    return acc;
  }, {});

  return (
    <div className="space-y-4">
      <div className="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">Network Graph</h3>
        <p className="text-sm text-gray-500">Interactive force-directed visualization. Click nodes to inspect, double-click to re-center.</p>
        <div className="flex gap-3 flex-wrap items-end">
          <div className="flex-1 min-w-[200px]">
            <label className="text-xs text-gray-500 mb-1 block">Starting User</label>
            <UserPicker value={nodeId} onChange={setNodeId} placeholder="Search user..." />
          </div>
          <div>
            <label className="text-xs text-gray-500 mb-1 block">Depth</label>
            <select value={traverseDepth} onChange={e => setTraverseDepth(Number(e.target.value))}
              className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white">
              {[1, 2, 3].map(d => <option key={d} value={d}>{d} hop{d > 1 ? 's' : ''}</option>)}
            </select>
          </div>
        </div>

        {/* Edge type toggles */}
        <div>
          <label className="text-xs text-gray-500 mb-2 block">Edge Types</label>
          <div className="flex flex-wrap gap-2">
            {EDGE_TYPES.map(type => (
              <button key={type} onClick={() => toggleEdgeType(type)}
                className="px-3 py-1.5 rounded-full text-xs font-medium transition-all border"
                style={{
                  backgroundColor: selectedEdgeTypes.includes(type) ? EDGE_COLORS[type] ?? '#999' : 'white',
                  color: selectedEdgeTypes.includes(type) ? 'white' : '#666',
                  borderColor: selectedEdgeTypes.includes(type) ? 'transparent' : '#ddd',
                }}>
                {type}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Force graph — inline or fullscreen */}
      <GraphCanvas
        fullscreen={fullscreen}
        setFullscreen={setFullscreen}
        graphRef={graphRef}
        containerRef={containerRef}
        graphData={graphData}
        graphWidth={graphWidth}
        graphHeight={graphHeight}
        setGraphHeight={setGraphHeight}
        traverseLoading={traverseLoading}
        nodeId={nodeId}
        selectedNode={selectedNode}
        setSelectedNode={handleNodeClick}
        setNodeId={setNodeId}
        edgeCounts={edgeCounts}
      />

      {/* Selected node detail */}
      {selectedNode && (
        <div className="bg-white border border-gray-200 rounded-xl p-4">
          <div className="flex items-center gap-3">
            {selectedNode.avatarUrl ? (
              <img src={selectedNode.avatarUrl} className="w-10 h-10 rounded-full" alt="" />
            ) : (
              <div className="w-10 h-10 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-bold">
                {selectedNode.displayName?.[0]?.toUpperCase() ?? '?'}
              </div>
            )}
            <div className="flex-1">
              <div className="font-medium text-gray-900">{selectedNode.displayName ?? selectedNode.id}</div>
              {selectedNode.username && <div className="text-xs text-gray-500">@{selectedNode.username}</div>}
              <div className="text-xs text-gray-400 font-mono">{selectedNode.id}</div>
            </div>
            <button onClick={() => { setNodeId(String(selectedNode.id)); setSelectedNode(null); }}
              className="px-3 py-1.5 bg-primary-50 text-primary-600 rounded-lg text-xs font-medium hover:bg-primary-100">
              Re-center on this node
            </button>
            <button onClick={() => { setNodeId(String(selectedNode.id)); setActiveSection('profile'); }}
              className="px-3 py-1.5 bg-gray-100 text-gray-700 rounded-lg text-xs font-medium hover:bg-gray-200">
              View Profile
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ────────────────────────── Graph Canvas (inline + fullscreen) ────────────────────────── */

function GraphCanvas({
  fullscreen, setFullscreen, graphRef, containerRef,
  graphData, graphWidth, graphHeight, setGraphHeight,
  traverseLoading, nodeId, selectedNode, setSelectedNode, setNodeId, edgeCounts,
}: {
  fullscreen: boolean; setFullscreen: (v: boolean) => void;
  graphRef: React.MutableRefObject<any>; containerRef: React.RefObject<any>;
  graphData: { nodes: any[]; links: any[] };
  graphWidth: number; graphHeight: number; setGraphHeight: (v: number) => void;
  traverseLoading: boolean; nodeId: string;
  selectedNode: any; setSelectedNode: (n: any) => void;
  setNodeId: (v: string) => void; edgeCounts: Record<string, number>;
}) {
  const w = fullscreen ? window.innerWidth : graphWidth;
  const h = fullscreen ? window.innerHeight : graphHeight;

  // Re-fit when toggling fullscreen
  useEffect(() => {
    if (graphData.nodes.length > 0 && graphRef.current) {
      setTimeout(() => graphRef.current?.zoomToFit(300, 60), 200);
    }
  }, [fullscreen, graphData.nodes.length, graphRef]);

  const graphContent = (
    <>
      {traverseLoading && (
        <div className="absolute inset-0 bg-white/70 flex items-center justify-center z-10">
          <div className="text-sm text-gray-500">Loading graph...</div>
        </div>
      )}
      {graphData.nodes.length > 0 ? (
        <>
          <ForceGraph2D
            ref={graphRef}
            graphData={graphData}
            width={w}
            height={h}
            nodeLabel={(node: any) => `${node.displayName || node.id}\n@${node.username || '?'}`}
            nodeColor={(node: any) => node.isRoot ? '#ef4444' : '#60a5fa'}
            linkColor={(link: any) => EDGE_COLORS[link.type] ?? '#999'}
            linkWidth={2}
            linkDirectionalArrowLength={6}
            linkDirectionalArrowRelPos={1}
            linkDirectionalArrowColor={(link: any) => EDGE_COLORS[link.type] ?? '#999'}
            onNodeClick={setSelectedNode}
            onNodeRightClick={(node: any) => { setNodeId(String(node.id)); }}
            nodeCanvasObject={(node: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
              const size = node.isRoot ? 8 : 5;
              const x = node.x ?? 0;
              const y = node.y ?? 0;
              ctx.fillStyle = node.isRoot ? '#ef4444' : (node.id === selectedNode?.id ? '#2563eb' : '#60a5fa');
              ctx.beginPath();
              ctx.arc(x, y, size, 0, 2 * Math.PI);
              ctx.fill();
              if (node.isRoot) {
                ctx.strokeStyle = '#b91c1c';
                ctx.lineWidth = 2 / globalScale;
                ctx.stroke();
              }
              const label = node.displayName || node.username || node.id;
              const fontSize = Math.max(11 / globalScale, 3);
              ctx.font = `${fontSize}px -apple-system, BlinkMacSystemFont, sans-serif`;
              ctx.fillStyle = '#1f2937';
              ctx.textAlign = 'left';
              ctx.textBaseline = 'middle';
              ctx.fillText(label, x + size + 3, y);
            }}
            linkCanvasObjectMode={() => 'after'}
            linkCanvasObject={(link: any, ctx: CanvasRenderingContext2D, globalScale: number) => {
              const start = link.source;
              const end = link.target;
              if (!start.x || !end.x) return;
              const fontSize = Math.max(8 / globalScale, 2);
              ctx.font = `${fontSize}px sans-serif`;
              ctx.fillStyle = EDGE_COLORS[link.type] ?? '#999';
              ctx.textAlign = 'center';
              ctx.textBaseline = 'middle';
              ctx.fillText(link.type, (start.x + end.x) / 2, (start.y + end.y) / 2);
            }}
          />

          {/* Legend */}
          <div className="absolute bottom-4 right-4 bg-white/95 rounded-lg shadow-md px-3 py-2 text-xs">
            <div className="font-semibold text-gray-700 mb-1.5">Legend</div>
            {Object.entries(edgeCounts).map(([type, count]) => (
              <div key={type} className="flex items-center gap-2 mb-1">
                <div className="w-3 h-0.5 rounded" style={{ backgroundColor: EDGE_COLORS[type] ?? '#999' }} />
                <span className="text-gray-600">{type}</span>
                <span className="text-gray-400">({count})</span>
              </div>
            ))}
            <div className="border-t border-gray-200 mt-1.5 pt-1.5 space-y-1">
              <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-red-500" /><span className="text-gray-600">Root</span></div>
              <div className="flex items-center gap-2"><div className="w-2.5 h-2.5 rounded-full bg-blue-400" /><span className="text-gray-600">Other</span></div>
            </div>
          </div>

          {/* Top bar: stats + controls */}
          <div className="absolute top-3 left-3 bg-white/95 rounded-lg shadow-md px-3 py-2 text-xs flex gap-3 items-center">
            <span className="text-gray-600"><strong>{graphData.nodes.length}</strong> nodes</span>
            <span className="text-gray-600"><strong>{graphData.links.length}</strong> edges</span>
            <span className="text-gray-300">|</span>
            <button onClick={() => graphRef.current?.zoomToFit(300, 50)}
              className="px-2 py-0.5 rounded bg-gray-100 text-gray-600 hover:bg-gray-200 font-medium">
              Fit
            </button>
            <button onClick={() => { setFullscreen(!fullscreen); }}
              className="px-2 py-0.5 rounded bg-gray-100 text-gray-600 hover:bg-gray-200 font-medium">
              {fullscreen ? 'Exit Fullscreen' : 'Fullscreen'}
            </button>
          </div>

          {/* Selected node popover in fullscreen */}
          {fullscreen && selectedNode && (
            <div className="absolute bottom-4 left-4 bg-white/95 rounded-lg shadow-lg px-4 py-3 max-w-xs">
              <div className="flex items-center gap-3">
                {selectedNode.avatarUrl ? (
                  <img src={selectedNode.avatarUrl} className="w-9 h-9 rounded-full" alt="" />
                ) : (
                  <div className="w-9 h-9 bg-primary-500 text-white rounded-full flex items-center justify-center text-xs font-bold">
                    {selectedNode.displayName?.[0]?.toUpperCase() ?? '?'}
                  </div>
                )}
                <div>
                  <div className="text-sm font-medium text-gray-900">{selectedNode.displayName ?? selectedNode.id}</div>
                  {selectedNode.username && <div className="text-xs text-gray-500">@{selectedNode.username}</div>}
                  <div className="text-[10px] text-gray-400 font-mono">{selectedNode.id}</div>
                </div>
              </div>
              <div className="flex gap-2 mt-2">
                <button onClick={() => setNodeId(String(selectedNode.id))}
                  className="px-2 py-1 bg-primary-50 text-primary-600 rounded text-xs font-medium hover:bg-primary-100">
                  Re-center
                </button>
                <button onClick={() => setSelectedNode(null)}
                  className="px-2 py-1 bg-gray-100 text-gray-600 rounded text-xs font-medium hover:bg-gray-200">
                  Dismiss
                </button>
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="flex items-center justify-center h-full text-gray-400 text-sm">
          {nodeId ? 'Select edge types to visualize' : 'Search for a user and select edge types to explore the network'}
        </div>
      )}
    </>
  );

  if (fullscreen) {
    return (
      <div className="fixed inset-0 z-[9999] bg-white" ref={containerRef}>
        {graphContent}
      </div>
    );
  }

  return (
    <div ref={containerRef} className="bg-white border border-gray-200 rounded-xl overflow-hidden relative" style={{ height: graphHeight }}>
      {graphContent}
      {/* Resize handle (inline only) */}
      <div className="absolute bottom-0 left-0 right-0 h-4 cursor-ns-resize flex justify-center items-center bg-gradient-to-b from-transparent to-gray-200/60 hover:to-gray-300/80 transition-colors"
        onMouseDown={e => {
          e.preventDefault();
          const startY = e.clientY;
          const startH = graphHeight;
          const onMove = (ev: MouseEvent) => setGraphHeight(Math.max(300, Math.min(window.innerHeight - 100, startH + ev.clientY - startY)));
          const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp); };
          document.addEventListener('mousemove', onMove);
          document.addEventListener('mouseup', onUp);
        }}>
        <div className="w-16 h-1.5 bg-gray-400 rounded-full" />
      </div>
    </div>
  );
}
