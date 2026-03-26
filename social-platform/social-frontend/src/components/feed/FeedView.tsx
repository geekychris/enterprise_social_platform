import { useCallback } from 'react';
import { useFeed } from '../../hooks/useFeed';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import PostCard from './PostCard';
import AiAssistant from '../ai/AiAssistant';

function PostSkeleton() {
  return (
    <div className="card p-4 space-y-3">
      <div className="flex items-center gap-3">
        <div className="skeleton w-10 h-10 rounded-full" />
        <div className="space-y-1.5 flex-1">
          <div className="skeleton h-3 w-28" />
          <div className="skeleton h-2.5 w-20" />
        </div>
      </div>
      <div className="space-y-2">
        <div className="skeleton h-3 w-full" />
        <div className="skeleton h-3 w-4/5" />
        <div className="skeleton h-3 w-3/5" />
      </div>
    </div>
  );
}

export default function FeedView() {
  const { data, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage } =
    useFeed();

  const handleLoadMore = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const sentinelRef = useInfiniteScroll(handleLoadMore, !!hasNextPage);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <PostSkeleton />
        <PostSkeleton />
        <PostSkeleton />
      </div>
    );
  }

  const posts = data?.pages.flatMap((p) => p.posts) ?? [];

  if (posts.length === 0) {
    return (
      <div className="card p-8 text-center">
        <p className="text-gray-400 text-sm">
          No posts yet. Be the first to share something!
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <AiAssistant context="feed" />
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}

      {isFetchingNextPage && <PostSkeleton />}

      {hasNextPage && (
        <div ref={sentinelRef} className="h-4" />
      )}

      {!hasNextPage && posts.length > 3 && (
        <p className="text-center text-gray-400 text-xs py-4">
          You've reached the end
        </p>
      )}
    </div>
  );
}
