import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from './api/client';
import AskExplore from './components/AskExplore';

type Tab = 'sets' | 'ask' | 'solutions' | 'metrics';

export default function App() {
  const [tab, setTab] = useState<Tab>('sets');
  const { data: health } = useQuery({ queryKey: ['health'], queryFn: () => api.get('/health').then(r => r.data), refetchInterval: 10000 });

  const tabs: { key: Tab; label: string }[] = [
    { key: 'sets', label: 'Knowledge Sets' },
    { key: 'ask', label: 'Ask & Explore' },
    { key: 'solutions', label: 'Solutions Queue' },
    { key: 'metrics', label: 'Metrics' },
  ];

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-indigo-900 text-white px-6 py-4 flex items-center justify-between">
        <h1 className="text-xl font-bold">AI Support Admin</h1>
        <div className="flex items-center gap-4 text-sm">
          <span className={`px-2 py-1 rounded ${health?.ollamaAvailable ? 'bg-green-600' : 'bg-red-600'}`}>
            Ollama: {health?.ollamaAvailable ? 'Connected' : 'Offline'}
          </span>
          <span className="text-indigo-300">{health?.knowledgeSets ?? 0} knowledge sets</span>
        </div>
      </header>
      <nav className="bg-white border-b px-6 flex gap-1">
        {tabs.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-3 text-sm font-medium -mb-px transition-colors ${
              tab === t.key ? 'border-b-2 border-indigo-500 text-indigo-600' : 'text-gray-500 hover:text-gray-700'
            }`}>
            {t.label}
          </button>
        ))}
      </nav>
      <main className="max-w-6xl mx-auto px-6 py-6">
        {tab === 'sets' && <KnowledgeSetsTab />}
        {tab === 'ask' && <AskExplore />}
        {tab === 'solutions' && <SolutionsTab />}
        {tab === 'metrics' && <MetricsTab />}
      </main>
    </div>
  );
}

function KnowledgeSetsTab() {
  const qc = useQueryClient();
  const { data: sets } = useQuery<any[]>({ queryKey: ['sets'], queryFn: () => api.get('/knowledge/sets').then(r => r.data) });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [newName, setNewName] = useState('');
  const [newSlug, setNewSlug] = useState('');

  const createSet = useMutation({
    mutationFn: () => api.post('/knowledge/sets', { name: newName, slug: newSlug, description: '' }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['sets'] }); setNewName(''); setNewSlug(''); },
  });

  const selected = sets?.find((s: any) => s.id === selectedId);

  return (
    <div className="grid grid-cols-3 gap-6">
      <div className="col-span-1 space-y-3">
        <h2 className="text-lg font-semibold">Knowledge Sets</h2>
        <div className="flex gap-2">
          <input value={newName} onChange={e => { setNewName(e.target.value); setNewSlug(e.target.value.toLowerCase().replace(/\s+/g,'-')); }}
            placeholder="Name" className="flex-1 border rounded px-2 py-1 text-sm" />
          <button onClick={() => createSet.mutate()} disabled={!newName || createSet.isPending}
            className="bg-indigo-500 text-white px-3 py-1 rounded text-sm hover:bg-indigo-600 disabled:opacity-50">Add</button>
        </div>
        {sets?.map((s: any) => (
          <button key={s.id} onClick={() => setSelectedId(s.id)}
            className={`w-full text-left p-3 rounded-lg border transition-colors ${
              selectedId === s.id ? 'border-indigo-500 bg-indigo-50' : 'border-gray-200 hover:border-gray-300'
            }`}>
            <div className="font-medium text-sm">{s.name}</div>
            <div className="text-xs text-gray-400">{s.slug}</div>
          </button>
        ))}
      </div>
      <div className="col-span-2">
        {selectedId ? <KnowledgeSetDetail id={selectedId} /> : (
          <div className="text-center text-gray-400 py-20">Select a knowledge set</div>
        )}
      </div>
    </div>
  );
}

function KnowledgeSetDetail({ id }: { id: number }) {
  const qc = useQueryClient();
  const { data: ks } = useQuery({ queryKey: ['ks', id], queryFn: () => api.get(`/knowledge/sets/${id}`).then(r => r.data) });
  const { data: docs } = useQuery<any[]>({ queryKey: ['docs', id], queryFn: () => api.get(`/knowledge/sets/${id}/documents`).then(r => r.data) });
  const [crawlUrl, setCrawlUrl] = useState('');
  const [docTitle, setDocTitle] = useState('');
  const [docContent, setDocContent] = useState('');

  const crawl = useMutation({
    mutationFn: () => api.post(`/knowledge/sets/${id}/crawl`, { url: crawlUrl, maxDepth: 2, maxPages: 30 }),
    onSuccess: () => { setCrawlUrl(''); qc.invalidateQueries({ queryKey: ['docs', id] }); },
  });

  const addDoc = useMutation({
    mutationFn: () => api.post(`/knowledge/sets/${id}/documents`, { title: docTitle, content: docContent, sourceType: 'MANUAL' }),
    onSuccess: () => { setDocTitle(''); setDocContent(''); qc.invalidateQueries({ queryKey: ['docs', id] }); qc.invalidateQueries({ queryKey: ['ks', id] }); },
  });

  const deleteDoc = useMutation({
    mutationFn: (docId: number) => api.delete(`/knowledge/documents/${docId}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['docs', id] }); qc.invalidateQueries({ queryKey: ['ks', id] }); },
  });

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">{ks?.name}</h2>
        <div className="flex gap-4 text-sm text-gray-500 mt-1">
          <span>{ks?.documentCount ?? 0} docs</span>
          <span>{ks?.chunkCount ?? 0} chunks</span>
          <span>{ks?.totalTokens ?? 0} tokens</span>
          <span>{ks?.vectorCount ?? 0} vectors</span>
        </div>
      </div>

      {/* Crawl */}
      <div className="bg-white rounded-lg border p-4 space-y-2">
        <h3 className="text-sm font-semibold">Crawl Website</h3>
        <div className="flex gap-2">
          <input value={crawlUrl} onChange={e => setCrawlUrl(e.target.value)}
            placeholder="https://example.com/docs" className="flex-1 border rounded px-3 py-2 text-sm" />
          <button onClick={() => crawl.mutate()} disabled={!crawlUrl || crawl.isPending}
            className="bg-indigo-500 text-white px-4 py-2 rounded text-sm hover:bg-indigo-600 disabled:opacity-50">
            {crawl.isPending ? 'Crawling...' : 'Crawl'}
          </button>
        </div>
      </div>

      {/* Add Document */}
      <div className="bg-white rounded-lg border p-4 space-y-2">
        <h3 className="text-sm font-semibold">Add Document</h3>
        <input value={docTitle} onChange={e => setDocTitle(e.target.value)}
          placeholder="Document title" className="w-full border rounded px-3 py-2 text-sm" />
        <textarea value={docContent} onChange={e => setDocContent(e.target.value)}
          placeholder="Document content..." className="w-full border rounded px-3 py-2 text-sm h-32 resize-y" />
        <button onClick={() => addDoc.mutate()} disabled={!docTitle || !docContent || addDoc.isPending}
          className="bg-indigo-500 text-white px-4 py-2 rounded text-sm hover:bg-indigo-600 disabled:opacity-50">
          {addDoc.isPending ? 'Adding...' : 'Add & Index'}
        </button>
      </div>

      {/* Documents */}
      <div className="bg-white rounded-lg border">
        <h3 className="text-sm font-semibold px-4 py-3 border-b">Documents ({docs?.length ?? 0})</h3>
        <div className="divide-y max-h-96 overflow-y-auto">
          {docs?.map((d: any) => (
            <div key={d.id} className="px-4 py-3 flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium truncate">{d.title}</div>
                <div className="text-xs text-gray-400">{d.sourceType} · {Math.round(d.contentLength / 1024)}KB · {d.indexed ? 'indexed' : 'not indexed'}</div>
              </div>
              <button onClick={() => deleteDoc.mutate(d.id)} className="text-xs text-red-500 hover:underline">Delete</button>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function AskTab() {
  const [ksId, setKsId] = useState('1');
  const [question, setQuestion] = useState('');
  const [mode, setMode] = useState<'simple' | 'agentic'>('agentic');
  const [history, setHistory] = useState<any[]>([]);
  const [selectedTraceId, setSelectedTraceId] = useState<number | null>(null);
  const { data: sets } = useQuery<any[]>({ queryKey: ['sets'], queryFn: () => api.get('/knowledge/sets').then(r => r.data) });

  const ask = useMutation({
    mutationFn: () => {
      const endpoint = mode === 'agentic' ? '/qa/ask-agentic' : '/qa/ask';
      return api.post(endpoint, { knowledgeSetId: ksId, question }).then(r => r.data);
    },
    onSuccess: (data) => {
      setHistory(prev => [{ question, ksId, mode, ...data, timestamp: new Date().toISOString() }, ...prev]);
      setQuestion('');
    },
  });

  const route = useMutation({
    mutationFn: () => api.post('/qa/route', { question }).then(r => r.data),
  });

  // Recent traces from server
  const { data: recentTraces } = useQuery<any[]>({
    queryKey: ['traces', ksId],
    queryFn: () => api.get(`/qa/traces?knowledgeSetId=${ksId}&limit=20`).then(r => r.data),
    refetchInterval: 5000,
  });

  // Selected trace detail
  const { data: traceDetail } = useQuery({
    queryKey: ['trace', selectedTraceId],
    queryFn: () => api.get(`/qa/traces/${selectedTraceId}`).then(r => r.data),
    enabled: selectedTraceId !== null,
  });

  const parseJson = (val: any) => {
    if (!val) return [];
    if (typeof val === 'string') try { return JSON.parse(val); } catch { return []; }
    return val;
  };

  return (
    <div className="grid grid-cols-5 gap-6">
      {/* Left: Chat + Input */}
      <div className="col-span-3 space-y-4">
        <div className="bg-white rounded-lg border p-4 space-y-3">
          <div className="flex gap-3 flex-wrap">
            <select value={ksId} onChange={e => setKsId(e.target.value)} className="border rounded px-3 py-2 text-sm">
              {sets?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <div className="flex border rounded overflow-hidden">
              <button onClick={() => setMode('agentic')}
                className={`px-3 py-2 text-xs font-medium transition-colors ${mode === 'agentic' ? 'bg-purple-500 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}>
                Agentic
              </button>
              <button onClick={() => setMode('simple')}
                className={`px-3 py-2 text-xs font-medium transition-colors ${mode === 'simple' ? 'bg-indigo-500 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}>
                Simple RAG
              </button>
            </div>
            <input value={question} onChange={e => setQuestion(e.target.value)}
              placeholder="Ask a question..." className="flex-1 min-w-[200px] border rounded px-3 py-2 text-sm"
              onKeyDown={e => e.key === 'Enter' && !ask.isPending && question && ask.mutate()} />
            <button onClick={() => ask.mutate()} disabled={!question || ask.isPending}
              className="bg-indigo-500 text-white px-4 py-2 rounded text-sm hover:bg-indigo-600 disabled:opacity-50 shrink-0">
              {ask.isPending ? 'Thinking...' : 'Ask'}
            </button>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-xs text-gray-400">Try:</span>
            {(EXAMPLE_QUESTIONS[ksId] ?? []).slice(0, 4).map((q: string, i: number) => (
              <button key={i} onClick={() => setQuestion(q)}
                className="text-[11px] text-gray-500 bg-gray-50 border border-gray-200 px-2 py-1 rounded-full hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors truncate max-w-[220px]">
                {q}
              </button>
            ))}
          </div>
          <div className="flex gap-2 text-xs">
            <button onClick={() => route.mutate()} disabled={!question || route.isPending}
              className="text-indigo-500 hover:underline">Route to best knowledge set</button>
            <span className="text-gray-200">|</span>
            <span className="text-gray-400">{mode === 'agentic' ? 'LLM will search & reason iteratively' : 'Single-shot RAG retrieval'}</span>
          </div>
        </div>

        {route.data && (
          <div className="bg-white rounded-lg border p-3 text-sm">
            <div className="text-xs font-semibold text-gray-400 mb-1">Routing</div>
            {(route.data as any[]).map((r: any, i: number) => (
              <span key={i} className="inline-block mr-3">
                <span className="font-medium">{r.name}</span> <span className="text-gray-400">({r.score?.toFixed(2)})</span>
              </span>
            ))}
          </div>
        )}

        {/* Chat history */}
        <div className="space-y-3 max-h-[600px] overflow-y-auto">
          {history.map((item, i) => (
            <div key={i} className="bg-white rounded-lg border overflow-hidden">
              {/* Question */}
              <div className="px-4 py-3 bg-indigo-50 border-b flex items-center gap-2">
                <span className="text-sm font-medium text-indigo-900">{item.question}</span>
                <span className="text-[10px] text-indigo-400 ml-auto">KS {item.ksId}</span>
              </div>

              {/* Answer */}
              <div className="px-4 py-3">
                <div className="flex gap-2 mb-2 flex-wrap">
                  <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${
                    item.confidence >= 0.7 ? 'bg-green-100 text-green-700' :
                    item.confidence >= 0.5 ? 'bg-yellow-100 text-yellow-700' : 'bg-red-100 text-red-700'
                  }`}>
                    {(item.confidence * 100).toFixed(0)}% confidence
                  </span>
                  <span className={`px-2 py-0.5 rounded text-[10px] ${item.mode === 'agentic' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100'}`}>
                    {item.method ?? item.mode}
                  </span>
                  {item.suggestHuman && <span className="px-2 py-0.5 bg-orange-100 text-orange-700 rounded text-[10px]">Needs human</span>}
                  {item.traceId && (
                    <button onClick={() => setSelectedTraceId(item.traceId)}
                      className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded text-[10px] hover:bg-purple-200">
                      Trace #{item.traceId}
                    </button>
                  )}
                </div>

                {/* Agentic steps */}
                {item.steps && item.steps.length > 0 && (
                  <div className="mb-3 space-y-1.5">
                    {item.steps.filter((s: any) => s.tool !== 'none').map((step: any, si: number) => (
                      <div key={si} className="bg-gray-50 rounded-lg px-3 py-2 text-xs">
                        <div className="flex items-center gap-2">
                          <span className="text-purple-600 font-mono font-medium">{step.tool}({step.args?.substring(0, 40)}{step.args?.length > 40 ? '...' : ''})</span>
                          <span className="text-gray-300">{step.durationMs}ms</span>
                        </div>
                        {step.thought && <div className="text-gray-500 mt-0.5 italic">{step.thought.substring(0, 120)}</div>}
                        {step.resultPreview && (
                          <details className="mt-1">
                            <summary className="text-gray-400 cursor-pointer hover:text-gray-600">Show retrieved content</summary>
                            <pre className="mt-1 text-[10px] text-gray-500 whitespace-pre-wrap max-h-32 overflow-y-auto bg-white rounded p-2 border">{step.resultPreview}</pre>
                          </details>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">{item.answer}</div>
                {item.citations?.length > 0 && (
                  <div className="mt-2 pt-2 border-t text-xs text-gray-400">
                    Sources: {item.citations.map((c: any) => c.title || `doc-${c.documentId}`).join(', ')}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Right: Trace Panel */}
      <div className="col-span-2 space-y-4">
        {/* Trace detail */}
        {traceDetail ? (
          <div className="bg-white rounded-lg border overflow-hidden">
            <div className="px-4 py-3 bg-purple-50 border-b flex items-center justify-between">
              <span className="text-sm font-semibold text-purple-900">Trace #{traceDetail.id}</span>
              <button onClick={() => setSelectedTraceId(null)} className="text-xs text-gray-400 hover:text-gray-600">Close</button>
            </div>
            <div className="px-4 py-3 space-y-4 max-h-[700px] overflow-y-auto text-xs">
              {/* Overview */}
              <div>
                <div className="font-semibold text-gray-700 mb-1">Overview</div>
                <div className="grid grid-cols-2 gap-1 text-gray-500">
                  <span>Method: <strong>{traceDetail.method}</strong></span>
                  <span>Confidence: <strong>{traceDetail.confidence}</strong></span>
                  <span>LLM: <strong>{traceDetail.llm_model}</strong></span>
                  <span>Duration: <strong>{traceDetail.llm_duration_ms}ms</strong></span>
                  <span>Total tokens: <strong>{traceDetail.total_knowledge_tokens}</strong></span>
                  <span>Context tokens: <strong>{traceDetail.context_token_count}</strong></span>
                </div>
              </div>

              {/* Search Results */}
              <div>
                <div className="font-semibold text-gray-700 mb-1">Lexical Search ({parseJson(traceDetail.lexical_results).length} hits)</div>
                <div className="space-y-1">
                  {parseJson(traceDetail.lexical_results).map((r: any, i: number) => (
                    <div key={i} className="bg-gray-50 rounded px-2 py-1">
                      <span className="text-gray-400">chunk-{r.chunkId}</span>
                      <span className="ml-2 font-mono text-gray-500">score={Number(r.score).toFixed(3)}</span>
                      {r.title && <span className="ml-2 text-gray-600">{r.title}</span>}
                    </div>
                  ))}
                </div>
              </div>

              <div>
                <div className="font-semibold text-gray-700 mb-1">Semantic Search ({parseJson(traceDetail.semantic_results).length} hits)</div>
                <div className="space-y-1">
                  {parseJson(traceDetail.semantic_results).map((r: any, i: number) => (
                    <div key={i} className="bg-blue-50 rounded px-2 py-1">
                      <span className="text-gray-400">chunk-{r.chunkId}</span>
                      <span className="ml-2 font-mono text-blue-600">cosine={Number(r.score).toFixed(3)}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Context Chunks */}
              <div>
                <div className="font-semibold text-gray-700 mb-1">Context Chunks Used ({parseJson(traceDetail.context_chunks).length})</div>
                <div className="space-y-1">
                  {parseJson(traceDetail.context_chunks).map((c: any, i: number) => (
                    <div key={i} className="bg-green-50 rounded px-2 py-1">
                      <div className="flex items-center gap-2">
                        <span className="text-green-700 font-medium">chunk-{c.chunkId}</span>
                        <span className="text-gray-400">doc-{c.documentId}</span>
                        <span className="text-gray-400">{c.tokens} tokens</span>
                      </div>
                      <div className="text-gray-500 mt-0.5 line-clamp-2">{c.content?.substring(0, 150)}...</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Prompts */}
              <div>
                <div className="font-semibold text-gray-700 mb-1">System Prompt</div>
                <pre className="bg-gray-50 rounded p-2 whitespace-pre-wrap text-gray-500 max-h-32 overflow-y-auto">{traceDetail.system_prompt}</pre>
              </div>

              <div>
                <div className="font-semibold text-gray-700 mb-1">User Prompt (context + question)</div>
                <pre className="bg-gray-50 rounded p-2 whitespace-pre-wrap text-gray-500 max-h-48 overflow-y-auto">{traceDetail.user_prompt}</pre>
              </div>

              {/* Citations */}
              <div>
                <div className="font-semibold text-gray-700 mb-1">Citations ({parseJson(traceDetail.citations).length})</div>
                {parseJson(traceDetail.citations).map((c: any, i: number) => (
                  <div key={i} className="text-gray-500">
                    [{i+1}] doc-{c.documentId}: {c.title}
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-lg border p-4 text-center text-gray-400 text-sm">
            Click a "Trace #" badge to inspect the QA pipeline steps
          </div>
        )}

        {/* Recent traces */}
        <div className="bg-white rounded-lg border">
          <div className="px-4 py-3 border-b font-semibold text-sm text-gray-700">Recent Traces</div>
          <div className="divide-y max-h-64 overflow-y-auto">
            {recentTraces?.map((t: any) => (
              <button key={t.id} onClick={() => setSelectedTraceId(t.id)}
                className={`w-full text-left px-4 py-2 text-xs hover:bg-gray-50 ${selectedTraceId === t.id ? 'bg-purple-50' : ''}`}>
                <div className="flex items-center gap-2">
                  <span className="text-purple-600 font-medium">#{t.id}</span>
                  <span className={`px-1.5 py-0.5 rounded text-[9px] ${
                    t.confidence >= 0.7 ? 'bg-green-100 text-green-700' : t.confidence >= 0.5 ? 'bg-yellow-100' : 'bg-red-100 text-red-700'
                  }`}>{(Number(t.confidence)*100).toFixed(0)}%</span>
                  <span className="text-gray-400">{t.method}</span>
                  {t.llm_duration_ms && <span className="text-gray-300">{t.llm_duration_ms}ms</span>}
                </div>
                <div className="text-gray-500 truncate mt-0.5">{t.question}</div>
              </button>
            ))}
            {(!recentTraces || recentTraces.length === 0) && (
              <div className="px-4 py-6 text-center text-gray-400 text-xs">No traces yet — ask a question</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ────────────────────────── Search Test Tab ────────────────────────── */

const EXAMPLE_QUESTIONS: Record<string, string[]> = {
  '1': [
    'How do I fix bad capacitors on my Amiga 500?',
    'What is WinUAE and how do I set it up?',
    'How do I upgrade the memory on my Amiga?',
    'What is Deluxe Paint?',
    'How do I set up networking on my Amiga?',
    'What are the differences between OCS, ECS, and AGA?',
    'How do I install AmigaOS 3.2?',
    'What is the PiStorm accelerator?',
  ],
  '2': [
    'How do I program the Tang Nano 9K?',
    'What is a CST pin constraint file?',
    'How do I use PLLs on Gowin FPGAs?',
    'What Gowin FPGA families are available?',
    'How do I fix timing closure errors?',
    'How do I use block RAM on Gowin?',
  ],
  '3': [
    'How do I connect my Atari 800XL to a modern TV?',
    'What is FujiNet and how do I use it?',
    'How do I use SIO2SD?',
    'What Atari 8-bit models exist?',
    'How do I write graphics programs in Atari BASIC?',
    'How do I replace the power supply on my Atari?',
  ],
  '4': [
    'What retro computers are popular for hobbyists?',
    'What is an FPGA and why would I use one?',
    'How do I get started with retro computing?',
  ],
};

function SearchTestTab() {
  const [ksId, setKsId] = useState('1');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchMode, setSearchMode] = useState<'lexical' | 'semantic' | 'hybrid'>('hybrid');
  const { data: sets } = useQuery<any[]>({ queryKey: ['sets'], queryFn: () => api.get('/knowledge/sets').then(r => r.data) });

  const lexicalSearch = useMutation({
    mutationFn: (q: string) => api.get(`/search/lexical/${ksId}`, { params: { q, topK: 10 } }).then(r => r.data),
  });
  const semanticSearch = useMutation({
    mutationFn: (q: string) => api.get(`/search/semantic/${ksId}`, { params: { q, topK: 10 } }).then(r => r.data),
  });
  const hybridSearch = useMutation({
    mutationFn: (q: string) => api.get(`/search/hybrid/${ksId}`, { params: { q, topK: 10 } }).then(r => r.data),
  });

  const doSearch = () => {
    if (!searchQuery) return;
    if (searchMode === 'lexical') lexicalSearch.mutate(searchQuery);
    else if (searchMode === 'semantic') semanticSearch.mutate(searchQuery);
    else hybridSearch.mutate(searchQuery);
  };

  const examples = EXAMPLE_QUESTIONS[ksId] ?? [];
  const isPending = lexicalSearch.isPending || semanticSearch.isPending || hybridSearch.isPending;

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-lg border p-5 space-y-4">
        <h2 className="text-lg font-semibold">Search Test</h2>
        <p className="text-sm text-gray-500">Test lexical (Lucene), semantic (vector cosine), or hybrid search directly against your knowledge base. See exactly what chunks are retrieved.</p>
        <div className="flex gap-3 flex-wrap">
          <select value={ksId} onChange={e => setKsId(e.target.value)} className="border rounded px-3 py-2 text-sm">
            {sets?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <div className="flex border rounded overflow-hidden">
            {(['lexical', 'semantic', 'hybrid'] as const).map(m => (
              <button key={m} onClick={() => setSearchMode(m)}
                className={`px-3 py-2 text-xs font-medium transition-colors ${searchMode === m ? 'bg-indigo-500 text-white' : 'bg-white text-gray-600 hover:bg-gray-50'}`}>
                {m.charAt(0).toUpperCase() + m.slice(1)}
              </button>
            ))}
          </div>
          <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
            placeholder="Search query..." className="flex-1 min-w-[200px] border rounded px-3 py-2 text-sm"
            onKeyDown={e => e.key === 'Enter' && doSearch()} />
          <button onClick={doSearch} disabled={!searchQuery || isPending}
            className="bg-indigo-500 text-white px-4 py-2 rounded text-sm hover:bg-indigo-600 disabled:opacity-50">
            {isPending ? 'Searching...' : 'Search'}
          </button>
        </div>

        {/* Example questions */}
        {examples.length > 0 && (
          <div>
            <div className="text-xs font-semibold text-gray-400 uppercase mb-2">Example Questions</div>
            <div className="flex flex-wrap gap-2">
              {examples.map((q, i) => (
                <button key={i} onClick={() => { setSearchQuery(q); }}
                  className="px-3 py-1.5 bg-gray-50 border border-gray-200 rounded-full text-xs text-gray-600 hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors">
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Hybrid results */}
      {hybridSearch.data && searchMode === 'hybrid' && (
        <div className="grid grid-cols-2 gap-4">
          <SearchResultPanel
            title="Lexical Results (Lucene)"
            results={hybridSearch.data.lexical ?? []}
            color="amber"
            showContent
          />
          <SearchResultPanel
            title="Semantic Results (Vector Cosine)"
            results={hybridSearch.data.semantic ?? []}
            color="blue"
            showContent={false}
          />
        </div>
      )}

      {/* Lexical only */}
      {lexicalSearch.data && searchMode === 'lexical' && (
        <SearchResultPanel title="Lexical Results (Lucene)" results={lexicalSearch.data} color="amber" showContent />
      )}

      {/* Semantic only */}
      {semanticSearch.data && searchMode === 'semantic' && (
        <SearchResultPanel title="Semantic Results (Vector Cosine)" results={semanticSearch.data} color="blue" showContent={false} />
      )}
    </div>
  );
}

function SearchResultPanel({ title, results, color, showContent }: {
  title: string; results: any[]; color: 'amber' | 'blue'; showContent: boolean;
}) {
  const bg = color === 'amber' ? 'bg-amber-50' : 'bg-blue-50';
  const border = color === 'amber' ? 'border-amber-200' : 'border-blue-200';
  const badge = color === 'amber' ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700';

  return (
    <div className={`rounded-lg border ${border} overflow-hidden`}>
      <div className={`px-4 py-3 ${bg} border-b ${border} flex items-center justify-between`}>
        <span className="text-sm font-semibold">{title}</span>
        <span className={`px-2 py-0.5 rounded text-xs font-medium ${badge}`}>{results.length} hits</span>
      </div>
      <div className="divide-y max-h-[500px] overflow-y-auto">
        {results.map((r: any, i: number) => (
          <div key={i} className="px-4 py-3">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-xs font-mono text-gray-400">chunk-{r.chunkId}</span>
              <span className="text-xs font-mono text-gray-400">doc-{r.documentId}</span>
              <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${badge}`}>
                score: {Number(r.score).toFixed(4)}
              </span>
            </div>
            {r.title && <div className="text-xs font-medium text-gray-700 mb-1">{r.title}</div>}
            {showContent && r.content && (
              <div className="text-xs text-gray-500 leading-relaxed line-clamp-4">{r.content}</div>
            )}
          </div>
        ))}
        {results.length === 0 && (
          <div className="px-4 py-8 text-center text-gray-400 text-sm">No results</div>
        )}
      </div>
    </div>
  );
}

function SolutionsTab() {
  const qc = useQueryClient();
  const [status, setStatus] = useState('PENDING');
  const { data: solutions } = useQuery<any[]>({
    queryKey: ['solutions', status],
    queryFn: () => api.get(`/solutions?status=${status}`).then(r => r.data),
  });
  const { data: stats } = useQuery({ queryKey: ['solution-stats'], queryFn: () => api.get('/solutions/stats').then(r => r.data) });

  const promote = useMutation({
    mutationFn: (id: number) => api.post(`/solutions/${id}/promote`, { tier: 'FIRST_CLASS' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['solutions'] }),
  });
  const dismiss = useMutation({
    mutationFn: (id: number) => api.post(`/solutions/${id}/dismiss`, { notes: '' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['solutions'] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex gap-4 items-center">
        <h2 className="text-lg font-semibold">Community Solutions</h2>
        <div className="flex gap-2 text-xs">
          {['PENDING', 'AUTO_PROMOTED', 'PROMOTED', 'DISMISSED'].map(s => (
            <button key={s} onClick={() => setStatus(s)}
              className={`px-3 py-1 rounded-full ${status === s ? 'bg-indigo-500 text-white' : 'bg-gray-100 text-gray-600'}`}>
              {s} ({(stats as any)?.[s.toLowerCase()] ?? 0})
            </button>
          ))}
        </div>
      </div>
      <div className="space-y-3">
        {solutions?.map((s: any) => (
          <div key={s.id} className="bg-white rounded-lg border p-4">
            <div className="flex items-start gap-3">
              <div className="flex-1">
                <div className="text-xs text-gray-400 mb-1">From: {s.sourceUsername} ({s.sourceType})</div>
                <div className="text-sm font-medium mb-1">{s.question}</div>
                <div className="text-sm text-gray-700 whitespace-pre-wrap">{s.solution}</div>
              </div>
              {s.status === 'PENDING' && (
                <div className="flex gap-2 shrink-0">
                  <button onClick={() => promote.mutate(s.id)}
                    className="bg-green-500 text-white px-3 py-1 rounded text-xs hover:bg-green-600">Promote</button>
                  <button onClick={() => dismiss.mutate(s.id)}
                    className="bg-gray-200 text-gray-700 px-3 py-1 rounded text-xs hover:bg-gray-300">Dismiss</button>
                </div>
              )}
            </div>
          </div>
        ))}
        {solutions?.length === 0 && <div className="text-center text-gray-400 py-10 text-sm">No {status.toLowerCase()} solutions</div>}
      </div>
    </div>
  );
}

function MetricsTab() {
  const { data: dashboard } = useQuery<any>({
    queryKey: ['metrics-dashboard'],
    queryFn: () => api.get('/metrics/dashboard').then(r => r.data),
    refetchInterval: 10000,
  });
  const { data: methods } = useQuery<any[]>({
    queryKey: ['metrics-methods'],
    queryFn: () => api.get('/metrics/methods').then(r => r.data),
  });
  const { data: alerts } = useQuery<any[]>({
    queryKey: ['metrics-alerts'],
    queryFn: () => api.get('/metrics/alerts').then(r => r.data),
    refetchInterval: 15000,
  });
  const { data: gaps } = useQuery<any[]>({
    queryKey: ['metrics-gaps'],
    queryFn: () => api.get('/metrics/gaps').then(r => r.data),
  });
  const qc = useQueryClient();
  const ackAlert = useMutation({
    mutationFn: (id: number) => api.post(`/metrics/alerts/${id}/acknowledge`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['metrics-alerts'] }),
  });

  if (!dashboard) return <div className="text-center py-10 text-gray-400">Loading metrics...</div>;

  return (
    <div className="space-y-6">
      {/* Key Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <StatBox label="Questions" value={dashboard.totalInteractions} sub={`${dashboard.totalInteractions24h ?? 0} today`} />
        <StatBox label="Avg Confidence" value={`${Math.round((dashboard.avgConfidence ?? 0) * 100)}%`} />
        <StatBox label="Escalated" value={dashboard.totalEscalated} sub={`${dashboard.escalationRate7d ?? 0}% rate (7d)`} />
        <StatBox label="Avg Response" value={`${Math.round(dashboard.avgResponseMs ?? 0)}ms`} sub={`p95: ${Math.round(dashboard.p95ResponseMs ?? 0)}ms`} />
        <StatBox label="Cache" value={dashboard.cacheEntries} sub={`${dashboard.cacheHits ?? 0} hits`} />
        <StatBox label="Gaps" value={dashboard.openKnowledgeGaps} sub="need docs" color={dashboard.openKnowledgeGaps > 0 ? 'orange' : undefined} />
      </div>

      {/* Alerts */}
      {alerts && alerts.length > 0 && (
        <div className="bg-white rounded-lg border overflow-hidden">
          <div className="px-4 py-3 bg-red-50 border-b font-semibold text-sm text-red-800 flex items-center justify-between">
            <span>Alerts ({alerts.length})</span>
          </div>
          <div className="divide-y max-h-48 overflow-y-auto">
            {alerts.map((a: any) => (
              <div key={a.id} className="px-4 py-2.5 flex items-center gap-3">
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                  a.severity === 'CRITICAL' ? 'bg-red-100 text-red-700' :
                  a.severity === 'WARNING' ? 'bg-yellow-100 text-yellow-700' : 'bg-blue-100 text-blue-700'
                }`}>{a.severity}</span>
                <span className="text-xs text-gray-500">{a.alert_type}</span>
                <span className="text-sm text-gray-700 flex-1">{a.message}</span>
                <button onClick={() => ackAlert.mutate(a.id)} className="text-xs text-indigo-500 hover:underline">Acknowledge</button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Method Distribution + Per-KS */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Methods */}
        {methods && methods.length > 0 && (
          <div className="bg-white rounded-lg border p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">QA Method Distribution (30d)</h3>
            <div className="space-y-2">
              {methods.map((m: any) => (
                <div key={m.method} className="flex items-center gap-3">
                  <span className="text-xs font-medium w-20">{m.method}</span>
                  <div className="flex-1 bg-gray-100 rounded-full h-4 overflow-hidden">
                    <div className="bg-indigo-500 h-full rounded-full" style={{
                      width: `${Math.round((m.count / Math.max(...methods.map((x: any) => x.count))) * 100)}%`
                    }} />
                  </div>
                  <span className="text-xs text-gray-500 w-16 text-right">{m.count} ({Math.round(m.avg_confidence * 100)}%)</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Per-KS breakdown */}
        {dashboard.perKnowledgeSet && (
          <div className="bg-white rounded-lg border p-4">
            <h3 className="text-sm font-semibold text-gray-700 mb-3">Per Knowledge Set</h3>
            <div className="space-y-2">
              {(dashboard.perKnowledgeSet as any[]).map((ks: any) => (
                <div key={ks.id} className="flex items-center gap-3 text-sm">
                  <span className="font-medium flex-1 truncate">{ks.name}</span>
                  <span className="text-gray-500">{ks.question_count} Q</span>
                  <span className="text-gray-400">{Math.round((ks.avg_confidence ?? 0) * 100)}%</span>
                  {ks.escalated_count > 0 && <span className="text-orange-500">{ks.escalated_count} esc</span>}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Knowledge Gaps */}
      {gaps && gaps.length > 0 && (
        <div className="bg-white rounded-lg border overflow-hidden">
          <div className="px-4 py-3 bg-orange-50 border-b font-semibold text-sm text-orange-800">
            Knowledge Gaps ({gaps.length} unanswered topics)
          </div>
          <div className="divide-y max-h-64 overflow-y-auto">
            {gaps.map((g: any) => (
              <div key={g.id} className="px-4 py-2.5 flex items-center gap-3">
                <span className="px-1.5 py-0.5 bg-orange-100 text-orange-700 rounded text-[10px] font-bold">{g.frequency}x</span>
                <span className="text-sm text-gray-700 flex-1">{g.question}</span>
                <span className="text-xs text-gray-400">
                  {new Date(g.last_asked_at).toLocaleDateString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Daily Activity Chart */}
      {dashboard.dailyActivity && (dashboard.dailyActivity as any[]).length > 0 && (
        <div className="bg-white rounded-lg border p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Daily Activity (14d)</h3>
          <div className="flex items-end gap-[2px] h-32">
            {(dashboard.dailyActivity as any[]).map((d: any, i: number) => {
              const max = Math.max(...(dashboard.dailyActivity as any[]).map((x: any) => x.questions || 0), 1);
              const h = ((d.questions || 0) / max) * 100;
              return (
                <div key={i} className="flex-1 min-w-[8px] group relative flex flex-col justify-end h-full">
                  <div className="bg-indigo-500 rounded-t opacity-80 hover:opacity-100" style={{ height: `${Math.max(h, 2)}%` }} />
                  <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 hidden group-hover:block bg-gray-800 text-white text-[10px] px-2 py-1 rounded whitespace-nowrap z-10">
                    {d.day}: {d.questions} questions
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function StatBox({ label, value, sub, color }: { label: string; value: any; sub?: string; color?: string }) {
  return (
    <div className="bg-white rounded-lg border p-3 text-center">
      <div className={`text-xl font-bold ${color === 'orange' ? 'text-orange-600' : 'text-indigo-600'}`}>
        {typeof value === 'number' ? value.toLocaleString() : value}
      </div>
      <div className="text-xs text-gray-500">{label}</div>
      {sub && <div className="text-[10px] text-gray-400 mt-0.5">{sub}</div>}
    </div>
  );
}
