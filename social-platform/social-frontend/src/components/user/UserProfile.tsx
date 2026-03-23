import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { UserDto } from '../../api/types';
import { useAuth } from '../../hooks/useAuth';
import FollowListModal from './FollowListModal';

interface Props {
  userId: number | string;
}

export default function UserProfile({ userId }: Props) {
  const { userId: currentUserId } = useAuth();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const isOwnProfile = String(currentUserId) === String(userId);
  const [showList, setShowList] = useState<'followers' | 'following' | null>(null);

  const { data: user, isLoading } = useQuery<UserDto>({
    queryKey: ['user', userId],
    queryFn: async () => {
      const { data } = await api.get(`/users/${userId}`);
      return data;
    },
  });

  const follow = useMutation({
    mutationFn: () => api.post(`/follow/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', userId] }),
  });

  const unfollow = useMutation({
    mutationFn: () => api.delete(`/follow/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['user', userId] }),
  });

  if (isLoading) {
    return (
      <div className="card overflow-hidden">
        <div className="skeleton h-32 rounded-none" />
        <div className="p-4 flex items-end gap-4 -mt-8">
          <div className="skeleton w-20 h-20 rounded-full border-4 border-white" />
          <div className="space-y-2 flex-1 pb-1">
            <div className="skeleton h-5 w-40" />
            <div className="skeleton h-3 w-24" />
          </div>
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="card p-8 text-center text-gray-400">User not found</div>
    );
  }

  return (
    <>
      <div className="card overflow-hidden">
        {/* Cover */}
        <div className="h-32 bg-gradient-to-r from-primary-500 to-primary-600" />

        {/* Profile info */}
        <div className="p-4 flex items-end gap-4 -mt-10">
          {user.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              className="w-20 h-20 rounded-full object-cover border-4 border-white shadow"
            />
          ) : (
            <div className="w-20 h-20 bg-primary-500 text-white rounded-full flex items-center justify-center text-2xl font-bold border-4 border-white shadow">
              {user.displayName?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
          <div className="flex-1 pb-1">
            <h1 className="text-xl font-bold text-gray-900">
              {user.displayName}
            </h1>
            <p className="text-sm text-gray-500">@{user.username}</p>
          </div>
          {!isOwnProfile && (
            <div className="pb-1 flex items-center gap-2">
              <button
                onClick={() => navigate(`/messages/${userId}`)}
                className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors flex items-center gap-1.5"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
                Message
              </button>
              <button
                onClick={() => follow.mutate()}
                disabled={follow.isPending}
                className="btn-primary text-sm"
              >
                Follow
              </button>
            </div>
          )}
        </div>

        {/* Bio + stats */}
        <div className="px-4 pb-4 space-y-3">
          {user.bio && (
            <p className="text-sm text-gray-700">{user.bio}</p>
          )}
          <div className="flex gap-6 text-sm">
            <button
              onClick={() => setShowList('followers')}
              className="hover:underline"
            >
              <span className="font-semibold text-gray-900">
                {user.followerCount}
              </span>{' '}
              <span className="text-gray-500">followers</span>
            </button>
            <button
              onClick={() => setShowList('following')}
              className="hover:underline"
            >
              <span className="font-semibold text-gray-900">
                {user.followingCount}
              </span>{' '}
              <span className="text-gray-500">following</span>
            </button>
          </div>
        </div>
      </div>

      {showList && (
        <FollowListModal
          userId={userId}
          type={showList}
          onClose={() => setShowList(null)}
        />
      )}
    </>
  );
}
