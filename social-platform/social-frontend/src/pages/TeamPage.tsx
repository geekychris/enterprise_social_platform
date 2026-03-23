import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import api from '../api/client';
import type { TeamDto } from '../api/types';
import FeedView from '../components/feed/FeedView';

export default function TeamPage() {
  const { id } = useParams<{ id: string }>();
  const teamId = id!;

  const { data: team, isLoading } = useQuery<TeamDto>({
    queryKey: ['team', teamId],
    queryFn: async () => {
      const { data } = await api.get(`/teams/${teamId}`);
      return data;
    },
    enabled: !!teamId,
  });

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="card overflow-hidden">
          <div className="skeleton h-32 rounded-none" />
          <div className="p-4 space-y-2">
            <div className="skeleton h-6 w-40" />
            <div className="skeleton h-3 w-64" />
          </div>
        </div>
      </div>
    );
  }

  if (!team) {
    return (
      <div className="card p-8 text-center text-gray-400">Team not found</div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="card overflow-hidden">
        {/* Cover */}
        <div className="h-32 bg-gradient-to-r from-primary-600 to-primary-400" />

        <div className="p-4 flex items-center gap-4 -mt-8">
          {team.avatarUrl ? (
            <img
              src={team.avatarUrl}
              alt=""
              className="w-16 h-16 rounded-xl object-cover border-4 border-white shadow"
            />
          ) : (
            <div className="w-16 h-16 bg-primary-500 text-white rounded-xl flex items-center justify-center text-xl font-bold border-4 border-white shadow">
              {team.name?.[0]?.toUpperCase() ?? 'T'}
            </div>
          )}
          <div>
            <h1 className="text-xl font-bold text-gray-900">{team.name}</h1>
            {team.description && (
              <p className="text-sm text-gray-500">{team.description}</p>
            )}
            <p className="text-xs text-gray-400 mt-1">
              {team.memberCount} member{team.memberCount !== 1 ? 's' : ''}{' '}
              &middot; {team.visibility}
            </p>
          </div>
        </div>
      </div>

      <h2 className="text-lg font-semibold text-gray-900">Team Feed</h2>
      <FeedView />
    </div>
  );
}
