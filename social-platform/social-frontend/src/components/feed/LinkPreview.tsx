import { useQuery } from '@tanstack/react-query';
import api from '../../api/client';

interface LinkPreviewData {
  title?: string;
  description?: string;
  image?: string;
  siteName?: string;
  url: string;
}

interface Props {
  url: string;
}

const YOUTUBE_PATTERNS = [
  /(?:youtube\.com\/watch\?.*v=)([\w-]+)/,
  /(?:youtu\.be\/)([\w-]+)/,
  /(?:youtube\.com\/embed\/)([\w-]+)/,
];

function extractYouTubeId(url: string): string | null {
  for (const pattern of YOUTUBE_PATTERNS) {
    const match = url.match(pattern);
    if (match?.[1]) return match[1];
  }
  return null;
}

function getDomain(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

export default function LinkPreview({ url }: Props) {
  const youtubeId = extractYouTubeId(url);

  const { data, isLoading, isError } = useQuery<LinkPreviewData>({
    queryKey: ['link-preview', url],
    queryFn: async () => {
      const { data } = await api.get('/link-preview', {
        params: { url },
      });
      return data;
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
    enabled: !youtubeId,
  });

  // YouTube embed
  if (youtubeId) {
    return (
      <div className="mt-3">
        <iframe
          src={`https://www.youtube.com/embed/${youtubeId}`}
          allowFullScreen
          className="w-full aspect-video rounded-lg"
          title="YouTube video"
        />
      </div>
    );
  }

  // Loading skeleton
  if (isLoading) {
    return (
      <div className="mt-3 border border-gray-200 rounded-lg overflow-hidden animate-pulse">
        <div className="flex">
          <div className="w-32 h-24 bg-gray-200 shrink-0" />
          <div className="p-3 flex-1 space-y-2">
            <div className="h-4 bg-gray-200 rounded w-3/4" />
            <div className="h-3 bg-gray-200 rounded w-full" />
            <div className="h-3 bg-gray-200 rounded w-1/3" />
          </div>
        </div>
      </div>
    );
  }

  // Don't show anything on error or empty data
  if (isError || !data || (!data.title && !data.description)) {
    return null;
  }

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="mt-3 block border border-gray-200 rounded-lg overflow-hidden hover:bg-gray-50 transition-colors"
    >
      <div className={data.image ? 'sm:flex' : ''}>
        {data.image && (
          <div className="sm:w-40 sm:shrink-0">
            <img
              src={data.image}
              alt=""
              className="w-full h-40 sm:h-full object-cover"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
              }}
            />
          </div>
        )}
        <div className="p-3 min-w-0 flex-1">
          {data.title && (
            <p className="font-semibold text-sm text-gray-900 line-clamp-1">
              {data.title}
            </p>
          )}
          {data.description && (
            <p className="text-xs text-gray-500 mt-1 line-clamp-2">
              {data.description}
            </p>
          )}
          <p className="text-xs text-gray-400 mt-1.5">
            {data.siteName || getDomain(url)}
          </p>
        </div>
      </div>
    </a>
  );
}
