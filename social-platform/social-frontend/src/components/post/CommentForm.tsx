import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
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
  const queryClient = useQueryClient();

  const addComment = useMutation({
    mutationFn: () =>
      api.post('/comments', {
        postId,
        parentCommentId: parentCommentId ?? undefined,
        content,
      }),
    onSuccess: () => {
      setContent('');
      queryClient.invalidateQueries({ queryKey: ['comments', postId] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      onDone?.();
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim()) return;
    addComment.mutate();
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <input
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={placeholder}
        className="input-field flex-1 text-sm py-2"
      />
      <button
        type="submit"
        disabled={!content.trim() || addComment.isPending}
        className="btn-primary text-sm px-4"
      >
        {addComment.isPending ? '...' : 'Post'}
      </button>
    </form>
  );
}
