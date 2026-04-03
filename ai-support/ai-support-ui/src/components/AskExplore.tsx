import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import api from '../api/client';

const EXAMPLE_QUESTIONS: Record<string, string[]> = {
  '1': [
    'How should I implement line drawing on the Amiga?',
    'How do I fix bad capacitors on my Amiga 500?',
    'What is the Blitter and how do I use it for graphics?',
    'How do I set up WinUAE emulation?',
    'How do I do double buffering for smooth animation?',
    'What is the PiStorm accelerator?',
    'How do I add memory to my Amiga 500?',
  ],
  '2': [
    'How do I implement line drawing on a Gowin FPGA?',
    'How do I generate VGA output on the Tang Nano 9K?',
    'How do I use Block RAM as a frame buffer?',
    'What is a CST pin constraint file?',
    'How do I fix timing closure errors?',
  ],
  '3': [
    'How do I draw lines on my Atari 800XL?',
    'How do I draw lines in assembler on the Atari?',
    'What is FujiNet and how do I set it up?',
    'How do I connect my Atari to a modern TV?',
    'What graphics modes does the Atari support?',
  ],
  '4': [
    'What retro computers are good for learning programming?',
    'What is an FPGA and why would I use one?',
  ],
};

export default function AskExplore() {
  const [ksId, setKsId] = useState('1');
  const [question, setQuestion] = useState('');
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [expandedTrace, setExpandedTrace] = useState<number | null>(null);

  const { data: sets } = useQuery<any[]>({
    queryKey: ['sets'],
    queryFn: () => api.get('/knowledge/sets').then(r => r.data),
  });

  const askAgentic = useMutation({
    mutationFn: (q: string) =>
      api.post('/qa/ask-agentic', { knowledgeSetId: ksId, question: q }).then(r => r.data),
    onSuccess: (data) => {
      setConversations(prev => [{
        id: Date.now(),
        question,
        ksId,
        ksName: sets?.find((s: any) => String(s.id) === ksId)?.name ?? 'Unknown',
        ...data,
      }, ...prev]);
      setQuestion('');
    },
  });

  const handleAsk = () => {
    if (!question.trim() || askAgentic.isPending) return;
    askAgentic.mutate(question);
  };

  const examples = EXAMPLE_QUESTIONS[ksId] ?? [];

  return (
    <div className="space-y-4">
      {/* Input area */}
      <div className="bg-white rounded-xl border p-5 space-y-4">
        <div className="flex gap-3 flex-wrap items-end">
          <div>
            <label className="block text-xs text-gray-400 mb-1">Knowledge Base</label>
            <select value={ksId} onChange={e => setKsId(e.target.value)}
              className="border rounded-lg px-3 py-2.5 text-sm bg-white">
              {sets?.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div className="flex-1 min-w-[300px]">
            <label className="block text-xs text-gray-400 mb-1">Your Question</label>
            <div className="flex gap-2">
              <input value={question} onChange={e => setQuestion(e.target.value)}
                placeholder="Ask anything about this topic..."
                className="flex-1 border rounded-lg px-4 py-2.5 text-sm focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
                onKeyDown={e => e.key === 'Enter' && handleAsk()} />
              <button onClick={handleAsk} disabled={!question.trim() || askAgentic.isPending}
                className="bg-indigo-500 text-white px-6 py-2.5 rounded-lg text-sm font-medium hover:bg-indigo-600 disabled:opacity-50 shrink-0">
                {askAgentic.isPending ? (
                  <span className="flex items-center gap-2">
                    <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    Thinking...
                  </span>
                ) : 'Ask'}
              </button>
            </div>
          </div>
        </div>

        {/* Example questions */}
        {examples.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <span className="text-xs text-gray-400 self-center">Try:</span>
            {examples.map((q, i) => (
              <button key={i} onClick={() => { setQuestion(q); }}
                className="text-xs text-gray-500 bg-gray-50 border border-gray-200 px-2.5 py-1.5 rounded-full hover:bg-indigo-50 hover:border-indigo-200 hover:text-indigo-600 transition-colors">
                {q}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Conversations */}
      {conversations.map((conv) => (
        <ConversationCard
          key={conv.id}
          conv={conv}
          expanded={expandedTrace === conv.id}
          onToggleTrace={() => setExpandedTrace(expandedTrace === conv.id ? null : conv.id)}
        />
      ))}

      {conversations.length === 0 && !askAgentic.isPending && (
        <div className="text-center py-16 text-gray-400">
          <div className="text-4xl mb-3">🤖</div>
          <div className="text-sm">Ask a question to get started. The AI will search the knowledge base and give you a curated answer.</div>
        </div>
      )}
    </div>
  );
}

interface ConversationItem {
  id: number;
  question: string;
  ksId: string;
  ksName: string;
  answer: string;
  confidence: number;
  method: string;
  suggestHuman: boolean;
  traceId?: number;
  interactionId?: number;
  steps?: ToolStep[];
  citations?: Citation[];
}

interface ToolStep {
  iteration: number;
  thought: string;
  tool: string;
  args: string;
  resultPreview: string;
  durationMs: number;
}

interface Citation {
  documentId: number;
  title: string;
  snippet: string;
}

function ConversationCard({ conv, expanded, onToggleTrace }: {
  conv: ConversationItem; expanded: boolean; onToggleTrace: () => void;
}) {
  const [rated, setRated] = useState<number | null>(null);
  const { data: trace } = useQuery({
    queryKey: ['trace-detail', conv.traceId],
    queryFn: () => api.get(`/qa/traces/${conv.traceId}`).then(r => r.data),
    enabled: expanded && !!conv.traceId,
  });

  const rateMutation = useMutation({
    mutationFn: (rating: number) => api.post('/qa/feedback', {
      interactionId: conv.interactionId,
      rating,
      comment: rating >= 4 ? 'Helpful' : 'Not helpful'
    }),
    onSuccess: (_, rating) => setRated(rating),
  });

  const toolSteps = (conv.steps ?? []).filter(s => s.tool !== 'none');
  const confColor = conv.confidence >= 0.7 ? 'green' : conv.confidence >= 0.5 ? 'yellow' : 'red';

  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      {/* Question */}
      <div className="px-5 py-4 bg-gradient-to-r from-indigo-50 to-white border-b">
        <div className="flex items-start gap-3">
          <div className="w-8 h-8 bg-indigo-100 text-indigo-600 rounded-full flex items-center justify-center text-sm font-bold shrink-0 mt-0.5">Q</div>
          <div className="flex-1">
            <div className="text-sm font-medium text-gray-900">{conv.question}</div>
            <div className="text-xs text-gray-400 mt-1">{conv.ksName}</div>
          </div>
        </div>
      </div>

      {/* Agentic steps (what the AI did) */}
      {toolSteps.length > 0 && (
        <div className="px-5 py-3 bg-gray-50 border-b space-y-2">
          <div className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">What the AI did</div>
          {toolSteps.map((step, i) => (
            <div key={i} className="flex items-start gap-2">
              <div className="w-5 h-5 bg-purple-100 text-purple-600 rounded flex items-center justify-center text-[10px] font-bold shrink-0 mt-0.5">{i + 1}</div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <code className="text-xs text-purple-700 bg-purple-50 px-1.5 py-0.5 rounded">{step.tool}({step.args.length > 40 ? step.args.substring(0, 40) + '...' : step.args})</code>
                  <span className="text-[10px] text-gray-400">{step.durationMs}ms</span>
                </div>
                {step.thought && <div className="text-xs text-gray-500 italic mt-0.5">{step.thought.substring(0, 150)}</div>}
                {step.resultPreview && (
                  <details className="mt-1">
                    <summary className="text-[10px] text-indigo-500 cursor-pointer hover:text-indigo-700">Show retrieved data</summary>
                    <pre className="mt-1 text-[10px] text-gray-500 whitespace-pre-wrap max-h-40 overflow-y-auto bg-white rounded-lg border p-2.5 leading-relaxed">{step.resultPreview}</pre>
                  </details>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Answer */}
      <div className="px-5 py-4">
        <div className="flex items-start gap-3">
          <div className="w-8 h-8 bg-green-100 text-green-600 rounded-full flex items-center justify-center text-sm font-bold shrink-0 mt-0.5">A</div>
          <div className="flex-1 min-w-0">
            <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">{conv.answer}</div>
          </div>
        </div>
      </div>

      {/* Footer: confidence, citations, trace link */}
      <div className="px-5 py-3 bg-gray-50 border-t flex items-center gap-3 flex-wrap">
        <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${
          confColor === 'green' ? 'bg-green-100 text-green-700' :
          confColor === 'yellow' ? 'bg-yellow-100 text-yellow-700' : 'bg-red-100 text-red-700'
        }`}>
          {(conv.confidence * 100).toFixed(0)}% confidence
        </span>
        <span className="px-2 py-0.5 bg-purple-50 text-purple-600 rounded text-[10px]">{conv.method}</span>
        {conv.suggestHuman && <span className="px-2 py-0.5 bg-orange-100 text-orange-700 rounded text-[10px]">Suggests human help</span>}
        {toolSteps.length > 0 && <span className="text-[10px] text-gray-400">{toolSteps.length} search{toolSteps.length > 1 ? 'es' : ''}</span>}
        {/* Feedback buttons */}
        {conv.interactionId && (
          <div className="flex gap-1">
            <button onClick={() => rateMutation.mutate(5)} disabled={rated !== null}
              className={`px-1.5 py-0.5 rounded text-xs transition-colors ${rated === 5 ? 'bg-green-500 text-white' : rated !== null ? 'bg-gray-100 text-gray-300' : 'bg-gray-100 text-gray-500 hover:bg-green-100 hover:text-green-700'}`}>
              👍
            </button>
            <button onClick={() => rateMutation.mutate(1)} disabled={rated !== null}
              className={`px-1.5 py-0.5 rounded text-xs transition-colors ${rated === 1 ? 'bg-red-500 text-white' : rated !== null ? 'bg-gray-100 text-gray-300' : 'bg-gray-100 text-gray-500 hover:bg-red-100 hover:text-red-700'}`}>
              👎
            </button>
          </div>
        )}
        <div className="flex-1" />
        {conv.citations && conv.citations.length > 0 && (
          <span className="text-[10px] text-gray-400">Sources: {conv.citations.map(c => c.title).join(', ')}</span>
        )}
        {conv.traceId && (
          <button onClick={onToggleTrace}
            className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${
              expanded ? 'bg-purple-500 text-white' : 'bg-purple-100 text-purple-700 hover:bg-purple-200'
            }`}>
            {expanded ? 'Hide Trace' : `Trace #${conv.traceId}`}
          </button>
        )}
      </div>

      {/* Expanded trace detail */}
      {expanded && trace && <TracePanel trace={trace} />}
    </div>
  );
}

function TracePanel({ trace }: { trace: any }) {
  const parseJson = (val: any) => {
    if (!val) return [];
    if (typeof val === 'string') try { return JSON.parse(val); } catch { return []; }
    return Array.isArray(val) ? val : [];
  };

  const contextChunks = parseJson(trace.context_chunks);
  const lexResults = parseJson(trace.lexical_results);
  const semResults = parseJson(trace.semantic_results);
  const citations = parseJson(trace.citations);

  return (
    <div className="border-t bg-white">
      <div className="px-5 py-3 bg-purple-50 border-b">
        <div className="text-xs font-semibold text-purple-800">Full Pipeline Trace #{trace.id}</div>
      </div>
      <div className="px-5 py-4 space-y-4 text-xs max-h-[600px] overflow-y-auto">
        {/* Overview */}
        <div className="grid grid-cols-3 gap-3">
          <Stat label="Method" value={trace.method} />
          <Stat label="LLM" value={trace.llm_model ?? '-'} />
          <Stat label="LLM Time" value={trace.llm_duration_ms ? `${trace.llm_duration_ms}ms` : '-'} />
          <Stat label="Total KB Tokens" value={trace.total_knowledge_tokens ?? '-'} />
          <Stat label="Context Tokens" value={trace.context_token_count ?? '-'} />
          <Stat label="Confidence" value={trace.confidence != null ? `${(trace.confidence * 100).toFixed(0)}%` : '-'} />
        </div>

        {/* Agentic steps (from context_chunks when method=AGENTIC) */}
        {trace.method === 'AGENTIC' && contextChunks.length > 0 && contextChunks[0]?.tool && (
          <Section title="Agentic Steps">
            {contextChunks.map((step: any, i: number) => (
              <div key={i} className="bg-purple-50 rounded-lg px-3 py-2 space-y-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-purple-700">Step {step.iteration + 1}: {step.tool}({(step.args ?? '').substring(0, 50)})</span>
                  <span className="text-gray-400">{step.durationMs}ms</span>
                </div>
                {step.thought && <div className="text-gray-500 italic">{step.thought}</div>}
                {step.resultPreview && <pre className="text-[10px] text-gray-500 whitespace-pre-wrap max-h-32 overflow-y-auto bg-white rounded p-2 border">{step.resultPreview}</pre>}
              </div>
            ))}
          </Section>
        )}

        {/* Lexical search results (RAG mode) */}
        {lexResults.length > 0 && (
          <Section title={`Lexical Search (${lexResults.length} hits)`}>
            {lexResults.map((r: any, i: number) => (
              <div key={i} className="bg-amber-50 rounded px-2 py-1.5">
                <span className="text-gray-400">chunk-{r.chunkId} (doc-{r.documentId})</span>
                <span className="ml-2 font-mono text-amber-700">score={Number(r.score).toFixed(3)}</span>
                {r.title && <span className="ml-2 text-gray-600">{r.title}</span>}
                {r.content && <div className="text-gray-500 mt-0.5 line-clamp-2">{r.content.substring(0, 200)}</div>}
              </div>
            ))}
          </Section>
        )}

        {/* Semantic search results (RAG mode) */}
        {semResults.length > 0 && (
          <Section title={`Semantic Search (${semResults.length} hits)`}>
            {semResults.map((r: any, i: number) => (
              <div key={i} className="bg-blue-50 rounded px-2 py-1.5">
                <span className="text-gray-400">chunk-{r.chunkId} (doc-{r.documentId})</span>
                <span className="ml-2 font-mono text-blue-700">cosine={Number(r.score).toFixed(3)}</span>
              </div>
            ))}
          </Section>
        )}

        {/* Context chunks used (RAG mode) */}
        {trace.method === 'RAG' && contextChunks.length > 0 && !contextChunks[0]?.tool && (
          <Section title={`Context Chunks Sent to LLM (${contextChunks.length})`}>
            {contextChunks.map((c: any, i: number) => (
              <div key={i} className="bg-green-50 rounded px-2 py-1.5">
                <div className="flex gap-2">
                  <span className="text-green-700 font-medium">chunk-{c.chunkId}</span>
                  <span className="text-gray-400">doc-{c.documentId}</span>
                  <span className="text-gray-400">{c.tokens} tokens</span>
                </div>
                {c.content && <div className="text-gray-500 mt-0.5 line-clamp-3">{c.content.substring(0, 300)}</div>}
              </div>
            ))}
          </Section>
        )}

        {/* Prompts */}
        {trace.system_prompt && (
          <Section title="System Prompt">
            <pre className="bg-gray-50 rounded p-2.5 whitespace-pre-wrap text-gray-500 max-h-32 overflow-y-auto">{trace.system_prompt}</pre>
          </Section>
        )}
        {trace.user_prompt && (
          <Section title="User Prompt (context + question)">
            <pre className="bg-gray-50 rounded p-2.5 whitespace-pre-wrap text-gray-500 max-h-48 overflow-y-auto">{trace.user_prompt}</pre>
          </Section>
        )}

        {/* Citations */}
        {citations.length > 0 && (
          <Section title={`Citations (${citations.length})`}>
            {citations.map((c: any, i: number) => (
              <div key={i} className="text-gray-600">
                [{i + 1}] <span className="font-medium">{c.title}</span>
                <span className="text-gray-400 ml-1">(doc-{c.documentId})</span>
              </div>
            ))}
          </Section>
        )}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs font-semibold text-gray-600 mb-1.5">{title}</div>
      <div className="space-y-1.5">{children}</div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="bg-gray-50 rounded-lg px-3 py-2 text-center">
      <div className="text-gray-900 font-semibold">{value}</div>
      <div className="text-[10px] text-gray-400">{label}</div>
    </div>
  );
}
