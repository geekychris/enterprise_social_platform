import { useState, useRef, useEffect } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useAuthStore } from '../../stores/authStore';
import api from '../../api/client';

interface AiAssistantProps {
  context: 'conversation' | 'group' | 'page' | 'feed';
  contextId?: number | string;
}

export default function AiAssistant({ context, contextId }: AiAssistantProps) {
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState('');
  const [response, setResponse] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const responseRef = useRef<HTMLDivElement>(null);
  const token = useAuthStore((s) => s.token);
  const debugUserId = useAuthStore((s) => s.debugUserId);

  useEffect(() => {
    if (responseRef.current) {
      responseRef.current.scrollTop = responseRef.current.scrollHeight;
    }
  }, [response]);

  const handleAsk = async () => {
    if (!question.trim() || loading) return;

    setLoading(true);
    setResponse('');
    setError(null);

    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (debugUserId) {
        headers['X-Debug-User-Id'] = String(debugUserId);
      } else if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }

      const baseURL = (api.defaults as { baseURL?: string }).baseURL ?? '';
      const res = await fetch(`${baseURL}/ai/ask`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          context,
          contextId: contextId ?? null,
          question: question.trim(),
        }),
      });

      if (!res.ok) {
        throw new Error(`Request failed: ${res.status}`);
      }

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) throw new Error('No response stream');

      let buffer = '';
      let currentEvent = '';
      let done = false;

      while (!done) {
        const result = await reader.read();
        if (result.done) break;

        buffer += decoder.decode(result.value, { stream: true });

        // Process complete lines
        let newlineIdx: number;
        while ((newlineIdx = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, newlineIdx).trim();
          buffer = buffer.slice(newlineIdx + 1);

          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const data = line.slice(5);
            if (currentEvent === 'token') {
              setResponse((prev) => prev + data.split('⏎').join('\n'));
            } else if (currentEvent === 'error') {
              setError(data);
            } else if (currentEvent === 'done') {
              done = true;
              reader.cancel();
              break;
            }
            currentEvent = '';
          }
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to connect to AI');
    } finally {
      setLoading(false);
    }
  };

  const suggestions = getSuggestions(context);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="flex items-center gap-1.5 text-xs text-purple-600 hover:text-purple-700 bg-purple-50 hover:bg-purple-100 px-3 py-1.5 rounded-full transition-colors font-medium"
        title="Ask AI"
      >
        <SparklesIcon className="w-3.5 h-3.5" />
        Ask AI
      </button>
    );
  }

  return (
    <div className="bg-gradient-to-b from-purple-50 to-white border border-purple-100 rounded-xl p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <SparklesIcon className="w-4 h-4 text-purple-500" />
          <span className="text-sm font-semibold text-purple-900">AI Assistant</span>
        </div>
        <button
          onClick={() => { setOpen(false); setResponse(''); setError(null); }}
          className="text-xs text-gray-400 hover:text-gray-600"
        >
          Close
        </button>
      </div>

      {/* Quick suggestions */}
      {!response && !loading && (
        <div className="flex flex-wrap gap-1.5">
          {suggestions.map((s) => (
            <button
              key={s}
              onClick={() => { setQuestion(s); }}
              className="text-[11px] bg-white border border-purple-200 text-purple-700 px-2.5 py-1 rounded-full hover:bg-purple-50 transition-colors"
            >
              {s}
            </button>
          ))}
        </div>
      )}

      {/* Response area */}
      {(response || loading) && (
        <div
          ref={responseRef}
          className="bg-white rounded-lg border border-gray-100 p-3 max-h-60 overflow-y-auto"
        >
          {response ? (
            <div className="prose prose-sm max-w-none text-gray-700">
              <Markdown remarkPlugins={[remarkGfm]}>{response}</Markdown>
            </div>
          ) : (
            <div className="flex items-center gap-2 text-sm text-gray-400">
              <span className="w-4 h-4 border-2 border-purple-400 border-t-transparent rounded-full animate-spin" />
              Thinking...
            </div>
          )}
        </div>
      )}

      {error && (
        <p className="text-xs text-red-500">{error}</p>
      )}

      {/* Input */}
      <div className="flex gap-2">
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') handleAsk(); }}
          placeholder="Ask about this content..."
          className="input-field flex-1 text-sm"
          disabled={loading}
        />
        <button
          onClick={handleAsk}
          disabled={!question.trim() || loading}
          className="bg-purple-500 hover:bg-purple-600 disabled:bg-purple-300 text-white text-sm px-4 py-2 rounded-lg transition-colors font-medium"
        >
          {loading ? (
            <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin inline-block" />
          ) : (
            'Ask'
          )}
        </button>
      </div>
    </div>
  );
}

function getSuggestions(context: string): string[] {
  switch (context) {
    case 'conversation':
      return ['Summarize this conversation', 'What are the key decisions?', 'List action items'];
    case 'group':
      return ['Summarize recent activity', 'What are the trending topics?', 'Any important announcements?'];
    case 'page':
      return ['Summarize recent updates', 'What\'s new on this page?', 'Key highlights'];
    case 'feed':
      return ['Catch me up on what I missed', 'What\'s trending today?', 'Summarize important posts'];
    default:
      return ['Summarize this content'];
  }
}

function SparklesIcon({ className }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
    </svg>
  );
}
