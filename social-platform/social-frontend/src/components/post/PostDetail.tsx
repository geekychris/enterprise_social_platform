import { useQuery } from '@tanstack/react-query';
import api from '../../api/client';
import type { CommentDto } from '../../api/types';
import CommentThread from './CommentThread';
import CommentForm from './CommentForm';

interface Props {
  postId: number;
}

export default function PostDetail({ postId }: Props) {
  const { data: comments, isLoading } = useQuery<CommentDto[]>({
    queryKey: ['comments', postId],
    queryFn: async () => {
      const { data } = await api.get(`/posts/${postId}/comments`);
      return data;
    },
  });

  return (
    <div className="p-4 space-y-2">
      <CommentForm postId={postId} />

      {isLoading && (
        <div className="space-y-2 py-2">
          {[1, 2].map((i) => (
            <div key={i} className="flex gap-2">
              <div className="skeleton w-8 h-8 rounded-full" />
              <div className="skeleton h-12 flex-1 rounded-2xl" />
            </div>
          ))}
        </div>
      )}

      {comments?.map((comment) => (
        <CommentThread key={comment.id} comment={comment} />
      ))}

      {!isLoading && (!comments || comments.length === 0) && (
        <p className="text-xs text-gray-400 text-center py-2">
          No comments yet
        </p>
      )}
    </div>
  );
}
