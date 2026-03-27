import { useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../api/client';
import type { PageDto, PostDto } from '../api/types';
import { useAuth } from '../hooks/useAuth';
import PostCard from '../components/feed/PostCard';
import CreatePostForm from '../components/feed/CreatePostForm';
import RichContent from '../components/feed/RichContent';
import MarkdownContent from '../components/feed/MarkdownContent';
import AiAssistant from '../components/ai/AiAssistant';

export default function PagePage() {
  const { id } = useParams<{ id: string }>();
  const pageId = id!;
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

  const { data: page, isLoading } = useQuery<PageDto>({
    queryKey: ['page', pageId],
    queryFn: async () => {
      const { data } = await api.get(`/pages/${pageId}`);
      return data;
    },
    enabled: !!pageId,
  });

  const { data: following } = useQuery<boolean>({
    queryKey: ['page-following', pageId],
    queryFn: async () => {
      const { data } = await api.get(`/pages/${pageId}/following`);
      return data;
    },
    enabled: !!pageId,
  });

  const { data: posts } = useQuery<PostDto[]>({
    queryKey: ['page-posts', pageId],
    queryFn: async () => {
      const { data } = await api.get(`/pages/${pageId}/posts`);
      return data;
    },
    enabled: !!pageId,
  });

  // Fetch pinned post
  const { data: pinnedPost } = useQuery<PostDto>({
    queryKey: ['pinned-post', 'page', pageId, page?.pinnedPostId],
    queryFn: async () => {
      const { data } = await api.get(`/posts/${page!.pinnedPostId}`);
      return data;
    },
    enabled: !!page?.pinnedPostId,
  });

  const isOwner = String(page?.ownerId) === String(userId);

  const followPage = useMutation({
    mutationFn: () => api.post(`/pages/${pageId}/follow`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['page-following', pageId] });
      queryClient.invalidateQueries({ queryKey: ['page', pageId] });
    },
  });

  const unfollowPage = useMutation({
    mutationFn: () => api.delete(`/pages/${pageId}/unfollow`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['page-following', pageId] });
      queryClient.invalidateQueries({ queryKey: ['page', pageId] });
    },
  });

  const pinPost = useMutation({
    mutationFn: (postId: number) => api.post(`/pages/${pageId}/pin/${postId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['page', pageId] });
      queryClient.invalidateQueries({ queryKey: ['pinned-post', 'page', pageId] });
    },
  });

  const unpinPost = useMutation({
    mutationFn: () => api.post(`/pages/${pageId}/pin/0`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['page', pageId] });
      queryClient.invalidateQueries({ queryKey: ['pinned-post', 'page', pageId] });
    },
  });

  const updatePage = useMutation({
    mutationFn: (payload: { name: string; description: string; avatarUrl: string | null; coverUrl: string | null }) =>
      api.put(`/pages/${pageId}`, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['page', pageId] });
      queryClient.invalidateQueries({ queryKey: ['my-pages'] });
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
    if (!page) return;
    setEditName(page.name);
    setEditDesc(page.description ?? '');
    setEditAvatarUrl(page.avatarUrl ?? null);
    setEditCoverUrl(page.coverUrl ?? null);
    setEditing(true);
  };

  const handleSaveEdit = () => {
    updatePage.mutate({
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

  if (!page) {
    return (
      <div className="card p-8 text-center text-gray-400">Page not found</div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Page header card */}
      <div className="card overflow-hidden">
        {page.coverUrl ? (
          <img
            src={page.coverUrl}
            alt=""
            className="h-32 w-full object-cover"
          />
        ) : (
          <div className="h-32 bg-gradient-to-r from-violet-600 to-violet-400" />
        )}
        <div className="p-4 flex items-start gap-4 -mt-8">
          {page.avatarUrl ? (
            <img
              src={page.avatarUrl}
              alt=""
              className="w-16 h-16 rounded-xl object-cover border-4 border-white shadow"
            />
          ) : (
            <div className="w-16 h-16 bg-violet-500 text-white rounded-xl flex items-center justify-center text-xl font-bold border-4 border-white shadow">
              {page.name?.[0]?.toUpperCase() ?? 'P'}
            </div>
          )}
          <div className="flex-1 pt-6">
            <div className="flex items-start justify-between">
              <div>
                <h1 className="text-xl font-bold text-gray-900">
                  {page.name}
                </h1>
                {page.description && (
                  <MarkdownContent
                    content={page.description}
                    className="text-sm text-gray-500 mt-0.5"
                  />
                )}
                <p className="text-xs text-gray-400 mt-1">
                  {page.followerCount} follower
                  {page.followerCount !== 1 ? 's' : ''} &middot;{' '}
                  <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
                    {page.visibility}
                  </span>
                </p>
              </div>

              <div className="flex items-center gap-2">
                {/* Edit button for owner */}
                {isOwner && (
                  <button
                    onClick={openEdit}
                    className="text-sm border border-gray-300 text-gray-700 px-3 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors"
                  >
                    Edit
                  </button>
                )}

                {/* Follow/Unfollow button */}
                {!isOwner && (
                  <div>
                    {following ? (
                      <button
                        onClick={() => unfollowPage.mutate()}
                        disabled={unfollowPage.isPending}
                        className="text-sm border border-gray-300 text-gray-700 px-4 py-1.5 rounded-lg font-medium hover:bg-gray-50 transition-colors"
                      >
                        {unfollowPage.isPending ? 'Unfollowing...' : 'Unfollow'}
                      </button>
                    ) : (
                      <button
                        onClick={() => followPage.mutate()}
                        disabled={followPage.isPending}
                        className="btn-primary text-sm"
                      >
                        {followPage.isPending ? 'Following...' : 'Follow'}
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Edit form */}
      {editing && (
        <div className="card p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-900">Edit Page</h3>
          <input
            type="text"
            value={editName}
            onChange={(e) => setEditName(e.target.value)}
            placeholder="Page name"
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
                <div className="w-12 h-12 bg-violet-500 text-white rounded-xl flex items-center justify-center text-lg font-bold">
                  {editName?.[0]?.toUpperCase() ?? 'P'}
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
              disabled={!editName.trim() || updatePage.isPending || uploadingAvatar || uploadingCover}
              className="btn-primary text-sm"
            >
              {updatePage.isPending ? 'Saving...' : 'Save'}
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

      {/* AI Assistant */}
      <AiAssistant context="page" contextId={pageId} />

      {/* Create post (if owner) */}
      {isOwner && <CreatePostForm defaultTargetType="PAGE_FEED" defaultTargetId={pageId} />}

      {/* Pinned post */}
      {pinnedPost && (
        <PostCard
          post={pinnedPost}
          pinned
          onUnpin={isOwner ? () => unpinPost.mutate() : undefined}
          canPin={false}
        />
      )}

      {/* Page feed */}
      <h2 className="text-lg font-semibold text-gray-900">Page Feed</h2>
      {posts && posts.length > 0 ? (
        <div className="space-y-4">
          {posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              canPin={isOwner}
              onPin={(postId) => pinPost.mutate(postId)}
            />
          ))}
        </div>
      ) : (
        <div className="card p-8 text-center">
          <p className="text-gray-400 text-sm">
            No posts on this page yet.
          </p>
        </div>
      )}
    </div>
  );
}
