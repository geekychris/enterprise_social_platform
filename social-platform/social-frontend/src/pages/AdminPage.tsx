import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ForceGraph2D from 'react-force-graph-2d';
import { useAuth } from '../hooks/useAuth';
import api from '../api/client';

type Tab = 'dashboard' | 'engagement' | 'users' | 'content' | 'groups-pages' | 'graph';

const tabs: { key: Tab; label: string }[] = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'engagement', label: 'Engagement' },
  { key: 'users', label: 'User Management' },
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
      <h1 className="text-2xl font-bold text-gray-900 mb-4">WorkSphere Admin</h1>

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

  const { data: contentBreakdown } = useQuery({
    queryKey: ['admin-content-breakdown'],
    queryFn: () => api.get('/admin/analytics/content-breakdown').then((r) => r.data),
  });

  const { data: hourlyActivity } = useQuery({
    queryKey: ['admin-hourly-activity'],
    queryFn: () => api.get('/admin/analytics/hourly-activity').then((r) => r.data),
  });

  const { data: messaging } = useQuery({
    queryKey: ['admin-messaging'],
    queryFn: () => api.get('/admin/analytics/messaging').then((r) => r.data),
  });

  const { data: socialGraph } = useQuery({
    queryKey: ['admin-social-graph'],
    queryFn: () => api.get('/admin/analytics/social-graph').then((r) => r.data),
  });

  if (loadingStats) {
    return <div className="text-gray-400 py-10 text-center">Loading dashboard...</div>;
  }

  const REACTION_EMOJI: Record<string, string> = {
    LIKE: '\uD83D\uDC4D', LOVE: '\u2764\uFE0F', HAHA: '\uD83D\uDE02',
    WOW: '\uD83D\uDE2E', SAD: '\uD83D\uDE22', ANGRY: '\uD83D\uDE20',
  };

  const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

  return (
    <div className="space-y-6">
      {/* Theme Selector */}
      <ThemeSelector />

      {/* ── Key Metrics ── */}
      {stats && (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <StatCard label="Users" value={stats.totalUsers} badge={stats.newUsersLast7d > 0 ? `+${stats.newUsersLast7d} 7d` : undefined} />
          <StatCard label="Posts" value={stats.totalPosts} badge={stats.postsLast24h > 0 ? `${stats.postsLast24h} today` : undefined} />
          <StatCard label="Comments" value={stats.totalComments} />
          <StatCard label="Reactions" value={stats.totalReactions} />
          <StatCard label="Groups" value={stats.totalGroups} />
          <StatCard label="Messages" value={stats.totalMessages} />
        </div>
      )}

      {/* ── Active Users ── */}
      {dauMau && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <StatCard label="DAU (Today)" value={dauMau.dau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.dau) / Number(dauMau.totalUsers)) * 100) : 0}%`} />
          <StatCard label="WAU (7d)" value={dauMau.wau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.wau) / Number(dauMau.totalUsers)) * 100) : 0}%`} />
          <StatCard label="MAU (30d)" value={dauMau.mau} badge={`${dauMau.totalUsers ? Math.round((Number(dauMau.mau) / Number(dauMau.totalUsers)) * 100) : 0}%`} />
          <StatCard label="Stickiness" value={dauMau.mau && Number(dauMau.mau) > 0 ? `${Math.round((Number(dauMau.dau) / Number(dauMau.mau)) * 100)}%` : '0%'} badge="DAU/MAU" />
        </div>
      )}

      {/* ── DAU Trend + Post Activity (side by side) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {dauMau?.dauTrend && Array.isArray(dauMau.dauTrend) && dauMau.dauTrend.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Daily Active Users (14d)</h3>
            <BarChart data={dauMau.dauTrend} valueKey="active_users" labelKey="date" color="bg-emerald-500" />
          </div>
        )}
        {activity && Array.isArray(activity) && activity.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Posts (Last 30d)</h3>
            <BarChart data={activity} valueKey="postCount" labelKey="date" />
          </div>
        )}
      </div>

      {/* ── Content Breakdown ── */}
      {contentBreakdown && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
            <StatCard label="Participation" value={`${contentBreakdown.postStats?.userParticipationRate ?? 0}%`} badge="users who posted" />
            <StatCard label="Avg Posts/User" value={contentBreakdown.postStats?.avgPostsPerActiveUser ?? 0} badge="active users" />
            <StatCard label="Avg Reactions/Post" value={contentBreakdown.avgReactionsPerPost ?? 0} />
            <StatCard label="Avg Comments/Post" value={contentBreakdown.avgCommentsPerPost ?? 0} />
            <StatCard label="Active Authors" value={contentBreakdown.postStats?.usersWithPosts ?? 0} badge={`of ${contentBreakdown.postStats?.totalUsers ?? 0}`} />
          </div>

          {/* Reaction Distribution */}
          {contentBreakdown.reactionDistribution && contentBreakdown.reactionDistribution.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Reaction Distribution</h3>
              <div className="flex gap-2 flex-wrap">
                {contentBreakdown.reactionDistribution.map((r: any) => {
                  const total = contentBreakdown.reactionDistribution.reduce((s: number, x: any) => s + Number(x.count), 0);
                  const pct = total > 0 ? Math.round((Number(r.count) / total) * 100) : 0;
                  return (
                    <div key={r.reaction_type} className="flex-1 min-w-[100px] bg-gray-50 rounded-lg p-3 text-center">
                      <div className="text-2xl mb-1">{REACTION_EMOJI[r.reaction_type] ?? r.reaction_type}</div>
                      <div className="text-sm font-bold text-gray-900">{Number(r.count).toLocaleString()}</div>
                      <div className="text-xs text-gray-500">{pct}%</div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Post Target Distribution */}
          {contentBreakdown.postTargetDistribution && contentBreakdown.postTargetDistribution.length > 0 && (
            <div className="bg-white rounded-lg border border-gray-200 p-4">
              <h3 className="text-sm font-semibold text-gray-700 mb-3">Where Are Posts Made?</h3>
              <div className="space-y-2">
                {contentBreakdown.postTargetDistribution.map((t: any) => {
                  const total = contentBreakdown.postTargetDistribution.reduce((s: number, x: any) => s + Number(x.count), 0);
                  const pct = total > 0 ? (Number(t.count) / total) * 100 : 0;
                  const label = (t.target_type || 'USER_FEED').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c: string) => c.toUpperCase());
                  return (
                    <div key={t.target_type} className="flex items-center gap-3">
                      <span className="text-xs text-gray-600 w-28 truncate">{label}</span>
                      <div className="flex-1 bg-gray-100 rounded-full h-5 overflow-hidden">
                        <div className="bg-primary-500 h-full rounded-full transition-all" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="text-xs text-gray-500 w-20 text-right">{Number(t.count).toLocaleString()} ({Math.round(pct)}%)</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}

      {/* ── Activity Heatmap ── */}
      {hourlyActivity && Array.isArray(hourlyActivity) && hourlyActivity.length > 0 && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Posting Activity Heatmap (Last 30d)</h3>
          <div className="overflow-x-auto">
            <div className="inline-grid gap-[2px]" style={{ gridTemplateColumns: `40px repeat(24, 1fr)` }}>
              {/* Hour labels */}
              <div />
              {Array.from({ length: 24 }, (_, h) => (
                <div key={h} className="text-[9px] text-gray-400 text-center">{h}</div>
              ))}
              {/* Rows per day */}
              {DAY_NAMES.map((day, d) => {
                const maxCount = Math.max(...hourlyActivity.map((h: any) => Number(h.count) || 0), 1);
                return (
                  <React.Fragment key={d}>
                    <div className="text-[10px] text-gray-500 flex items-center">{day}</div>
                    {Array.from({ length: 24 }, (_, h) => {
                      const cell = hourlyActivity.find((x: any) => Number(x.day_of_week) === d && Number(x.hour) === h);
                      const count = cell ? Number(cell.count) : 0;
                      const intensity = count / maxCount;
                      return (
                        <div
                          key={h}
                          className="w-full aspect-square rounded-sm group relative"
                          style={{ backgroundColor: count > 0 ? `rgba(79, 70, 229, ${0.15 + intensity * 0.85})` : '#f1f5f9' }}
                          title={`${day} ${h}:00 — ${count} posts`}
                        />
                      );
                    })}
                  </React.Fragment>
                );
              })}
            </div>
          </div>
          <div className="flex items-center gap-2 mt-2 text-[10px] text-gray-400">
            <span>Less</span>
            {[0.1, 0.3, 0.5, 0.7, 1].map(i => (
              <div key={i} className="w-3 h-3 rounded-sm" style={{ backgroundColor: `rgba(79, 70, 229, ${0.15 + i * 0.85})` }} />
            ))}
            <span>More</span>
          </div>
        </div>
      )}

      {/* ── Social Graph + Messaging (side by side) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Social Graph */}
        {socialGraph && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Social Graph</h3>
            <div className="grid grid-cols-2 gap-3 mb-4">
              <MiniCard label="Total Follows" value={Number(socialGraph.totalFollows).toLocaleString()} />
              <MiniCard label="Avg Follows/User" value={socialGraph.avgFollowsPerUser} />
              <MiniCard label="Group Memberships" value={Number(socialGraph.totalMemberships).toLocaleString()} />
              <MiniCard label="Friend Requests" value={`${socialGraph.acceptedFriendRequests} accepted / ${socialGraph.pendingFriendRequests} pending`} />
            </div>
            {socialGraph.mostFollowed && socialGraph.mostFollowed.length > 0 && (
              <>
                <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Most Followed</h4>
                <div className="space-y-1.5">
                  {socialGraph.mostFollowed.slice(0, 5).map((u: any, i: number) => (
                    <div key={String(u.id)} className="flex items-center gap-2 text-sm">
                      <span className="text-gray-400 w-4 text-right">{i + 1}</span>
                      {u.avatar_url ? (
                        <img src={u.avatar_url} className="w-6 h-6 rounded-full" alt="" />
                      ) : (
                        <div className="w-6 h-6 bg-primary-400 text-white rounded-full flex items-center justify-center text-[10px] font-bold">
                          {(u.display_name ?? u.username ?? '?')[0]?.toUpperCase()}
                        </div>
                      )}
                      <Link to={`/profile/${u.id}`} className="text-primary-600 hover:underline flex-1 truncate">
                        {u.display_name || u.username}
                      </Link>
                      <span className="text-gray-500 font-medium">{u.follower_count}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* Messaging */}
        {messaging && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Messaging</h3>
            <div className="grid grid-cols-2 gap-3 mb-4">
              <MiniCard label="Total Messages" value={Number(messaging.totalMessages).toLocaleString()} />
              <MiniCard label="Conversations" value={Number(messaging.uniqueConversations).toLocaleString()} />
              <MiniCard label="Users Messaging" value={Number(messaging.usersWhoMessaged).toLocaleString()} />
              <MiniCard label="Avg/Conversation" value={messaging.uniqueConversations > 0 ? Math.round(Number(messaging.totalMessages) / Number(messaging.uniqueConversations)) : 0} />
            </div>
            {messaging.messageTrend && messaging.messageTrend.length > 0 && (
              <>
                <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Messages (14d)</h4>
                <BarChart data={messaging.messageTrend} valueKey="msg_count" labelKey="date" color="bg-cyan-500" />
              </>
            )}
          </div>
        )}
      </div>

      {/* ── Top Users / Groups / Pages (3 columns) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Top Users */}
        {topUsers && Array.isArray(topUsers) && topUsers.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Posters (7d)</h3>
            <div className="space-y-2">
              {topUsers.map((u: any, i: number) => (
                <div key={String(u.userId ?? u.id ?? i)} className="flex items-center gap-2 text-sm">
                  <span className="text-gray-400 w-4 text-right">{i + 1}</span>
                  {u.avatarUrl ? (
                    <img src={u.avatarUrl} className="w-6 h-6 rounded-full" alt="" />
                  ) : (
                    <div className="w-6 h-6 bg-primary-400 text-white rounded-full flex items-center justify-center text-[10px] font-bold">
                      {(u.displayName ?? u.username ?? '?')[0]?.toUpperCase()}
                    </div>
                  )}
                  <Link to={`/profile/${u.userId ?? u.id}`} className="text-primary-600 hover:underline flex-1 truncate">
                    {u.displayName || u.username}
                  </Link>
                  <span className="text-gray-500 font-medium">{u.postCount}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Top Groups */}
        {topGroups && Array.isArray(topGroups) && topGroups.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Groups</h3>
            <div className="space-y-2">
              {topGroups.map((g: any, i: number) => (
                <div key={String(g.groupId ?? g.id ?? i)} className="flex items-center gap-2 text-sm">
                  <span className="text-gray-400 w-4 text-right">{i + 1}</span>
                  <Link to={`/group/${g.groupId ?? g.id}`} className="text-primary-600 hover:underline flex-1 truncate">
                    {g.name}
                  </Link>
                  <span className="text-xs text-gray-400">{g.memberCount}m</span>
                  <span className="text-gray-500 font-medium">{g.postCount}p</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Top Pages */}
        {topPages && Array.isArray(topPages) && topPages.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Top Pages</h3>
            <div className="space-y-2">
              {topPages.map((p: any, i: number) => (
                <div key={String(p.pageId ?? p.id ?? i)} className="flex items-center gap-2 text-sm">
                  <span className="text-gray-400 w-4 text-right">{i + 1}</span>
                  <Link to={`/page/${p.pageId ?? p.id}`} className="text-primary-600 hover:underline flex-1 truncate">
                    {p.name}
                  </Link>
                  <span className="text-xs text-gray-400">{p.followerCount}f</span>
                  <span className="text-gray-500 font-medium">{p.postCount}p</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* ── Growth + System (side by side) ── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {growth && Array.isArray(growth) && growth.length > 0 && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">User Growth (Weekly Signups)</h3>
            <BarChart data={growth} valueKey="signups" labelKey="week" color="bg-violet-500" />
          </div>
        )}

        {system && (
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">System Health</h3>
            <div className="grid grid-cols-2 gap-3">
              <MiniCard label="Upload Dir" value={system.uploadDirSize ?? 'N/A'} />
              <MiniCard label="Files" value={system.fileCount ?? 'N/A'} />
              <MiniCard label="DB Size" value={system.databaseSize ?? system.dbSize ?? 'N/A'} />
              <MiniCard label="Duplicates" value={system.duplicateAttachments ?? 'N/A'} />
            </div>
          </div>
        )}
      </div>
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
  const [subTab, setSubTab] = useState<'directory' | 'invite' | 'batch' | 'pending'>('directory');

  const subTabs = [
    { key: 'directory' as const, label: 'User Directory' },
    { key: 'invite' as const, label: 'Invite User' },
    { key: 'batch' as const, label: 'Batch Import' },
    { key: 'pending' as const, label: 'Pending Invites' },
  ];

  return (
    <div className="space-y-4">
      <div className="flex gap-1 bg-gray-100 rounded-lg p-1 w-fit">
        {subTabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setSubTab(t.key)}
            className={`px-3 py-1.5 text-sm rounded-md transition-colors ${
              subTab === t.key
                ? 'bg-white shadow-sm text-gray-900 font-medium'
                : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {subTab === 'directory' && <UserDirectorySection />}
      {subTab === 'invite' && <InviteUserSection />}
      {subTab === 'batch' && <BatchImportSection />}
      {subTab === 'pending' && <PendingInvitesSection />}
    </div>
  );
}

function UserDirectorySection() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', page, search],
    queryFn: () =>
      api.get('/admin/users', { params: { page, size: 20, q: search || undefined } }).then((r) => r.data),
    placeholderData: (prev: any) => prev,
  });

  const toggleAdmin = useMutation({
    mutationFn: (userId: string) => api.put(`/admin/users/${userId}/admin`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  });

  const users = data?.content ?? data?.users ?? (Array.isArray(data) ? data : []);

  return (
    <div className="space-y-4">
      <input
        type="text"
        placeholder="Search users by name, email..."
        value={search}
        onChange={(e) => { setSearch(e.target.value); setPage(0); }}
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
                  <th className="px-4 py-3">Email</th>
                  <th className="px-4 py-3">Department</th>
                  <th className="px-4 py-3 text-center">Status</th>
                  <th className="px-4 py-3 text-center">Role</th>
                  <th className="px-4 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u: any) => (
                  <tr key={String(u.id)} className="even:bg-gray-50 border-b border-gray-100">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {u.avatar_url || u.avatarUrl ? (
                          <img src={u.avatar_url || u.avatarUrl} alt="" className="w-7 h-7 rounded-full object-cover" />
                        ) : (
                          <div className="w-7 h-7 bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-bold">
                            {(u.display_name ?? u.displayName ?? u.username ?? '?')[0]?.toUpperCase()}
                          </div>
                        )}
                        <div>
                          <div className="font-medium text-gray-900">{u.display_name ?? u.displayName}</div>
                          <div className="text-xs text-gray-400">@{u.username}</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{u.email}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">{u.department || u.job_title || '-'}</td>
                    <td className="px-4 py-3 text-center">
                      {u.status === 'PENDING_SETUP' ? (
                        <span className="inline-block bg-amber-100 text-amber-700 text-xs font-medium px-2 py-0.5 rounded">
                          Pending Setup
                        </span>
                      ) : (
                        <span className="inline-block bg-green-100 text-green-700 text-xs font-medium px-2 py-0.5 rounded">
                          Active
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center">
                      {(u.is_admin || u.admin) && (
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
                          {(u.is_admin || u.admin) ? 'Remove Admin' : 'Make Admin'}
                        </button>
                        <Link to={`/profile/${String(u.id)}`} className="text-xs text-gray-500 hover:underline">
                          View
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))}
                {users.length === 0 && (
                  <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">No users found</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50">Previous</button>
            <span className="text-sm text-gray-500">Page {page + 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={users.length < 20} className="btn-primary text-sm px-3 py-1.5 disabled:opacity-50">Next</button>
          </div>
        </>
      )}
    </div>
  );
}

function InviteUserSection() {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [department, setDepartment] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const [selectedGroups, setSelectedGroups] = useState<number[]>([]);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);

  const { data: groups } = useQuery({
    queryKey: ['admin-groups-list'],
    queryFn: () => api.get('/admin/groups', { params: { size: 100 } }).then((r) => r.data),
  });
  const groupList = Array.isArray(groups) ? groups : [];

  const inviteMutation = useMutation({
    mutationFn: (payload: any) => api.post('/admin/users/invite', payload),
    onSuccess: ({ data }) => {
      setResult(data);
      setError('');
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      queryClient.invalidateQueries({ queryKey: ['admin-invites'] });
    },
    onError: (err: any) => {
      setError(err.response?.data?.message ?? 'Failed to create invite');
      setResult(null);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    inviteMutation.mutate({
      email,
      displayName: displayName || email.split('@')[0],
      department: department || null,
      jobTitle: jobTitle || null,
      groupIds: selectedGroups,
      admin: isAdmin,
    });
  };

  const copyLink = () => {
    if (result?.inviteUrl) {
      const url = result.inviteUrl.startsWith('http') ? result.inviteUrl : window.location.origin + result.inviteUrl;
      navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const reset = () => {
    setEmail(''); setDisplayName(''); setDepartment(''); setJobTitle('');
    setIsAdmin(false); setSelectedGroups([]); setResult(null); setError('');
  };

  if (result) {
    const fullUrl = result.inviteUrl?.startsWith('http') ? result.inviteUrl : window.location.origin + (result.inviteUrl || '');
    return (
      <div className="max-w-lg">
        <div className="bg-green-50 border border-green-200 rounded-lg p-6">
          <h3 className="text-lg font-semibold text-green-800 mb-2">Invite Created</h3>
          <p className="text-sm text-green-700 mb-4">
            An account has been created for <strong>{result.displayName}</strong> ({result.email}).
            Share the setup link below so they can complete their account.
          </p>
          <div className="flex gap-2">
            <input type="text" readOnly value={fullUrl} className="input-field flex-1 text-xs bg-white" />
            <button onClick={copyLink} className="btn-primary text-sm px-3 whitespace-nowrap">
              {copied ? 'Copied!' : 'Copy Link'}
            </button>
          </div>
          <div className="mt-3 p-3 bg-white rounded border border-green-100 text-xs text-gray-500">
            <strong>Email integration:</strong> To automatically email invite links, configure SMTP settings
            (SMTP_HOST, SMTP_USER, SMTP_PASS environment variables). Until then, share links manually.
          </div>
          <button onClick={reset} className="mt-4 text-sm text-green-700 hover:underline">
            Invite another user
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-lg">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Invite a New User</h3>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="input-field w-full" placeholder="user@company.com" required />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
            <input type="text" value={displayName} onChange={(e) => setDisplayName(e.target.value)} className="input-field w-full" placeholder="Jane Doe" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Department</label>
              <input type="text" value={department} onChange={(e) => setDepartment(e.target.value)} className="input-field w-full" placeholder="Engineering" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Job Title</label>
              <input type="text" value={jobTitle} onChange={(e) => setJobTitle(e.target.value)} className="input-field w-full" placeholder="Software Engineer" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Add to Groups</label>
            <div className="max-h-32 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
              {groupList.length === 0 && <div className="text-xs text-gray-400 p-1">No groups available</div>}
              {groupList.map((g: any) => (
                <label key={g.id} className="flex items-center gap-2 text-sm text-gray-700 hover:bg-gray-50 px-1 rounded cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selectedGroups.includes(g.id)}
                    onChange={(e) => {
                      setSelectedGroups((prev) =>
                        e.target.checked ? [...prev, g.id] : prev.filter((id) => id !== g.id)
                      );
                    }}
                    className="rounded border-gray-300"
                  />
                  {g.name} <span className="text-xs text-gray-400">({g.member_count ?? 0} members)</span>
                </label>
              ))}
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="checkbox" checked={isAdmin} onChange={(e) => setIsAdmin(e.target.checked)} className="rounded border-gray-300" />
            Grant admin privileges
          </label>
          {error && <div className="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{error}</div>}
          <button type="submit" disabled={inviteMutation.isPending} className="btn-primary w-full">
            {inviteMutation.isPending ? 'Creating...' : 'Create Invite'}
          </button>
        </form>
      </div>
    </div>
  );
}

function BatchImportSection() {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string[][]>([]);
  const [result, setResult] = useState<any>(null);
  const [error, setError] = useState('');
  const [copiedIdx, setCopiedIdx] = useState<number | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setFile(f);
    setResult(null);
    setError('');

    const reader = new FileReader();
    reader.onload = (ev) => {
      const text = ev.target?.result as string;
      const lines = text.split('\n').filter((l) => l.trim());
      setPreview(lines.slice(0, 6).map((l) => l.split(',')));
    };
    reader.readAsText(f);
  };

  const uploadMutation = useMutation({
    mutationFn: (formData: FormData) => api.post('/admin/users/invite/batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
    onSuccess: ({ data }) => {
      setResult(data);
      setError('');
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      queryClient.invalidateQueries({ queryKey: ['admin-invites'] });
    },
    onError: (err: any) => setError(err.response?.data?.message ?? 'Upload failed'),
  });

  const handleUpload = () => {
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    uploadMutation.mutate(formData);
  };

  const copyLink = (url: string, idx: number) => {
    const full = url.startsWith('http') ? url : window.location.origin + url;
    navigator.clipboard.writeText(full);
    setCopiedIdx(idx);
    setTimeout(() => setCopiedIdx(null), 2000);
  };

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-2">Batch Import Users</h3>
        <p className="text-sm text-gray-500 mb-4">
          Upload a CSV file to invite multiple users at once. Each user will get a unique setup link.
        </p>

        <div className="bg-gray-50 rounded-lg p-4 mb-4">
          <div className="text-sm font-medium text-gray-700 mb-2">CSV Format</div>
          <code className="text-xs text-gray-600 block">
            email,displayName,department,jobTitle,groups,admin<br />
            jane@company.com,Jane Doe,Engineering,Software Engineer,Coffee Lovers;Book Club,false<br />
            bob@company.com,Bob Smith,Marketing,Content Lead,,false
          </code>
          <div className="text-xs text-gray-400 mt-2">
            Only <strong>email</strong> is required. Groups use semicolons to separate multiple names.
          </div>
        </div>

        <input type="file" accept=".csv,text/csv" onChange={handleFileChange}
          className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-sm file:font-medium file:bg-primary-50 file:text-primary-700 hover:file:bg-primary-100" />

        {preview.length > 0 && (
          <div className="mt-4">
            <div className="text-sm font-medium text-gray-700 mb-2">Preview (first 5 rows)</div>
            <div className="overflow-x-auto">
              <table className="text-xs w-full border border-gray-200">
                {preview.map((row, i) => (
                  <tr key={i} className={i === 0 ? 'bg-gray-100 font-medium' : 'even:bg-gray-50'}>
                    {row.map((cell, j) => (
                      <td key={j} className="px-2 py-1 border-r border-gray-200">{cell.trim()}</td>
                    ))}
                  </tr>
                ))}
              </table>
            </div>
            <button onClick={handleUpload} disabled={uploadMutation.isPending} className="btn-primary mt-3">
              {uploadMutation.isPending ? 'Importing...' : `Import ${preview.length - 1} Users`}
            </button>
          </div>
        )}

        {error && <div className="mt-3 text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">{error}</div>}
      </div>

      {result && (
        <div className="bg-white rounded-lg border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Import Results</h3>
          <div className="flex gap-4 mb-4">
            <div className="text-sm"><span className="font-medium text-green-600">{result.created}</span> created</div>
            {result.failed > 0 && <div className="text-sm"><span className="font-medium text-red-600">{result.failed}</span> failed</div>}
            <div className="text-sm text-gray-500">{result.total} total</div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b bg-gray-50">
                  <th className="px-3 py-2">Row</th>
                  <th className="px-3 py-2">Email</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Setup Link</th>
                </tr>
              </thead>
              <tbody>
                {result.results?.map((r: any, i: number) => (
                  <tr key={i} className="border-b border-gray-100">
                    <td className="px-3 py-2 text-gray-500">{r.row}</td>
                    <td className="px-3 py-2">{r.email}</td>
                    <td className="px-3 py-2">
                      {r.status === 'created' ? (
                        <span className="text-green-600 font-medium">Created</span>
                      ) : (
                        <span className="text-red-600" title={r.error}>Failed: {r.error}</span>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {r.inviteUrl && (
                        <button onClick={() => copyLink(r.inviteUrl, i)} className="text-xs text-primary-600 hover:underline">
                          {copiedIdx === i ? 'Copied!' : 'Copy Link'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function PendingInvitesSection() {
  const queryClient = useQueryClient();
  const [copiedToken, setCopiedToken] = useState<string | null>(null);

  const { data: invites, isLoading } = useQuery({
    queryKey: ['admin-invites'],
    queryFn: () => api.get('/admin/invites').then((r) => r.data),
  });

  const regenerate = useMutation({
    mutationFn: (userId: number) => api.post(`/admin/invites/${userId}/regenerate`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-invites'] }),
  });

  const revoke = useMutation({
    mutationFn: (tokenId: number) => api.delete(`/admin/invites/${tokenId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-invites'] }),
  });

  const copyLink = (url: string) => {
    const full = url.startsWith('http') ? url : window.location.origin + url;
    navigator.clipboard.writeText(full);
    setCopiedToken(url);
    setTimeout(() => setCopiedToken(null), 2000);
  };

  const list = Array.isArray(invites) ? invites : [];

  if (isLoading) return <div className="text-gray-400 py-10 text-center">Loading invites...</div>;

  return (
    <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-gray-500 border-b bg-gray-50">
            <th className="px-4 py-3">User</th>
            <th className="px-4 py-3">Email</th>
            <th className="px-4 py-3 text-center">Status</th>
            <th className="px-4 py-3">Created</th>
            <th className="px-4 py-3">Expires</th>
            <th className="px-4 py-3">Actions</th>
          </tr>
        </thead>
        <tbody>
          {list.map((inv: any) => (
            <tr key={String(inv.id)} className="even:bg-gray-50 border-b border-gray-100">
              <td className="px-4 py-3 font-medium text-gray-900">{inv.displayName ?? inv.username}</td>
              <td className="px-4 py-3 text-gray-500 text-xs">{inv.email}</td>
              <td className="px-4 py-3 text-center">
                {inv.status === 'USED' && (
                  <span className="inline-block bg-green-100 text-green-700 text-xs font-medium px-2 py-0.5 rounded">Completed</span>
                )}
                {inv.status === 'EXPIRED' && (
                  <span className="inline-block bg-red-100 text-red-700 text-xs font-medium px-2 py-0.5 rounded">Expired</span>
                )}
                {inv.status === 'PENDING' && (
                  <span className="inline-block bg-amber-100 text-amber-700 text-xs font-medium px-2 py-0.5 rounded">Pending</span>
                )}
              </td>
              <td className="px-4 py-3 text-xs text-gray-500">
                {inv.createdAt ? new Date(inv.createdAt).toLocaleDateString() : '-'}
              </td>
              <td className="px-4 py-3 text-xs text-gray-500">
                {inv.expiresAt ? new Date(inv.expiresAt).toLocaleDateString() : '-'}
              </td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  {inv.status === 'PENDING' && (
                    <>
                      <button onClick={() => copyLink(inv.inviteUrl)} className="text-xs text-primary-600 hover:underline">
                        {copiedToken === inv.inviteUrl ? 'Copied!' : 'Copy Link'}
                      </button>
                      <button onClick={() => revoke.mutate(inv.id)} className="text-xs text-red-500 hover:underline">
                        Revoke
                      </button>
                    </>
                  )}
                  {inv.status === 'EXPIRED' && (
                    <button onClick={() => regenerate.mutate(inv.userId)} className="text-xs text-primary-600 hover:underline">
                      Regenerate
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
          {list.length === 0 && (
            <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">No invites yet</td></tr>
          )}
        </tbody>
      </table>
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

function ThemeSelector() {
  const queryClient = useQueryClient();

  const { data: currentTheme } = useQuery<{ theme: string }>({
    queryKey: ['platform-theme'],
    queryFn: () => api.get('/settings/theme').then(r => r.data),
  });

  const setTheme = useMutation({
    mutationFn: (theme: string) => api.put('/admin/settings/theme', { theme }),
    onSuccess: (_, theme) => {
      document.documentElement.setAttribute('data-theme', theme);
      queryClient.setQueryData(['platform-theme'], { theme });
    },
  });

  const themes = [
    {
      id: 'modern-collaboration',
      name: 'Modern Collaboration',
      description: 'Indigo + teal + cool gray — balanced and current',
      colors: ['#4f46e5', '#14b8a6', '#64748b'],
    },
    {
      id: 'serious-enterprise',
      name: 'Serious Enterprise',
      description: 'Navy + steel blue + white — corporate and professional',
      colors: ['#1b3a5c', '#4a90b0', '#6e7a89'],
    },
    {
      id: 'premium-culture',
      name: 'Premium Culture',
      description: 'Deep plum + blue-gray + soft gold — distinctive brand',
      colors: ['#6b2fa0', '#c9a032', '#6b6b7a'],
    },
    {
      id: 'worksphere',
      name: 'WorkSphere',
      description: 'Indigo/cobalt + teal accent + charcoal text',
      colors: ['#4338ca', '#0d9488', '#334155'],
    },
  ];

  return (
    <div className="bg-white border border-gray-200 rounded-xl p-6">
      <h3 className="text-lg font-semibold text-gray-900 mb-1">Platform Theme</h3>
      <p className="text-sm text-gray-500 mb-4">Choose a color theme for all users. Changes take effect immediately.</p>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {themes.map(theme => {
          const isActive = currentTheme?.theme === theme.id;
          return (
            <button
              key={theme.id}
              onClick={() => setTheme.mutate(theme.id)}
              disabled={setTheme.isPending}
              className={`p-4 rounded-xl border-2 text-left transition-all ${
                isActive
                  ? 'border-primary-500 bg-primary-50 shadow-sm'
                  : 'border-gray-200 hover:border-gray-300 hover:shadow-sm'
              }`}
            >
              <div className="flex gap-1.5 mb-3">
                {theme.colors.map((color, i) => (
                  <div
                    key={i}
                    className="w-6 h-6 rounded-full border border-white shadow-sm"
                    style={{ backgroundColor: color }}
                  />
                ))}
              </div>
              <div className="text-sm font-semibold text-gray-900">{theme.name}</div>
              <div className="text-xs text-gray-500 mt-0.5">{theme.description}</div>
              {isActive && (
                <div className="text-xs text-primary-600 font-medium mt-2">Active</div>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
