import { useState } from 'react';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useAuthStore } from '../../stores/authStore';
import api from '../../api/client';

interface Props {
  type: 'conversation' | 'comments';
  targetId: number | string;
}

export default function SummarizeButton({ type, targetId }: Props) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState('');
  const [error, setError] = useState<string | null>(null);
  const token = useAuthStore((s) => s.token);
  const debugUserId = useAuthStore((s) => s.debugUserId);

  const summarize = async () => {
    setLoading(true);
    setSummary('');
    setError(null);

    try {
      const headers: Record<string, string> = { 'Content-Type': 'application/json' };
      if (debugUserId) headers['X-Debug-User-Id'] = String(debugUserId);
      else if (token) headers['Authorization'] = `Bearer ${token}`;

      const baseURL = (api.defaults as { baseURL?: string }).baseURL ?? '';
      const endpoint = type === 'conversation' ? '/ai/summarize/conversation' : '/ai/summarize/comments';
      const bodyKey = type === 'conversation' ? 'conversationId' : 'postId';

      const res = await fetch(`${baseURL}${endpoint}`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ [bodyKey]: targetId }),
      });

      if (!res.ok) throw new Error(`Request failed: ${res.status}`);

      const reader = res.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) throw new Error('No stream');

      let buffer = '';
      let currentEvent = '';
      let done = false;

      while (!done) {
        const result = await reader.read();
        if (result.done) break;

        buffer += decoder.decode(result.value, { stream: true });
        let idx: number;
        while ((idx = buffer.indexOf('\n')) !== -1) {
          const line = buffer.slice(0, idx).trim();
          buffer = buffer.slice(idx + 1);

          if (line.startsWith('event:')) {
            currentEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const data = line.slice(5);
            if (currentEvent === 'token') {
              setSummary((prev) => prev + data.split('⏎').join('\n'));
            } else if (currentEvent === 'done') {
              done = true;
              reader.cancel();
              break;
            } else if (currentEvent === 'error') {
              setError(data);
            }
            currentEvent = '';
          }
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setLoading(false);
    }
  };

  if (!open) {
    return (
      <button
        onClick={() => { setOpen(true); summarize(); }}
        className="flex items-center gap-1 text-[11px] text-purple-600 hover:text-purple-700 bg-purple-50 hover:bg-purple-100 px-2.5 py-1 rounded-full transition-colors font-medium"
      >
        <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z" />
        </svg>
        Summarize
      </button>
    );
  }

  return (
    <div className="bg-purple-50 border border-purple-100 rounded-lg p-3 space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-purple-700">AI Summary</span>
        <button onClick={() => { setOpen(false); setSummary(''); }} className="text-[10px] text-gray-400 hover:text-gray-600">
          Close
        </button>
      </div>
      {loading && !summary && (
        <div className="flex items-center gap-2 text-xs text-gray-400">
          <span className="w-3 h-3 border-2 border-purple-400 border-t-transparent rounded-full animate-spin" />
          Summarizing...
        </div>
      )}
      {summary && (
        <div className="prose prose-sm max-w-none text-gray-700">
          <Markdown remarkPlugins={[remarkGfm]}>{summary}</Markdown>
        </div>
      )}
      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  );
}
