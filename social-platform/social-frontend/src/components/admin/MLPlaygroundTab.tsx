import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import api from '../../api/client';

export default function MLPlaygroundTab() {
  const [activeSection, setActiveSection] = useState<'features' | 'scorer' | 'stats' | 'data' | 'model'>('features');

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 flex-wrap">
        {(['features', 'scorer', 'stats', 'data', 'model'] as const).map((s) => (
          <button
            key={s}
            onClick={() => setActiveSection(s)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${
              activeSection === s ? 'bg-purple-500 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {s === 'features' ? 'Feature Explorer' : s === 'scorer' ? 'Score Simulator' : s === 'stats' ? 'Analytics Stats' : s === 'data' ? 'Data Explorer' : 'Model Info'}
          </button>
        ))}
      </div>

      {activeSection === 'features' && <FeatureExplorer />}
      {activeSection === 'scorer' && <ScoreSimulator />}
      {activeSection === 'stats' && <AnalyticsStats />}
      {activeSection === 'data' && <DataExplorer />}
      {activeSection === 'model' && <ModelInfo />}
    </div>
  );
}

function FeatureExplorer() {
  const { data, isLoading } = useQuery<any>({
    queryKey: ['ml-features'],
    queryFn: async () => { const { data } = await api.get('/admin/ml/features?limit=30'); return data; },
  });

  if (isLoading) return <div className="text-sm text-gray-400">Loading features...</div>;

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-bold text-gray-700">Feed Ranking Features — Top 30 Posts</h3>
      <p className="text-xs text-gray-500">Shows what the ranking model sees for each post, sorted by score.</p>

      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="bg-gray-50 text-left">
              <th className="p-2 font-medium">Rank</th>
              <th className="p-2 font-medium">Score</th>
              <th className="p-2 font-medium">Author</th>
              <th className="p-2 font-medium">Content</th>
              <th className="p-2 font-medium">Engag.</th>
              <th className="p-2 font-medium">Hours</th>
              <th className="p-2 font-medium">Affinity</th>
              <th className="p-2 font-medium">Reactions</th>
              <th className="p-2 font-medium">Comments</th>
              <th className="p-2 font-medium">Followers</th>
              <th className="p-2 font-medium">Attach</th>
              <th className="p-2 font-medium">Poll</th>
              <th className="p-2 font-medium">Dist</th>
            </tr>
          </thead>
          <tbody>
            {data?.posts?.map((post: any, i: number) => (
              <tr key={post.postId} className={`border-t ${i % 2 === 0 ? 'bg-white' : 'bg-gray-50/50'}`}>
                <td className="p-2 font-mono text-gray-400">#{i + 1}</td>
                <td className="p-2 font-mono font-bold text-purple-600">{post.score}</td>
                <td className="p-2 text-gray-700">{post.authorName}</td>
                <td className="p-2 text-gray-500 max-w-[200px] truncate">{post.content}</td>
                <td className="p-2 font-mono">{post.features.engagement?.toFixed(1)}</td>
                <td className="p-2 font-mono">{post.features.recencyHours?.toFixed(1)}</td>
                <td className="p-2 font-mono">{post.features.affinity?.toFixed(2)}</td>
                <td className="p-2 font-mono">{post.features.reactionCount}</td>
                <td className="p-2 font-mono">{post.features.commentCount}</td>
                <td className="p-2 font-mono">{post.features.authorFollowers}</td>
                <td className="p-2">{post.features.hasAttachment ? '📎' : '-'}</td>
                <td className="p-2">{post.features.hasPoll ? '📊' : '-'}</td>
                <td className="p-2 font-mono">{post.features.socialDistance}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ScoreSimulator() {
  const [features, setFeatures] = useState({
    engagement: 10, recencyHours: 2, affinity: 1.3, reactionCount: 5,
    commentCount: 2, authorFollowers: 100, isRecommended: false,
    hasAttachment: false, hasPoll: false, socialDistance: 1,
  });

  const scoreMutation = useMutation({
    mutationFn: async () => {
      const { data } = await api.post('/admin/ml/score', features);
      return data;
    },
  });

  const updateFeature = (key: string, value: any) => {
    setFeatures((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-700">Score Simulator</h3>
      <p className="text-xs text-gray-500">Experiment with feature values to see how the ranking score changes.</p>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
        {[
          { key: 'engagement', label: 'Engagement', type: 'number', step: 1 },
          { key: 'recencyHours', label: 'Recency (hours)', type: 'number', step: 0.5 },
          { key: 'affinity', label: 'Affinity', type: 'number', step: 0.1 },
          { key: 'reactionCount', label: 'Reactions', type: 'number', step: 1 },
          { key: 'commentCount', label: 'Comments', type: 'number', step: 1 },
          { key: 'authorFollowers', label: 'Followers', type: 'number', step: 10 },
          { key: 'socialDistance', label: 'Social Dist (1-3)', type: 'number', step: 1 },
        ].map(({ key, label, step }) => (
          <div key={key}>
            <label className="text-[10px] text-gray-500 font-medium">{label}</label>
            <input
              type="number"
              value={(features as any)[key]}
              step={step}
              onChange={(e) => updateFeature(key, parseFloat(e.target.value) || 0)}
              className="input-field w-full text-sm"
            />
          </div>
        ))}
        {[
          { key: 'isRecommended', label: 'Recommended' },
          { key: 'hasAttachment', label: 'Has Attachment' },
          { key: 'hasPoll', label: 'Has Poll' },
        ].map(({ key, label }) => (
          <div key={key} className="flex items-center gap-2 pt-4">
            <input
              type="checkbox"
              checked={(features as any)[key]}
              onChange={(e) => updateFeature(key, e.target.checked)}
              className="w-4 h-4"
            />
            <label className="text-xs text-gray-600">{label}</label>
          </div>
        ))}
      </div>

      <button onClick={() => scoreMutation.mutate()} className="btn-primary text-sm px-4">
        {scoreMutation.isPending ? 'Scoring...' : 'Calculate Score'}
      </button>

      {scoreMutation.data && (
        <div className="bg-purple-50 border border-purple-200 rounded-lg p-4">
          <div className="text-2xl font-bold text-purple-700">
            Score: {scoreMutation.data.score?.toFixed(6)}
          </div>
          <div className="mt-2 text-xs text-gray-500">
            Feature Vector: [{scoreMutation.data.featureVector?.map((v: number) => v.toFixed(2)).join(', ')}]
          </div>
        </div>
      )}
    </div>
  );
}

function AnalyticsStats() {
  const { data, isLoading } = useQuery<any>({
    queryKey: ['ml-stats'],
    queryFn: async () => { const { data } = await api.get('/admin/ml/analytics/stats'); return data; },
  });

  if (isLoading) return <div className="text-sm text-gray-400">Loading stats...</div>;

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-700">Platform Analytics</h3>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {data && Object.entries(data).map(([key, value]) => (
          <div key={key} className="bg-white border rounded-lg p-3">
            <div className="text-[10px] text-gray-400 uppercase tracking-wider">{key.replace(/([A-Z])/g, ' $1').trim()}</div>
            <div className="text-lg font-bold text-gray-800">{typeof value === 'number' ? value.toLocaleString() : String(value)}</div>
          </div>
        ))}
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-xs text-blue-800">
        <strong>Kafka Topics:</strong>
        <ul className="mt-1 space-y-0.5 list-disc list-inside">
          <li><code>worksphere-feed-impressions</code> — feed impression events with ranking features</li>
          <li><code>worksphere-user-interactions</code> — reactions, comments, messages, searches, etc.</li>
          <li><code>posts.created</code> — post creation events for feed fan-out</li>
          <li><code>messages.sent</code> — message events</li>
        </ul>
      </div>
    </div>
  );
}

function ModelInfo() {
  const { data, isLoading } = useQuery<any>({
    queryKey: ['ml-model-info'],
    queryFn: async () => { const { data } = await api.get('/admin/ml/model/info'); return data; },
  });

  if (isLoading) return <div className="text-sm text-gray-400">Loading...</div>;

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-700">Feed Ranking Model</h3>

      <div className="bg-white border rounded-lg p-4 space-y-3 text-sm">
        <div><span className="text-gray-500">Type:</span> <span className="font-mono">{data?.modelType}</span></div>
        <div><span className="text-gray-500">Features:</span> <span className="font-mono">{data?.featureCount}</span></div>
        <div><span className="text-gray-500">Formula:</span> <code className="bg-gray-100 px-2 py-0.5 rounded">{data?.scoringFormula}</code></div>
        <div><span className="text-gray-500">Status:</span> <span className="text-orange-600">{data?.status}</span></div>

        <div className="pt-2 border-t">
          <div className="text-xs font-semibold text-gray-500 mb-1">User Features (per-request)</div>
          <div className="flex gap-1 flex-wrap">
            {data?.userFeatures?.map((f: string) => (
              <span key={f} className="bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded">{f}</span>
            ))}
          </div>
        </div>

        <div>
          <div className="text-xs font-semibold text-gray-500 mb-1">Item Features (pre-loaded)</div>
          <div className="flex gap-1 flex-wrap">
            {data?.itemFeatures?.map((f: string) => (
              <span key={f} className="bg-green-100 text-green-700 text-xs px-2 py-0.5 rounded">{f}</span>
            ))}
          </div>
        </div>

        <div className="bg-gray-50 rounded-lg p-3 text-xs">
          <div className="font-semibold mb-1">Pipeline Status</div>
          <ol className="list-decimal list-inside space-y-0.5 text-gray-600">
            <li className="text-green-600 font-medium">✓ Feature extraction (FeedFeatureExtractor)</li>
            <li className="text-green-600 font-medium">✓ Structured logging to Kafka</li>
            <li className="text-green-600 font-medium">✓ Heuristic scoring deployed</li>
            <li className="text-green-600 font-medium">✓ XGBoost model trained on synthetic data</li>
            <li className="text-green-600 font-medium">✓ GBDT kernel generated (C/CPU)</li>
            <li className="text-yellow-600">⏳ Collecting real impression/interaction data</li>
            <li className="text-gray-400">○ Train model on real data</li>
            <li className="text-gray-400">○ Deploy GBDT ranker (gRPC)</li>
            <li className="text-gray-400">○ A/B test heuristic vs GBDT</li>
          </ol>
        </div>
      </div>
    </div>
  );
}

function DataExplorer() {
  const [selectedTopic, setSelectedTopic] = useState('worksphere-feed-impressions');
  const [limit, setLimit] = useState(20);

  const { data: topics } = useQuery<any[]>({
    queryKey: ['analytics-topics'],
    queryFn: async () => { const { data } = await api.get('/admin/analytics/topics'); return data; },
  });

  const { data: events, isLoading, refetch } = useQuery<any>({
    queryKey: ['analytics-events', selectedTopic, limit],
    queryFn: async () => { const { data } = await api.get(`/admin/analytics/events/${selectedTopic}?limit=${limit}`); return data; },
  });

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-bold text-gray-700">Kafka Data Explorer</h3>
      <p className="text-xs text-gray-500">Browse raw analytics events from Kafka topics.</p>
      <div className="flex items-center gap-3 flex-wrap">
        {topics?.map((t: any) => (
          <button key={t.topic} onClick={() => setSelectedTopic(t.topic)}
            className={`text-xs px-3 py-1.5 rounded-lg border transition-colors ${selectedTopic === t.topic ? 'bg-blue-500 text-white border-blue-500' : 'bg-white text-gray-700 border-gray-200 hover:border-blue-300'}`}>
            {t.topic.replace('worksphere-', '')} ({t.messageCount})
          </button>
        ))}
        <select value={limit} onChange={(e) => setLimit(Number(e.target.value))} className="text-xs border rounded px-2 py-1">
          <option value={10}>10</option><option value={20}>20</option><option value={50}>50</option>
        </select>
        <button onClick={() => refetch()} className="text-xs text-blue-600 hover:underline">Refresh</button>
      </div>
      {isLoading ? <div className="text-sm text-gray-400">Loading...</div> : (
        <div className="space-y-1 max-h-[500px] overflow-y-auto">
          <div className="text-xs text-gray-500 mb-2">{events?.count ?? 0} events</div>
          {events?.events?.map((evt: string, i: number) => {
            let p: any; try { p = JSON.parse(evt); } catch { p = null; }
            const d = p?.data || p;
            return (
              <details key={i}>
                <summary className="cursor-pointer text-xs bg-gray-50 hover:bg-gray-100 rounded px-3 py-2 flex items-center gap-2">
                  <span className="text-gray-400 font-mono">#{i+1}</span>
                  <span className="font-medium text-gray-700">{d?.interaction_type || d?.source || p?._log_type || 'event'}</span>
                  <span className="text-gray-500">user:{d?.user_id} {d?.timestamp?.substring?.(11,19) ?? ''}</span>
                  {d?.score != null && <span className="text-purple-600 font-mono ml-auto">score:{typeof d.score === 'number' ? d.score.toFixed(2) : d.score}</span>}
                </summary>
                <pre className="text-[10px] bg-gray-900 text-green-400 p-3 rounded-b overflow-x-auto">{JSON.stringify(d||p, null, 2)}</pre>
              </details>
            );
          })}
        </div>
      )}
      <div className="bg-gray-50 border rounded-lg p-3 text-xs text-gray-600">
        <strong>Trino:</strong> Running at <code>localhost:8081</code>. Connect: <code>trino --server localhost:8081 --catalog iceberg --schema analytics</code>
      </div>
    </div>
  );
}
