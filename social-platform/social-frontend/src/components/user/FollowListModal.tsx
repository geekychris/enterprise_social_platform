import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import api from '../../api/client';
import type { AuthorDto } from '../../api/types';

interface Props {
  userId: number | string;
  type: 'followers' | 'following';
  onClose: () => void;
}

export default function FollowListModal({ userId, type, onClose }: Props) {
  const { data: users, isLoading } = useQuery<AuthorDto[]>({
    queryKey: ['follow-list', userId, type],
    queryFn: async () => {
      const { data } = await api.get(`/users/${userId}/${type}`);
      return data;
    },
  });

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div
        className="bg-white rounded-xl shadow-2xl w-full max-w-md max-h-[70vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-900 capitalize">{type}</h2>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 text-gray-500"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* List */}
        <div className="overflow-y-auto flex-1">
          {isLoading && (
            <div className="p-4 space-y-3">
              {[1, 2, 3, 4, 5].map((i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="skeleton w-10 h-10 rounded-full" />
                  <div className="skeleton h-4 w-32" />
                </div>
              ))}
            </div>
          )}

          {users && users.length === 0 && (
            <div className="p-8 text-center text-gray-400 text-sm">
              No {type} yet
            </div>
          )}

          {users && users.length > 0 && (
            <div className="divide-y divide-gray-50">
              {users.map((user) => (
                <Link
                  key={user.id}
                  to={`/profile/${user.id}`}
                  onClick={onClose}
                  className="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 transition-colors"
                >
                  {user.avatarUrl ? (
                    <img src={user.avatarUrl} alt="" className="w-10 h-10 rounded-full object-cover" />
                  ) : (
                    <div className="w-10 h-10 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold">
                      {user.displayName?.[0]?.toUpperCase() ?? '?'}
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="font-medium text-sm text-gray-900 truncate">
                      {user.displayName}
                    </div>
                    <div className="text-xs text-gray-500">@{user.username}</div>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
