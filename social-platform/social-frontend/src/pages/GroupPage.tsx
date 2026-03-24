import { useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../api/client';
import type { GroupDto, MembershipDto, PostDto } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import PostCard from '../components/feed/PostCard';
import CreatePostForm from '../components/feed/CreatePostForm';
import RichContent from '../components/feed/RichContent';

export default function GroupPage() {
  const { id } = useParams<{ id: string }>();
  const groupId = id!;
  const { userId } = useAuth();
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editAvatarUrl, setEditAvatarUrl] = useState<string | null>(null);
  const [editCoverUrl, setEditCoverUrl] = useState<string | null>(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [uploadingCover, setUploadingCover] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);
  const coverInputRef = useRef<HTMLInputElement>(null);

  const { data: group, isLoading } = useQuery<GroupDto>({
    queryKey: ['group', groupId],
    queryFn: async () => {
      const { data } = await api.get(`/groups/${groupId}`);
      return data;
    },
    enabled: !!groupId,
  });

  const { data: members } = useQuery<MembershipDto[]>({
    queryKey: ['group-members', groupId],
    queryFn: async () => {
      const { data } = await api.get(`/groups/${groupId}/members`);
      return data;
    },
    enabled: !!groupId,
  });

  const { data: posts } = useQuery<PostDto[]>({
    queryKey: ['group-posts', groupId],
    queryFn: async () => {
      const { data } = await api.get(`/groups/${groupId}/posts`);
      return data;
    },
    enabled: !!groupId,
  });

  // Fetch pinned post
  const { data: pinnedPost } = useQuery<PostDto>({
    queryKey: ['pinned-post', 'group', groupId, group?.pinnedPostId],
    queryFn: async () => {
      const { data } = await api.get(`/posts/${group!.pinnedPostId}`);
      return data;
    },
    enabled: !!group?.pinnedPostId,
  });

  // Get current user's membership status
  const { data: myMembership } = useQuery<MembershipDto | null>({
    queryKey: ['group-membership', groupId],
    queryFn: async () => {
      const { data } = await api.get(`/groups/${groupId}/membership`);
      return data || null;
    },
    enabled: !!groupId,
  });

  const isMember = myMembership?.status === 'APPROVED';
  const isPending = myMembership?.status === 'PENDING';
  const isOwnerOrAdmin =
    myMembership?.role === 'OWNER' || myMembership?.role === 'ADMIN';

  // Get pending members (admin/owner only)
  const { data: pendingMembers = [] } = useQuery<MembershipDto[]>({
    queryKey: ['group-pending', groupId],
    queryFn: async () => {
      const { data } = await api.get(`/groups/${groupId}/pending`);
      return data;
    },
    enabled: !!groupId && !!isOwnerOrAdmin,
  });

  const joinGroup = useMutation({
    mutationFn: () => api.post(`/groups/${groupId}/join`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-membership', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
    },
  });

  const leaveGroup = useMutation({
    mutationFn: () => api.delete(`/groups/${groupId}/leave`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-membership', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
    },
  });

  const approveMember = useMutation({
    mutationFn: (memberId: number) =>
      api.post(`/groups/${groupId}/members/${memberId}/approve`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
    },
  });

  const rejectMember = useMutation({
    mutationFn: (memberId: number) =>
      api.post(`/groups/${groupId}/members/${memberId}/reject`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group-members', groupId] });
    },
  });

  const pinPost = useMutation({
    mutationFn: (postId: number) => api.post(`/groups/${groupId}/pin/${postId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['pinned-post', 'group', groupId] });
    },
  });

  const unpinPost = useMutation({
    mutationFn: () => api.post(`/groups/${groupId}/pin/0`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['pinned-post', 'group', groupId] });
    },
  });

  const updateGroup = useMutation({
    mutationFn: (payload: { name: string; description: string; avatarUrl: string | null; coverUrl: string | null }) =>
      api.put(`/groups/${groupId}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupId] });
      queryClient.invalidateQueries({ queryKey: ['my-groups'] });
      setEditing(false);
    },
  });

  const handleUpload = async (
    file: File,
    setUrl: (url: string) => void,
    setUploading: (v: boolean) => void,
  ) => {
    setUploading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const { data } = await api.post('/attachments/upload', form);
      setUrl(data.fileUrl);
    } catch {
      // silently fail
    } finally {
      setUploading(false);
    }
  };

  const openEdit = () => {
    if (!group) return;
    setEditName(group.name);
    setEditDesc(group.description ?? '');
    setEditAvatarUrl(group.avatarUrl ?? null);
    setEditCoverUrl(group.coverUrl ?? null);
    setEditing(true);
  };

  const handleSaveEdit = () => {
    updateGroup.mutate({
      name: editName,
      description: editDesc,
      avatarUrl: editAvatarUrl,
      coverUrl: editCoverUrl,
    });
  };

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

  if (!group) {
    return (
      <div className="card p-8 text-center text-gray-400">Group not found</div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Group header card */}
      <div className="card overflow-hidden">
        {group.coverUrl ? (
          <img
            src={group.coverUrl}
            alt=""
            className="h-32 w-full object-cover"
          />
        ) : (
          <div className="h-32 bg-gradient-to-r from-emerald-600 to-emerald-400" />
        )}
        <div className="p-4 flex items-start gap-4 -mt-8">
          {group.avatarUrl ? (
            <img
              src={group.avatarUrl}
              alt=""
              className="w-16 h-16 rounded-xl object-cover border-4 border-white shadow"
            />
          ) : (
            <div className="w-16 h-16 bg-emerald-500 text-white rounded-xl flex items-center justify-center text-xl font-bold border-4 border-white shadow">
              {group.name?.[0]?.toUpperCase() ?? 'G'}
            </div>
          )}
          <div className="flex-1 pt-6">
            <div className="flex items-start justify-between">
              <div>
                <h1 className="text-xl font-bold text-gray-900">
                  {group.name}
                </h1>
                {group.description && (
                  <RichContent
                    content={group.description}
                    className="text-sm text-gray-500 mt-0.5"
                  />
                )}
                <p className="text-xs text-gray-400 mt-1">
                  {group.memberCount} member
                  {group.memberCount !== 1 ? 's' : ''} &middot;{' '}
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
                    {group.visibility}
                  </span>
                </p>
              </div>

              <div className="flex items-center gap-2">
                {/* Edit button for owner/admin */}
                {isOwnerOrAdmin && (
                  <button
                    onClick={openEdit}
                    className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors"
                  >
                    Edit
                  </button>
                )}

                {/* Join/Leave button */}
                {isMember ? (
                  <button
                    onClick={() => leaveGroup.mutate()}
                    disabled={leaveGroup.isPending}
                    className="text-sm border border-gray-300 text-gray-700 px-4 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors"
                  >
                    {leaveGroup.isPending ? 'Leaving...' : 'Leave'}
                  </button>
                ) : isPending ? (
                  <button
                    disabled
                    className="text-sm border border-yellow-300 text-yellow-700 bg-yellow-50 px-4 py-1.5 rounded-lg font-medium cursor-not-allowed"
                  >
                    Pending Approval
                  </button>
                ) : (
                  <button
                    onClick={() => joinGroup.mutate()}
                    disabled={joinGroup.isPending}
                    className="btn-primary text-sm"
                  >
                    {joinGroup.isPending ? 'Joining...' : 'Join Group'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Edit modal */}
      {editing && (
        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-900">Edit Group</h3>
          <input
            type="text"
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            placeholder="Group name"
            className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-1 focus:ring-primary-500"
          />
          <textarea
            value={editDesc}
            onChange={(e) => setEditDesc(e.target.value)}
            placeholder="Description (supports **bold**, _italic_, and links)"
            rows={3}
            className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
          />

          {/* Avatar upload */}
          <div>
            <label className="text-xs font-medium text-gray-500 block mb-1">Avatar</label>
            <input
              ref={avatarInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) handleUpload(file, setEditAvatarUrl, setUploadingAvatar);
                e.target.value = '';
              }}
            />
            <div className="flex items-center gap-3">
              {editAvatarUrl ? (
                <img src={editAvatarUrl} alt="" className="w-12 h-12 rounded-xl object-cover" />
              ) : (
                <div className="w-12 h-12 bg-emerald-500 text-white rounded-xl flex items-center justify-center text-lg font-bold">
                  {editName?.[0]?.toUpperCase() ?? 'G'}
                </div>
              )}
              <button
                type="button"
                onClick={() => avatarInputRef.current?.click()}
                disabled={uploadingAvatar}
                className="text-xs text-primary-500 hover:text-primary-600"
              >
                {uploadingAvatar ? 'Uploading...' : 'Change avatar'}
              </button>
              {editAvatarUrl && (
                <button
                  type="button"
                  onClick={() => setEditAvatarUrl(null)}
                  className="text-xs text-red-500 hover:text-red-600"
                >
                  Remove
                </button>
              )}
            </div>
          </div>

          {/* Cover upload */}
          <div>
            <label className="text-xs font-medium text-gray-500 block mb-1">Cover image</label>
            <input
              ref={coverInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) handleUpload(file, setEditCoverUrl, setUploadingCover);
                e.target.value = '';
              }}
            />
            {editCoverUrl ? (
              <div className="relative">
                <img src={editCoverUrl} alt="" className="w-full h-24 rounded-lg object-cover" />
                <div className="absolute top-1 right-1 flex gap-1">
                  <button
                    type="button"
                    onClick={() => coverInputRef.current?.click()}
                    disabled={uploadingCover}
                    className="text-xs bg-white/80 text-gray-700 px-2 py-0.5 rounded hover:bg-white"
                  >
                    Change
                  </button>
                  <button
                    type="button"
                    onClick={() => setEditCoverUrl(null)}
                    className="text-xs bg-white/80 text-red-500 px-2 py-0.5 rounded hover:bg-white"
                  >
                    Remove
                  </button>
                </div>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => coverInputRef.current?.click()}
                disabled={uploadingCover}
                className="text-xs text-primary-500 hover:text-primary-600 flex items-center gap-1"
              >
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
                {uploadingCover ? 'Uploading...' : 'Add cover image'}
              </button>
            )}
          </div>

          <div className="flex gap-2 pt-1">
            <button
              onClick={handleSaveEdit}
              disabled={!editName.trim() || updateGroup.isPending || uploadingAvatar || uploadingCover}
              className="btn-primary text-sm"
            >
              {updateGroup.isPending ? 'Saving...' : 'Save'}
            </button>
            <button
              onClick={() => setEditing(false)}
              className="text-sm border border-gray-300 text-gray-700 px-4 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Pending members (admin only) */}
      {isOwnerOrAdmin && pendingMembers.length > 0 && (
        <div className="card p-4">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">
            Pending Members ({pendingMembers.length})
          </h3>
          <div className="space-y-2">
            {pendingMembers.map((m) => (
              <div
                key={m.userId}
                className="flex items-center gap-3 p-2 rounded-lg bg-gray-50"
              >
                {m.userAvatarUrl ? (
                  <img
                    src={m.userAvatarUrl}
                    alt=""
                    className="w-8 h-8 rounded-full object-cover"
                  />
                ) : (
                  <div className="w-8 h-8 bg-primary-500 text-white rounded-full flex items-center justify-center text-xs font-semibold">
                    {m.userName?.[0]?.toUpperCase() ?? '?'}
                  </div>
                )}
                <span className="flex-1 text-sm font-medium text-gray-700">
                  {m.userName}
                </span>
                <button
                  onClick={() => approveMember.mutate(m.userId)}
                  className="text-xs bg-green-500 text-white px-3 py-1 rounded-lg hover:bg-green-600 transition-colors"
                >
                  Approve
                </button>
                <button
                  onClick={() => rejectMember.mutate(m.userId)}
                  className="text-xs bg-red-500 text-white px-3 py-1 rounded-lg hover:bg-red-600 transition-colors"
                >
                  Reject
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Create post (if member) */}
      {isMember && <CreatePostForm defaultTargetType="GROUP_FEED" defaultTargetId={groupId} />}

      {/* Pinned post */}
      {pinnedPost && (
        <PostCard
          post={pinnedPost}
          pinned
          onUnpin={isOwnerOrAdmin ? () => unpinPost.mutate() : undefined}
          canPin={false}
        />
      )}

      {/* Group feed */}
      <h2 className="text-lg font-semibold text-gray-900">Group Feed</h2>
      {posts && posts.length > 0 ? (
        <div className="space-y-4">
          {posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              canPin={isOwnerOrAdmin}
              onPin={(postId) => pinPost.mutate(postId)}
            />
          ))}
        </div>
      ) : (
        <div className="card p-8 text-center">
          <p className="text-gray-400 text-sm">
            No posts in this group yet. Be the first to share something!
          </p>
        </div>
      )}
    </div>
  );
}
