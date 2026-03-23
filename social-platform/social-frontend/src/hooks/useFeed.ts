import { useInfiniteQuery } from '@tanstack/react-query';
import api from '../api/client';
import type { FeedResponse } from '../api/types';

export function useFeed(limit = 20) {
  return useInfiniteQuery<FeedResponse>({
    queryKey: ['feed'],
    queryFn: async ({ pageParam }) => {
      const params: Record<string, string> = { limit: String(limit) };
      if (pageParam) params.cursor = pageParam as string;
      const { data } = await api.get('/feed', { params });
      return data;
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasMore ? lastPage.nextCursor : undefined,
  });
}
