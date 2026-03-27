import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import api from '../api/client';

interface OrgUnit {
  id: number;
  name: string;
  type: string;
  parentId: number | null;
  headUserId: number | null;
  headUserName: string | null;
  description: string | null;
  childCount: number;
  memberCount: number;
}

interface OrgAssignment {
  id: number;
  userId: number;
  userName: string;
  userAvatarUrl: string | null;
  orgUnitId: number;
  orgUnitName: string;
  title: string;
  relationshipType: string;
  reportsToUserId: number | null;
  reportsToUserName: string | null;
  level: string;
}

export default function OrgPage() {
  const [searchParams] = useSearchParams();
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);

  // Auto-select user from URL query param
  useEffect(() => {
    const userParam = searchParams.get('user');
    if (userParam) {
      setSelectedUserId(Number(userParam));
    }
  }, [searchParams]);

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold text-gray-900">Organization</h1>

      {/* Search */}
      <div className="flex gap-2">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search for a person..."
          className="input-field flex-1"
        />
      </div>

      {searchQuery.trim().length > 1 && (
        <PersonSearch query={searchQuery} onSelect={(id) => { setSelectedUserId(id); setSearchQuery(''); }} />
      )}

      {selectedUserId ? (
        <PersonOrgView userId={selectedUserId} onNavigate={setSelectedUserId} />
      ) : (
        <OrgTreeView onSelectUser={setSelectedUserId} />
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Person Search Results                                               */
/* ------------------------------------------------------------------ */

function PersonSearch({ query, onSelect }: { query: string; onSelect: (id: number) => void }) {
  const { data: results } = useQuery<any[]>({
    queryKey: ['user-search-org', query],
    queryFn: async () => {
      const { data } = await api.get(`/users/search?q=${encodeURIComponent(query)}`);
      return data;
    },
    enabled: query.trim().length > 1,
  });

  if (!results?.length) return null;

  return (
    <div className="card p-2 max-h-48 overflow-y-auto">
      {results.slice(0, 8).map((u: any) => (
        <button
          key={u.id}
          onClick={() => onSelect(u.id)}
          className="w-full flex items-center gap-2 p-2 hover:bg-gray-50 rounded-lg text-left"
        >
          {u.avatarUrl ? (
            <img src={u.avatarUrl} alt="" className="w-8 h-8 rounded-full object-cover" />
          ) : (
            <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-xs font-semibold">
              {u.displayName?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
          <div>
            <div className="text-sm font-medium text-gray-900">{u.displayName}</div>
            <div className="text-xs text-gray-500">{u.jobTitle || u.department || u.username}</div>
          </div>
        </button>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Person Org View — chain up + reports down                           */
/* ------------------------------------------------------------------ */

function PersonOrgView({ userId, onNavigate }: { userId: number; onNavigate: (id: number) => void }) {
  const { data: chain } = useQuery<OrgAssignment[]>({
    queryKey: ['org-chain', userId],
    queryFn: async () => { const { data } = await api.get(`/org/assignments/chain/${userId}`); return data; },
  });

  const { data: assignments } = useQuery<OrgAssignment[]>({
    queryKey: ['org-assignments', userId],
    queryFn: async () => { const { data } = await api.get(`/org/assignments/user/${userId}`); return data; },
  });

  const { data: reports } = useQuery<OrgAssignment[]>({
    queryKey: ['org-reports', userId],
    queryFn: async () => { const { data } = await api.get(`/org/assignments/reports/${userId}`); return data; },
  });

  const currentAssignment = assignments?.[0];

  return (
    <div className="space-y-4">
      {/* Reporting chain (upward) */}
      {chain && chain.length > 0 && (
        <div className="card p-4">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Reports To</h3>
          <div className="space-y-1">
            {[...chain].reverse().map((a, i) => (
              <div key={a.id} className="flex items-center gap-2" style={{ paddingLeft: `${i * 16}px` }}>
                {i > 0 && <span className="text-gray-300 text-xs">└</span>}
                <button onClick={() => onNavigate(a.userId)} className="flex items-center gap-2 hover:bg-gray-50 rounded-lg p-1.5 transition-colors">
                  <PersonBadge avatarUrl={a.userAvatarUrl} name={a.userName} />
                  <div>
                    <span className="text-sm font-medium text-gray-900">{a.userName}</span>
                    <span className="text-xs text-gray-500 ml-2">{a.title}</span>
                  </div>
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Current person */}
      {currentAssignment && (
        <div className="card p-4 border-2 border-primary-500 bg-primary-50/30">
          <div className="flex items-center gap-3">
            <PersonBadge avatarUrl={currentAssignment.userAvatarUrl} name={currentAssignment.userName} size={12} />
            <div>
              <Link to={`/profile/${currentAssignment.userId}`} className="text-lg font-bold text-gray-900 hover:underline">
                {currentAssignment.userName}
              </Link>
              <div className="text-sm text-gray-600">{currentAssignment.title}</div>
              <div className="flex items-center gap-2 mt-1">
                <span className="text-xs bg-primary-100 text-primary-700 px-2 py-0.5 rounded-full font-medium">
                  {currentAssignment.level}
                </span>
                <span className="text-xs text-gray-500">{currentAssignment.orgUnitName}</span>
                {currentAssignment.relationshipType === 'DOTTED' && (
                  <span className="text-xs bg-yellow-100 text-yellow-700 px-2 py-0.5 rounded-full">Dotted Line</span>
                )}
              </div>
            </div>
          </div>

          {/* Show all assignments if multiple */}
          {assignments && assignments.length > 1 && (
            <div className="mt-3 pt-3 border-t border-primary-200">
              <span className="text-xs text-gray-500">Also assigned to:</span>
              {assignments.slice(1).map((a) => (
                <div key={a.id} className="text-xs text-gray-600 mt-1">
                  {a.title} in {a.orgUnitName}
                  {a.relationshipType === 'DOTTED' && <span className="text-yellow-600 ml-1">(dotted line)</span>}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Direct reports (downward) */}
      {reports && reports.length > 0 && (
        <div className="card p-4">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">
            Direct Reports ({reports.length})
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {reports.map((r) => (
              <button
                key={r.id}
                onClick={() => onNavigate(r.userId)}
                className="flex items-center gap-2 p-2 hover:bg-gray-50 rounded-lg text-left transition-colors"
              >
                <PersonBadge avatarUrl={r.userAvatarUrl} name={r.userName} />
                <div className="min-w-0">
                  <div className="text-sm font-medium text-gray-900 truncate">{r.userName}</div>
                  <div className="text-xs text-gray-500 truncate">{r.title}</div>
                </div>
                <DirectReportsBadge userId={r.userId} />
              </button>
            ))}
          </div>
        </div>
      )}

      {(!reports || reports.length === 0) && currentAssignment && (
        <div className="card p-4 text-center text-sm text-gray-400">
          No direct reports
        </div>
      )}
    </div>
  );
}

function DirectReportsBadge({ userId }: { userId: number }) {
  const { data } = useQuery<OrgAssignment[]>({
    queryKey: ['org-reports-count', userId],
    queryFn: async () => { const { data } = await api.get(`/org/assignments/reports/${userId}`); return data; },
    staleTime: 60000,
  });
  if (!data?.length) return null;
  return (
    <span className="ml-auto text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded-full shrink-0">
      {data.length} reports
    </span>
  );
}

/* ------------------------------------------------------------------ */
/* Org Tree View (expandable)                                          */
/* ------------------------------------------------------------------ */

function OrgTreeView({ onSelectUser }: { onSelectUser: (id: number) => void }) {
  const { data: roots } = useQuery<OrgUnit[]>({
    queryKey: ['org-roots'],
    queryFn: async () => { const { data } = await api.get('/org/units'); return data; },
  });

  if (!roots?.length) return <div className="text-sm text-gray-400 text-center py-8">No org structure defined</div>;

  return (
    <div className="card p-4">
      <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Organization Tree</h3>
      {roots.map((unit) => (
        <OrgUnitNode key={unit.id} unit={unit} depth={0} onSelectUser={onSelectUser} />
      ))}
    </div>
  );
}

function OrgUnitNode({ unit, depth, onSelectUser }: { unit: OrgUnit; depth: number; onSelectUser: (id: number) => void }) {
  const [expanded, setExpanded] = useState(depth < 1);

  const { data: children } = useQuery<OrgUnit[]>({
    queryKey: ['org-children', unit.id],
    queryFn: async () => { const { data } = await api.get(`/org/units/${unit.id}/children`); return data; },
    enabled: expanded && unit.childCount > 0,
  });

  const { data: members } = useQuery<OrgAssignment[]>({
    queryKey: ['org-unit-members', unit.id],
    queryFn: async () => { const { data } = await api.get(`/org/units/${unit.id}/members`); return data; },
    enabled: expanded,
  });

  const typeColors: Record<string, string> = {
    COMPANY: 'bg-purple-100 text-purple-700',
    DIVISION: 'bg-blue-100 text-blue-700',
    DEPARTMENT: 'bg-green-100 text-green-700',
    TEAM: 'bg-orange-100 text-orange-700',
  };

  return (
    <div style={{ marginLeft: `${depth * 16}px` }}>
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 py-1.5 px-2 hover:bg-gray-50 rounded-lg text-left transition-colors"
      >
        <span className="text-gray-400 text-xs w-4 text-center">
          {(unit.childCount > 0 || unit.memberCount > 0) ? (expanded ? '▼' : '▶') : '·'}
        </span>
        <span className={`text-[10px] px-1.5 py-0.5 rounded font-medium ${typeColors[unit.type] || 'bg-gray-100 text-gray-600'}`}>
          {unit.type}
        </span>
        <span className="text-sm font-medium text-gray-900">{unit.name}</span>
        {unit.headUserName && (
          <span className="text-xs text-gray-400 ml-1">({unit.headUserName})</span>
        )}
        <span className="text-[10px] text-gray-400 ml-auto">
          {unit.memberCount > 0 && `${unit.memberCount} people`}
          {unit.childCount > 0 && ` · ${unit.childCount} sub-units`}
        </span>
      </button>

      {expanded && members && members.length > 0 && (
        <div className="ml-8 mb-1">
          {members.map((m) => (
            <button
              key={m.id}
              onClick={() => onSelectUser(m.userId)}
              className="flex items-center gap-2 py-1 px-2 hover:bg-gray-50 rounded text-left w-full"
            >
              <PersonBadge avatarUrl={m.userAvatarUrl} name={m.userName} size={6} />
              <span className="text-xs text-gray-700">{m.userName}</span>
              <span className="text-[10px] text-gray-400">{m.title}</span>
              {m.relationshipType === 'DOTTED' && (
                <span className="text-[9px] text-yellow-600 border border-yellow-300 px-1 rounded">dotted</span>
              )}
            </button>
          ))}
        </div>
      )}

      {expanded && children?.map((child) => (
        <OrgUnitNode key={child.id} unit={child} depth={depth + 1} onSelectUser={onSelectUser} />
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Shared                                                              */
/* ------------------------------------------------------------------ */

function PersonBadge({ avatarUrl, name, size = 8 }: { avatarUrl?: string | null; name: string; size?: number }) {
  const dim = `w-${size} h-${size}`;
  if (avatarUrl) {
    return <img src={avatarUrl} alt="" className={`${dim} rounded-full object-cover shrink-0`} />;
  }
  return (
    <div className={`${dim} bg-primary-500 text-white rounded-full flex items-center justify-center text-[10px] font-semibold shrink-0`}>
      {name[0]?.toUpperCase() ?? '?'}
    </div>
  );
}
