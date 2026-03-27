import { useState, useRef, useCallback, type RefObject } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';

function wrapSelection(
  textareaRef: RefObject<HTMLTextAreaElement | null>,
  content: string,
  setContent: (v: string) => void,
  wrapper: string
) {
  const el = textareaRef.current;
  if (!el) return;
  const start = el.selectionStart;
  const end = el.selectionEnd;
  const selected = content.slice(start, end);
  const before = content.slice(0, start);
  const after = content.slice(end);
  const wrapped = `${wrapper}${selected}${wrapper}`;
  setContent(before + wrapped + after);
  // Restore focus after state update
  setTimeout(() => {
    el.focus();
    el.setSelectionRange(start + wrapper.length, end + wrapper.length);
  }, 0);
}

function insertLink(
  textareaRef: RefObject<HTMLTextAreaElement | null>,
  content: string,
  setContent: (v: string) => void
) {
  const el = textareaRef.current;
  if (!el) return;
  const url = prompt('Enter URL:');
  if (!url) return;
  const start = el.selectionStart;
  const end = el.selectionEnd;
  const selected = content.slice(start, end);
  const before = content.slice(0, start);
  const after = content.slice(end);
  const linkText = selected || url;
  const inserted = `${linkText} (${url})`;
  setContent(before + inserted + after);
  setTimeout(() => {
    el.focus();
  }, 0);
}

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: 'Public' },
  { value: 'TEAM_VISIBLE', label: 'Team Visible' },
  { value: 'RESTRICTED', label: 'Restricted' },
  { value: 'PRIVATE', label: 'Private' },
];

interface FilePreview {
  file: File;
  previewUrl: string | null;
}

interface CreatePostFormProps {
  defaultTargetType?: string;
  defaultTargetId?: number | string;
}

