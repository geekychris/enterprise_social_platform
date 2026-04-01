import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import api from '../api/client';

interface SupportCase {
  id: number;
  caseNumber: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  requesterId: number;
  assigneeId: number | null;
  sourcePostId: number | null;
  appId: number | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  requesterName?: string;
  assigneeName?: string;
}

const STATUS_COLORS: Record<string, string> = {
  OPEN: 'bg-blue-100 text-blue-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700',
  WAITING: 'bg-orange-100 text-orange-700',
  RESOLVED: 'bg-green-100 text-green-700',
  CLOSED: 'bg-gray-100 text-gray-500',
};

const PRIORITY_COLORS: Record<string, string> = {
  LOW: 'text-gray-400',
  NORMAL: 'text-blue-500',
  HIGH: 'text-orange-500',
  URGENT: 'text-red-600',
};

export default function SupportPage() {
  const { userId, isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [selectedCase, setSelectedCase] = useState<SupportCase | null>(null);
  const [filter, setFilter] = useState<string>('OPEN');
  const [replyText, setReplyText] = useState('');
  const [selectedApp, setSelectedApp] = useState<number | null>(null);

  // Check if user has access — must be admin or have app installations they manage
  const { data: myInstallations } = useQuery<Array<{ appId: number; appName: string }>>({
    queryKey: ['my-app-installations'],
    queryFn: async () => {
      try {
        const { data } = await api.get('/app-registry/my-installations');
        return data;
      } catch {
        return [];
      }
    },
  });

  const hasAccess = isAdmin || (myInstallations && myInstallations.length > 0);

  if (!hasAccess && myInstallations !== undefined) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <div className="text-4xl mb-4">&#128274;</div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Support Access Required</h2>
          <p className="text-gray-500">You need to be an admin or have app management permissions to access the support queue.</p>
        </div>
      </div>
    );
  }

  // Get unique apps from installations for the app filter
  const appOptions = myInstallations
    ? [...new Map(myInstallations.map(i => [i.appId, i.appName])).entries()].map(([id, name]) => ({ id, name }))
    : [];

  const { data: cases, isLoading } = useQuery<SupportCase[]>({
    queryKey: ['support-cases', filter, selectedApp],
    queryFn: () => {
      const params = new URLSearchParams();
      if (filter !== 'ALL') params.set('status', filter);
      if (selectedApp) params.set('appId', String(selectedApp));
      return api.get(`/cases?${params.toString()}`).then(r => r.data);
    },
    refetchInterval: 10000,
  });

  const { data: stats } = useQuery<Record<string, number>>({
    queryKey: ['case-stats'],
    queryFn: () => api.get('/cases/stats').then(r => r.data),
    staleTime: 30000,
  });

  const assignMutation = useMutation({
    mutationFn: (caseId: number) => api.post(`/cases/${caseId}/assign`, { assigneeId: userId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['support-cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
    },
  });

  const resolveMutation = useMutation({
    mutationFn: ({ caseId, resolution }: { caseId: number; resolution: string }) =>
      api.post(`/cases/${caseId}/resolve`, { resolution }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['support-cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
      setSelectedCase(null);
    },
  });

  const replyMutation = useMutation({
    mutationFn: ({ postId, content }: { postId: number; content: string }) =>
      api.post('/comments', { postId: String(postId), content }),
    onSuccess: () => {
      setReplyText('');
      queryClient.invalidateQueries({ queryKey: ['support-cases'] });
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ caseId, status }: { caseId: number; status: string }) =>
      api.put(`/cases/${caseId}`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['support-cases'] });
      queryClient.invalidateQueries({ queryKey: ['case-stats'] });
    },
  });

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Support Queue</h1>
          <p className="text-sm text-gray-500">Manage support cases from app integrations</p>
        </div>
        {stats && (
          <div className="flex gap-4 text-sm">
            <div className="text-center">
              <div className="text-2xl font-bold text-blue-600">{stats.open ?? 0}</div>
              <div className="text-gray-400">Open</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-yellow-600">{stats.inProgress ?? 0}</div>
              <div className="text-gray-400">In Progress</div>
            </div>
            <div className="text-center">
              <div className="text-2xl font-bold text-green-600">{stats.resolved ?? 0}</div>
              <div className="text-gray-400">Resolved</div>
            </div>
          </div>
        )}
      </div>

      {/* App selector */}
      {appOptions.length > 0 && (
        <div className="flex items-center gap-2 mb-4">
          <span className="text-sm text-gray-500">App:</span>
          <button
            onClick={() => setSelectedApp(null)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              !selectedApp ? 'bg-primary-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            All Apps
          </button>
          {appOptions.map(app => (
            <button
              key={app.id}
              onClick={() => setSelectedApp(app.id)}
              className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
                selectedApp === app.id ? 'bg-primary-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {app.name}
            </button>
          ))}
        </div>
      )}

      {/* Filter tabs */}
      <div className="flex gap-1 mb-4 bg-gray-100 rounded-lg p-1">
        {['OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'ALL'].map(s => (
          <button
            key={s}
            onClick={() => { setFilter(s); setSelectedCase(null); }}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              filter === s ? 'bg-white shadow-sm text-gray-900' : 'text-gray-500 hover:text-gray-700'
            }`}
          >
            {s.replace('_', ' ')}
          </button>
        ))}
      </div>

      <div className="flex gap-6">
        {/* Case list */}
        <div className="flex-1 space-y-2">
          {isLoading && <div className="text-gray-400 py-8 text-center">Loading cases...</div>}
          {cases?.length === 0 && !isLoading && (
            <div className="text-gray-400 py-8 text-center">No cases with status: {filter}</div>
          )}
          {cases?.map(c => (
            <div
              key={c.id}
              onClick={() => setSelectedCase(c)}
              className={`bg-white border rounded-lg p-4 cursor-pointer transition-all hover:shadow-sm ${
                selectedCase?.id === c.id ? 'border-primary-500 ring-1 ring-primary-500' : 'border-gray-200'
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs text-gray-400">#{c.caseNumber}</span>
                    <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STATUS_COLORS[c.status] ?? 'bg-gray-100'}`}>
                      {c.status}
                    </span>
                    <span className={`text-xs font-medium ${PRIORITY_COLORS[c.priority] ?? ''}`}>
                      {c.priority !== 'NORMAL' ? c.priority : ''}
                    </span>
                  </div>
                  <h3 className="font-medium text-gray-900 mt-1 truncate">{c.title}</h3>
                  <div className="text-xs text-gray-400 mt-1">
                    {new Date(c.createdAt).toLocaleString()}
                    {c.assigneeId && <span className="ml-2">Assigned</span>}
                  </div>
                </div>
                {c.sourcePostId && (
                  <Link
                    to={`/post/${c.sourcePostId}`}
                    onClick={e => e.stopPropagation()}
                    className="text-xs text-primary-500 hover:underline shrink-0 ml-2"
                  >
                    View Post
                  </Link>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* Case detail panel */}
        {selectedCase && (
          <div className="w-96 bg-white border border-gray-200 rounded-lg p-5 sticky top-24 self-start space-y-4">
            <div className="flex items-center justify-between">
              <span className="font-mono text-sm text-gray-500">#{selectedCase.caseNumber}</span>
              <button onClick={() => setSelectedCase(null)} className="text-gray-400 hover:text-gray-600 text-sm">Close</button>
            </div>

            <h2 className="font-semibold text-gray-900">{selectedCase.title}</h2>

            {selectedCase.description && (
              <p className="text-sm text-gray-600 whitespace-pre-wrap">{selectedCase.description}</p>
            )}

            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-400">Status</span>
                <select
                  value={selectedCase.status}
                  onChange={e => updateStatusMutation.mutate({ caseId: selectedCase.id, status: e.target.value })}
                  className="text-sm border rounded px-2 py-1"
                >
                  <option value="OPEN">Open</option>
                  <option value="IN_PROGRESS">In Progress</option>
                  <option value="WAITING">Waiting</option>
                  <option value="RESOLVED">Resolved</option>
                  <option value="CLOSED">Closed</option>
                </select>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400">Priority</span>
                <span className={`font-medium ${PRIORITY_COLORS[selectedCase.priority]}`}>{selectedCase.priority}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-400">Created</span>
                <span>{new Date(selectedCase.createdAt).toLocaleString()}</span>
              </div>
              {selectedCase.sourcePostId && (
                <div className="flex justify-between">
                  <span className="text-gray-400">Source</span>
                  <Link to={`/post/${selectedCase.sourcePostId}`} className="text-primary-500 hover:underline">
                    View original post
                  </Link>
                </div>
              )}
            </div>

            {/* Actions */}
            <div className="space-y-2 pt-2 border-t">
              {!selectedCase.assigneeId && (
                <button
                  onClick={() => assignMutation.mutate(selectedCase.id)}
                  disabled={assignMutation.isPending}
                  className="w-full px-4 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50"
                >
                  {assignMutation.isPending ? 'Assigning...' : 'Assign to Me'}
                </button>
              )}

              {selectedCase.status !== 'RESOLVED' && selectedCase.status !== 'CLOSED' && (
                <button
                  onClick={() => resolveMutation.mutate({ caseId: selectedCase.id, resolution: 'Resolved by support agent' })}
                  disabled={resolveMutation.isPending}
                  className="w-full px-4 py-2 bg-green-500 text-white rounded-lg text-sm font-medium hover:bg-green-600 disabled:opacity-50"
                >
                  {resolveMutation.isPending ? 'Resolving...' : 'Mark Resolved'}
                </button>
              )}
            </div>

            {/* Reply to original post */}
            {selectedCase.sourcePostId && selectedCase.status !== 'CLOSED' && (
              <div className="pt-2 border-t">
                <label className="block text-xs font-medium text-gray-500 mb-1">Reply on original post</label>
                <textarea
                  value={replyText}
                  onChange={e => setReplyText(e.target.value)}
                  placeholder="Type your response..."
                  className="w-full border rounded-lg px-3 py-2 text-sm resize-none"
                  rows={3}
                />
                <button
                  onClick={() => {
                    if (replyText.trim() && selectedCase.sourcePostId) {
                      replyMutation.mutate({ postId: selectedCase.sourcePostId, content: replyText.trim() });
                    }
                  }}
                  disabled={!replyText.trim() || replyMutation.isPending}
                  className="mt-2 px-4 py-2 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 disabled:opacity-50"
                >
                  {replyMutation.isPending ? 'Sending...' : 'Send Reply'}
                </button>
                <p className="text-xs text-gray-400 mt-1">This will post as a comment on the user's original question.</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
