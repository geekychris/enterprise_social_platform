import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import api from '../api/client';
import type { PostDto } from '../api/types';
import PostCard from '../components/feed/PostCard';

export default function PostPage() {
  const { id } = useParams<{ id: string }>();

  const { data: post, isLoading } = useQuery<PostDto>({
    queryKey: ['post', id],
    queryFn: () => api.get(`/posts/${id}`).then(r => r.data),
    enabled: !!id,
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="card p-4">
          <div className="flex gap-3">
            <div className="skeleton w-10 h-10 rounded-full" />
            <div className="space-y-2 flex-1">
              <div className="skeleton h-4 w-32" />
              <div className="skeleton h-3 w-full" />
              <div className="skeleton h-3 w-3/4" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (!post) {
    return <div className="card p-8 text-center text-gray-400">Post not found</div>;
  }

  return (
    <div>
      <PostCard post={post} defaultExpanded />
    </div>
  );
}