export default function CreatePostForm({ defaultTargetType, defaultTargetId }: CreatePostFormProps) {
  const [content, setContent] = useState('');
  const [visibility, setVisibility] = useState('PUBLIC');
  const [targetType, setTargetType] = useState<string | null>(defaultTargetType ?? null);
  const [targetId, setTargetId] = useState(defaultTargetId ? String(defaultTargetId) : '');
  const [filePreviews, setFilePreviews] = useState<FilePreview[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const queryClient = useQueryClient();

  const addFiles = useCallback((newFiles: File[]) => {
    const previews = newFiles.map((file) => {
      const isImage = file.type.startsWith('image/');
      return {
        file,
        previewUrl: isImage ? URL.createObjectURL(file) : null,
      };
    });
    setFilePreviews((prev) => [...prev, ...previews]);
  }, []);

  const removeFile = useCallback((index: number) => {
    setFilePreviews((prev) => {
      const removed = prev[index];
      if (removed.previewUrl) URL.revokeObjectURL(removed.previewUrl);
      return prev.filter((_, i) => i !== index);
    });
  }, []);

  // Handle paste - intercept image paste from clipboard
  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    const items = e.clipboardData?.items;
    if (!items) return;

    const imageFiles: File[] = [];
    for (const item of Array.from(items)) {
      if (item.type.startsWith('image/')) {
        e.preventDefault();
        const file = item.getAsFile();
        if (file) {
          // Name the pasted image
          const ext = file.type.split('/')[1] || 'png';
          const named = new File([file], `pasted-image-${Date.now()}.${ext}`, {
            type: file.type,
          });
          imageFiles.push(named);
        }
      }
    }
    if (imageFiles.length > 0) {
      addFiles(imageFiles);
    }
  }, [addFiles]);

  // Handle drag and drop
  const [isDragging, setIsDragging] = useState(false);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const droppedFiles = Array.from(e.dataTransfer.files);
    if (droppedFiles.length > 0) {
      addFiles(droppedFiles);
    }
  }, [addFiles]);

  const createPost = useMutation({
    mutationFn: async () => {
      // Upload attachments first
      const attachmentIds: number[] = [];
      for (const { file } of filePreviews) {
        const form = new FormData();
        form.append('file', file);
        const { data } = await api.post('/attachments/upload', form);
        attachmentIds.push(data.id);
      }

      return api.post('/posts', {
        content,
        targetType: targetType || undefined,
        targetId: targetId || undefined,
        visibility,
        attachmentIds: attachmentIds.length ? attachmentIds : undefined,
      });
    },
    onSuccess: () => {
      setContent('');
      // Clean up preview URLs
      filePreviews.forEach((fp) => {
        if (fp.previewUrl) URL.revokeObjectURL(fp.previewUrl);
      });
      setFilePreviews([]);
      // Reset target only if not using defaults
      if (!defaultTargetType) {
        setTargetType(null);
        setTargetId('');
      }
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      if (defaultTargetType === 'GROUP_FEED' && defaultTargetId) {
        queryClient.invalidateQueries({ queryKey: ['group-posts', defaultTargetId] });
      }
      if (defaultTargetType === 'PAGE_FEED' && defaultTargetId) {
        queryClient.invalidateQueries({ queryKey: ['page-posts', defaultTargetId] });
      }
    },
    onError: (err) => {
      console.error('Failed to create post:', err);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim() && filePreviews.length === 0) return;
    createPost.mutate();
  };

  return (
    <form
      onSubmit={handleSubmit}
      className={`card p-4 space-y-3 ${isDragging ? 'ring-2 ring-primary-500 ring-offset-2' : ''}`}
      onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
    >
      {/* Formatting toolbar (only for group/page posts) */}
      {defaultTargetType && (
        <div className="flex items-center gap-1 pb-1">
          <button
            type="button"
            onClick={() => wrapSelection(textareaRef, content, setContent, '**')}
            className="px-2 py-1 text-xs font-bold text-gray-600 hover:bg-gray-100 rounded transition-colors"
            title="Bold"
          >
            B
          </button>
          <button
            type="button"
            onClick={() => wrapSelection(textareaRef, content, setContent, '_')}
            className="px-2 py-1 text-xs italic text-gray-600 hover:bg-gray-100 rounded transition-colors"
            title="Italic"
          >
            I
          </button>
          <button
            type="button"
            onClick={() => insertLink(textareaRef, content, setContent)}
            className="px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 rounded transition-colors flex items-center gap-1"
            title="Insert Link"
          >
            <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
            Link
          </button>
        </div>
      )}

      <textarea
        ref={textareaRef}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        onPaste={handlePaste}
        placeholder="What's on your mind? (paste images with Ctrl+V)"
        rows={3}
        className="input-field resize-none"
      />

      {/* Image previews */}
      {filePreviews.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {filePreviews.map((fp, i) => (
            <div key={i} className="relative group">
              {fp.previewUrl ? (
                <img
                  src={fp.previewUrl}
                  alt=""
                  className="w-24 h-24 object-cover rounded-lg border border-gray-200"
                />
              ) : (
                <div className="w-24 h-24 bg-gray-100 rounded-lg border border-gray-200 flex items-center justify-center">
                  <div className="text-center">
                    <svg className="w-6 h-6 mx-auto text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                    </svg>
                    <span className="text-[10px] text-gray-400 truncate block w-20 mt-1">
                      {fp.file.name}
                    </span>
                  </div>
                </div>
              )}
              <button
                type="button"
                onClick={() => removeFile(i)}
                className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-red-500 text-white rounded-full text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow"
              >
                x
              </button>
            </div>
          ))}
        </div>
      )}

      {isDragging && (
        <div className="border-2 border-dashed border-primary-300 rounded-lg p-6 text-center text-primary-500 text-sm">
          Drop files here
        </div>
      )}

      <div className="flex items-center gap-2 flex-wrap">
        {/* Visibility */}
        <select
          value={visibility}
          onChange={(e) => setVisibility(e.target.value)}
          className="text-sm border border-gray-200 rounded-lg px-2 py-1.5 bg-white text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500"
        >
          {VISIBILITY_OPTIONS.map((v) => (
            <option key={v.value} value={v.value}>
              {v.label}
            </option>
          ))}
        </select>

        {/* Target label when posting to a specific group/page */}
        {defaultTargetType && (
          <span className="text-xs text-gray-400 px-1">
            Posting to {defaultTargetType === 'GROUP_FEED' ? 'group' : defaultTargetType === 'PAGE_FEED' ? 'page' : 'feed'}
          </span>
        )}

        {/* File attach button */}
        <input
          ref={fileInputRef}
          type="file"
          multiple
          accept="image/*,video/*,.mov,.avi,.mkv,.webm"
          className="hidden"
          onChange={(e) => {
            if (e.target.files) addFiles(Array.from(e.target.files));
            e.target.value = '';
          }}
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="text-sm text-gray-500 hover:text-primary-500 hover:bg-gray-50 px-2 py-1.5 rounded-lg transition-colors flex items-center gap-1"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
          </svg>
          Media
        </button>

        <div className="flex-1" />

        <button
          type="submit"
          disabled={(!content.trim() && filePreviews.length === 0) || createPost.isPending}
          className="btn-primary text-sm px-6"
        >
          {createPost.isPending ? 'Posting...' : 'Post'}
        </button>
      </div>
    </form>
  );
}
