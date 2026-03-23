import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import api from '../../api/client';
import type { SearchResultDto, SearchHit } from '../../api/types';

interface Props {
  query: string;
  type?: string;
}

function getHitLink(hit: SearchHit): string {
  switch (hit.objectType) {
    case 'USER':
      return `/profile/${hit.id}`;
    case 'TEAM':
      return `/team/${hit.id}`;
    case 'GROUP':
      return `/group/${hit.id}`;
    case 'PAGE':
      return `/page/${hit.id}`;
    case 'POST':
      return `/`;
    default:
      return `/profile/${hit.id}`;
  }
}

export default function SearchResults({ query, type }: Props) {
  const { data, isLoading } = useQuery<SearchResultDto>({
    queryKey: ['search', query, type],
    queryFn: async () => {
      const params: Record<string, string> = { q: query };
      if (type) params.type = type;
      const { data } = await api.get('/search', { params });
      return data;
    },
    enabled: !!query,
  });

  if (!query) {
    return (
      <div className="card p-8 text-center text-gray-400 text-sm">
        Enter a search term to find people, teams, and posts.
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-2">
        {[1, 2, 3].map((i) => (
          <div key={i} className="card p-4 flex items-center gap-3">
            <div className="skeleton w-12 h-12 rounded-full" />
            <div className="flex-1 space-y-1.5">
              <div className="skeleton h-4 w-32" />
              <div className="skeleton h-3 w-48" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (!data?.hits.length) {
    return (
      <div className="card p-8 text-center text-gray-400 text-sm">
        No results found for "{query}"
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-gray-500 mb-3">
        {data.totalHits} result{data.totalHits !== 1 ? 's' : ''}
      </p>
      {data.hits.map((hit) => (
        <Link
          key={`${hit.objectType}-${hit.id}`}
          to={getHitLink(hit)}
          className="card p-4 flex items-center gap-3 hover:shadow-md transition-shadow"
        >
          {hit.avatarUrl ? (
            <img
              src={hit.avatarUrl}
              alt=""
              className="w-12 h-12 rounded-full object-cover"
            />
          ) : (
            <div className="w-12 h-12 bg-primary-500 text-white rounded-full flex items-center justify-center font-semibold">
              {hit.name?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
          <div className="flex-1 min-w-0">
            <p className="font-semibold text-sm text-gray-900">{hit.name}</p>
            {hit.description && (
              <p className="text-xs text-gray-500 truncate">{hit.description}</p>
            )}
            <span className="text-xs text-gray-400 uppercase tracking-wider">
              {hit.objectType}
            </span>
          </div>
        </Link>
      ))}
    </div>
  );
}
