import { useState, useRef, useEffect, useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';

interface Props {
  postId: number;
  parentCommentId?: number | null;
  onDone?: () => void;
  placeholder?: string;
}

export default function CommentForm({
  postId,
  parentCommentId,
  onDone,
  placeholder = 'Write a comment...',
}: Props) {
  const [content, setContent] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const queryClient = useQueryClient();

  // @mention state
  const [mentionQuery, setMentionQuery] = useState<string | null>(null);
  const [mentionStart, setMentionStart] = useState(0);
  const [showMentions, setShowMentions] = useState(false);
  const [mentionIndex, setMentionIndex] = useState(0);

  const { data: mentionResults } = useQuery<{ id: number; username: string; displayName: string; avatarUrl: string | null }[]>({
    queryKey: ['mention-search', mentionQuery],
    queryFn: () => api.get('/users/search', { params: { q: mentionQuery } }).then(r => r.data),
    enabled: mentionQuery !== null && mentionQuery.length >= 1,
    staleTime: 10000,
  });

  const filteredMentions = mentionResults?.slice(0, 8) ?? [];

  const handleContentChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setContent(val);

    const cursorPos = e.target.selectionStart ?? val.length;
    // Find the last @ before cursor that's not already a completed mention
    const textBeforeCursor = val.substring(0, cursorPos);
    const atIdx = textBeforeCursor.lastIndexOf('@');

    if (atIdx >= 0) {
      // Check that the @ isn't inside a completed mention like @[Name](id)
      const beforeAt = textBeforeCursor.substring(0, atIdx);
      const afterAt = textBeforeCursor.substring(atIdx + 1);

      // If there's no space before @ (except at start) or if @ is preceded by a [ (completed mention), skip
      if (atIdx > 0 && beforeAt[atIdx - 1] !== ' ' && beforeAt[atIdx - 1] !== '\n') {
        setShowMentions(false);
        setMentionQuery(null);
        return;
      }

      // Check the query after @ doesn't contain spaces (simple word matching)
      if (!afterAt.includes(' ') && !afterAt.includes(']')) {
        setMentionQuery(afterAt);
        setMentionStart(atIdx);
        setShowMentions(true);
        setMentionIndex(0);
        return;
      }
    }

    setShowMentions(false);
    setMentionQuery(null);
  }, []);

  const insertMention = useCallback((user: { id: number; displayName: string; username: string }) => {
    const before = content.substring(0, mentionStart);
    const after = content.substring((inputRef.current?.selectionStart ?? content.length));
    const mention = `@[${user.displayName}](${user.id}) `;
    const newContent = before + mention + after;
    setContent(newContent);
    setShowMentions(false);
    setMentionQuery(null);

    // Focus back on input
    setTimeout(() => {
      if (inputRef.current) {
        inputRef.current.focus();
        const pos = before.length + mention.length;
        inputRef.current.setSelectionRange(pos, pos);
      }
    }, 0);
  }, [content, mentionStart]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!showMentions || filteredMentions.length === 0) return;

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setMentionIndex(i => Math.min(i + 1, filteredMentions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setMentionIndex(i => Math.max(i - 1, 0));
    } else if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault();
      insertMention(filteredMentions[mentionIndex]);
    } else if (e.key === 'Escape') {
      setShowMentions(false);
      setMentionQuery(null);
    }
  }, [showMentions, filteredMentions, mentionIndex, insertMention]);

  const addComment = useMutation({
    mutationFn: async () => {
      const attachmentIds: number[] = [];
      for (const file of files) {
        const fd = new FormData();
        fd.append('file', file);
        const { data } = await api.post('/attachments/upload', fd);
        attachmentIds.push(data.id);
      }

      return api.post('/comments', {
        postId,
        parentCommentId: parentCommentId ?? undefined,
        content,
        attachmentIds: attachmentIds.length > 0 ? attachmentIds : undefined,
      });
    },
    onSuccess: () => {
      setContent('');
      setFiles([]);
      setShowMentions(false);
      queryClient.invalidateQueries({ queryKey: ['comments', postId] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      onDone?.();
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (showMentions) return; // Don't submit while picking a mention
    if (!content.trim() && files.length === 0) return;
    addComment.mutate();
  };

  const canSend = (content.trim().length > 0 || files.length > 0) && !addComment.isPending;

  return (
    <div className="space-y-2">
      {/* File previews */}
      {files.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {files.map((f, i) => (
            <div key={`${f.name}-${i}`} className="relative group">
              {f.type.startsWith('image/') ? (
                <img src={URL.createObjectURL(f)} alt={f.name} className="w-16 h-16 object-cover rounded-lg border border-gray-200" />
              ) : (
                <div className="w-16 h-16 bg-gray-100 rounded-lg border border-gray-200 flex items-center justify-center">
                  <span className="text-[9px] text-gray-500 truncate w-14 text-center">{f.name}</span>
                </div>
              )}
              <button
                type="button"
                onClick={() => setFiles(prev => prev.filter((_, idx) => idx !== i))}
                className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-red-500 text-white rounded-full text-xs flex items-center justify-center shadow hover:bg-red-600"
              >
                x
              </button>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="flex gap-2 items-center relative">
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*"
          style={{ position: 'absolute', left: -9999, opacity: 0, width: 1, height: 1 }}
          onChange={(e) => {
            const newFiles = Array.from(e.target.files ?? []);
            if (newFiles.length > 0) setFiles(prev => [...prev, ...newFiles]);
            e.target.value = '';
          }}
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="p-1.5 text-gray-400 hover:text-primary-500 hover:bg-gray-50 rounded transition-colors shrink-0"
          title="Attach image or video"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
        </button>
        <div className="flex-1 relative">
          <input
            ref={inputRef}
            value={content}
            onChange={handleContentChange}
            onKeyDown={handleKeyDown}
            onBlur={() => setTimeout(() => setShowMentions(false), 200)}
            placeholder={files.length > 0 ? 'Add a caption...' : placeholder}
            className="input-field w-full text-sm py-2"
          />

          {/* @mention dropdown */}
          {showMentions && filteredMentions.length > 0 && (
            <div className="absolute bottom-full left-0 right-0 mb-1 bg-white border border-gray-200 rounded-lg shadow-lg z-50 max-h-56 overflow-y-auto">
              {filteredMentions.map((user, i) => (
                <button
                  key={user.id}
                  type="button"
                  onMouseDown={(e) => { e.preventDefault(); insertMention(user); }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 text-left text-sm transition-colors ${
                    i === mentionIndex ? 'bg-primary-50' : 'hover:bg-gray-50'
                  }`}
                >
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} className="w-7 h-7 rounded-full object-cover" alt="" />
                  ) : (
                    <div className="w-7 h-7 bg-primary-400 text-white rounded-full flex items-center justify-center text-xs font-bold">
                      {user.displayName?.[0]?.toUpperCase() ?? '?'}
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="font-medium text-gray-900 truncate">{user.displayName}</div>
                    <div className="text-xs text-gray-400">@{user.username}</div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <button
          type="submit"
          disabled={!canSend}
          className="btn-primary text-sm px-4"
        >
          {addComment.isPending ? '...' : 'Post'}
        </button>
      </form>
    </div>
  );
}
