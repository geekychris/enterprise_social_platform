import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { GroupDto, PageDto, SearchResultDto, FriendRequestDto } from '../../api/types';

export default function RightPanel() {
  const queryClient = useQueryClient();

  const { data: myGroups } = useQuery<GroupDto[]>({
    queryKey: ['my-groups'],
    queryFn: async () => {
      const { data } = await api.get('/groups/mine');
      return data;
    },
  });

  const { data: allGroups } = useQuery<GroupDto[]>({
    queryKey: ['all-groups'],
    queryFn: async () => {
      const { data } = await api.get('/groups/search', { params: { q: '' } });
      return data;
    },
  });

  const { data: allPages } = useQuery<PageDto[]>({
    queryKey: ['all-pages'],
    queryFn: async () => {
      const { data } = await api.get('/pages/search', { params: { q: '' } });
      return data;
    },
  });

  const { data: searchResults } = useQuery<SearchResultDto>({
    queryKey: ['suggested-people'],
    queryFn: async () => {
      const { data } = await api.get('/search', {
        params: { q: '', type: 'USER' },
      });
      return data;
    },
  });

  const { data: receivedRequests } = useQuery<FriendRequestDto[]>({
    queryKey: ['friend-requests-received'],
    queryFn: async () => {
      const { data } = await api.get('/friend-requests/received');
      return data;
    },
  });

  const myGroupIds = new Set(myGroups?.map((g) => String(g.id)) ?? []);
  const suggestedGroups =
    allGroups?.filter((g) => !myGroupIds.has(String(g.id))).slice(0, 5) ?? [];

  const suggestedPages = allPages?.slice(0, 5) ?? [];
  const suggestedPeople = searchResults?.hits?.slice(0, 5) ?? [];

  return (
    <aside className="fixed right-0 top-14 bottom-0 w-72 bg-white border-l border-gray-100 pt-4 hidden xl:block z-20 overflow-y-auto">
      {/* Friend Requests */}
      {receivedRequests && receivedRequests.length > 0 && (
        <div className="px-4 mb-6">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Friend Requests
          </h3>
          <div className="space-y-2">
            {receivedRequests.map((request) => (
              <FriendRequestCard key={request.id} request={request} />
            ))}
          </div>
        </div>
      )}

      {/* Suggested Groups */}
      {suggestedGroups.length > 0 && (
        <div className="px-4 mb-6">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Suggested Groups
          </h3>
          <div className="space-y-1">
            {suggestedGroups.map((group) => (
              <Link
                key={group.id}
                to={`/group/${group.id}`}
                className="flex items-center gap-2.5 px-2 py-2 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <div className="w-8 h-8 bg-emerald-500 text-white rounded-lg flex items-center justify-center text-xs font-bold shrink-0">
                  {group.name?.[0]?.toUpperCase() ?? 'G'}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-gray-900 truncate">
                    {group.name}
                  </p>
                  <p className="text-[10px] text-gray-400">
                    {group.memberCount} member
                    {group.memberCount !== 1 ? 's' : ''}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Suggested Pages */}
      {suggestedPages.length > 0 && (
        <div className="px-4 mb-6">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            Suggested Pages
          </h3>
          <div className="space-y-1">
            {suggestedPages.map((page) => (
              <Link
                key={page.id}
                to={`/page/${page.id}`}
                className="flex items-center gap-2.5 px-2 py-2 rounded-lg hover:bg-gray-50 transition-colors"
              >
                {page.avatarUrl ? (
                  <img
                    src={page.avatarUrl}
                    alt=""
                    className="w-8 h-8 rounded-lg object-cover shrink-0"
                  />
                ) : (
                  <div className="w-8 h-8 bg-violet-500 text-white rounded-lg flex items-center justify-center text-xs font-bold shrink-0">
                    {page.name?.[0]?.toUpperCase() ?? 'P'}
                  </div>
                )}
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-gray-900 truncate">
                    {page.name}
                  </p>
                  <p className="text-[10px] text-gray-400">
                    {page.followerCount} follower
                    {page.followerCount !== 1 ? 's' : ''}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* People You May Know */}
      {suggestedPeople.length > 0 && (
        <div className="px-4 mb-6">
          <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
            People You May Know
          </h3>
          <div className="space-y-1">
            {suggestedPeople.map((person) => (
              <SuggestedPerson key={person.id} person={person} />
            ))}
          </div>
        </div>
      )}
    </aside>
  );
}

function FriendRequestCard({ request }: { request: FriendRequestDto }) {
  const queryClient = useQueryClient();

  const accept = useMutation({
    mutationFn: () => api.post(`/friend-requests/${request.id}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests-received'] });
      queryClient.invalidateQueries({ queryKey: ['friends'] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });

  const reject = useMutation({
    mutationFn: () => api.post(`/friend-requests/${request.id}/reject`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['friend-requests-received'] });
    },
  });

  return (
    <div className="flex items-start gap-2.5 px-2 py-2 rounded-lg bg-gray-50">
      <Link to={`/profile/${request.senderId}`} className="shrink-0">
        {request.senderAvatarUrl ? (
          <img
            src={request.senderAvatarUrl}
            alt=""
            className="w-9 h-9 rounded-full object-cover"
          />
        ) : (
          <div className="w-9 h-9 bg-primary-500 text-white rounded-full flex items-center justify-center text-xs font-semibold">
            {request.senderDisplayName?.[0]?.toUpperCase() ?? '?'}
          </div>
        )}
      </Link>
      <div className="min-w-0 flex-1">
        <Link
          to={`/profile/${request.senderId}`}
          className="text-sm font-medium text-gray-900 truncate block hover:underline"
        >
          {request.senderDisplayName}
        </Link>
        <div className="flex gap-1.5 mt-1.5">
          <button
            onClick={() => accept.mutate()}
            disabled={accept.isPending || reject.isPending}
            className="flex-1 px-2 py-1 bg-primary-500 text-white text-xs font-medium rounded-md hover:bg-primary-600 disabled:opacity-50 transition-colors"
          >
            Accept
          </button>
          <button
            onClick={() => reject.mutate()}
            disabled={accept.isPending || reject.isPending}
            className="flex-1 px-2 py-1 bg-gray-200 text-gray-700 text-xs font-medium rounded-md hover:bg-gray-300 disabled:opacity-50 transition-colors"
          >
            Decline
          </button>
        </div>
      </div>
    </div>
  );
}

function SuggestedPerson({ person }: { person: { id: number; name: string; description: string | null; avatarUrl: string | null } }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState(false);

  const { data: serverStatus } = useQuery<{ status: string }>({
    queryKey: ['friend-status', person.id],
    queryFn: async () => {
      const { data } = await api.get(`/friend-requests/status/${person.id}`);
      return data;
    },
    staleTime: 60000,
  });

  const sendRequest = useMutation({
    mutationFn: () => api.post(`/friend-requests/${person.id}`),
    onSuccess: () => {
      setError(false);
      queryClient.invalidateQueries({ queryKey: ['friend-status', person.id] });
      queryClient.invalidateQueries({ queryKey: ['friend-requests-sent'] });
    },
    onError: () => {
      setError(true);
    },
  });

  const status = serverStatus?.status ?? 'NONE';
  const alreadySent = status === 'REQUEST_SENT' || status === 'FRIENDS' || status === 'REQUEST_RECEIVED';
  const buttonDisabled = sendRequest.isPending || alreadySent;
  const buttonText = error
    ? 'Failed - Retry'
    : status === 'REQUEST_SENT'
      ? 'Requested'
      : status === 'FRIENDS'
        ? 'Friends'
        : status === 'REQUEST_RECEIVED'
          ? 'Respond to Request'
          : 'Add Friend';

  return (
    <div className="flex items-center gap-2.5 px-2 py-2 rounded-lg hover:bg-gray-50 transition-colors">
      <Link to={`/profile/${person.id}`} className="shrink-0">
        {person.avatarUrl ? (
          <img
            src={person.avatarUrl}
            alt=""
            className="w-8 h-8 rounded-full object-cover"
          />
        ) : (
          <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-xs font-semibold">
            {person.name?.[0]?.toUpperCase() ?? '?'}
          </div>
        )}
      </Link>
      <div className="min-w-0 flex-1">
        <Link to={`/profile/${person.id}`} className="text-sm font-medium text-gray-900 truncate block hover:underline">
          {person.name}
        </Link>
        {person.description && (
          <p className="text-[10px] text-gray-400 truncate">
            {person.description}
          </p>
        )}
        <button
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setError(false);
            sendRequest.mutate();
          }}
          disabled={buttonDisabled}
          className={`mt-1 w-full px-2 py-1 text-xs font-medium rounded-md transition-colors ${
            error
              ? 'bg-red-50 text-red-600 hover:bg-red-100'
              : buttonDisabled
                ? 'bg-gray-100 text-gray-400 cursor-default'
                : 'bg-primary-50 text-primary-600 hover:bg-primary-100'
          }`}
        >
          {sendRequest.isPending ? 'Sending...' : buttonText}
        </button>
      </div>
    </div>
  );
}
