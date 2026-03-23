import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { UserDto } from '../../api/types';

interface Props {
  user: Pick<UserDto, 'id' | 'username' | 'displayName' | 'avatarUrl' | 'bio'>;
  showFollow?: boolean;
}

export default function UserCard({ user, showFollow = true }: Props) {
  const queryClient = useQueryClient();

  const follow = useMutation({
    mutationFn: () => api.post(`/follow/${user.id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', user.id] }),
  });

  return (
    <div className="card p-4 flex items-center gap-3">
      <Link to={`/profile/${user.id}`}>
        {user.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt=""
            className="w-12 h-12 rounded-full object-cover"
          />
        ) : (
          <div className="w-12 h-12 bg-primary-500 text-white rounded-full flex items-center justify-center font-semibold">
            {user.displayName?.[0]?.toUpperCase() ?? user.username?.[0]?.toUpperCase() ?? '?'}
          </div>
        )}
      </Link>
      <div className="flex-1 min-w-0">
        <Link
          to={`/profile/${user.id}`}
          className="font-semibold text-sm text-gray-900 hover:underline"
        >
          {user.displayName || user.username}
        </Link>
        {user.bio && (
          <p className="text-xs text-gray-500 truncate">{user.bio}</p>
        )}
      </div>
      {showFollow && (
        <button
          onClick={() => follow.mutate()}
          disabled={follow.isPending}
          className="btn-primary text-xs px-3 py-1.5"
        >
          Follow
        </button>
      )}
    </div>
  );
}
