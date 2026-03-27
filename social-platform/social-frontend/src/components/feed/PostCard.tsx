import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { PostDto } from '../../api/types';
import { useAuth } from '../../hooks/useAuth';
import ReactionBar from './ReactionBar';
import RichContent from './RichContent';
import AttachmentViewer from '../media/AttachmentViewer';
import LinkPreview from './LinkPreview';
import PollDisplay from '../poll/PollDisplay';
import PostDetail from '../post/PostDetail';
import { formatRelativeTime } from '../../utils';

function getTargetRoute(targetType: string): string {
  switch (targetType) {
    case 'GROUP_FEED': return 'group';
    case 'PAGE_FEED': return 'page';
    case 'TEAM_FEED': return 'team';
    default: return '';
  }
}

function getTargetApiPath(targetType: string): string {
  switch (targetType) {
    case 'GROUP_FEED': return 'groups';
    case 'PAGE_FEED': return 'pages';
    case 'TEAM_FEED': return 'teams';
    default: return '';
  }
}

function getTargetLabel(targetType: string): string {
  switch (targetType) {
    case 'GROUP_FEED': return 'group';
    case 'PAGE_FEED': return 'page';
    case 'TEAM_FEED': return 'team';
    case 'PROJECT_FEED': return 'project';
    default: return '';
  }
}

function useTargetName(targetType: string | null, targetId: number | null) {
  const apiPath = targetType ? getTargetApiPath(targetType) : '';
  return useQuery<{ name?: string }>({
    queryKey: ['target', targetType, targetId],
    queryFn: async () => {
      const { data } = await api.get(`/${apiPath}/${targetId}`);
      return data;
    },
    enabled: !!targetType && !!targetId && !!apiPath,
    staleTime: 5 * 60 * 1000,
  });
}

interface Props {
  post: PostDto;
  pinned?: boolean;
  onPin?: (postId: number) => void;
  onUnpin?: () => void;
  canPin?: boolean;
  defaultExpanded?: boolean;
}

export default function PostCard({ post, pinned, onPin, onUnpin, canPin, defaultExpanded }: Props) {
  const [showComments, setShowComments] = useState(defaultExpanded ?? false);
  const [showMenu, setShowMenu] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editContent, setEditContent] = useState(post.content);
  const { userId } = useAuth();
  const { author } = post;
  const { data: target } = useTargetName(post.targetType, post.targetId);
  const queryClient = useQueryClient();

  const targetRoute = post.targetType ? getTargetRoute(post.targetType) : '';
  const targetLabel = post.targetType ? getTargetLabel(post.targetType) : '';

  const isOwnPost = String(author.id) === String(userId);

  const editMutation = useMutation({
    mutationFn: () => api.put(`/posts/${post.id}`, { content: editContent }),
    onSuccess: () => {
      setEditing(false);
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['group-posts'] });
      queryClient.invalidateQueries({ queryKey: ['page-posts'] });
      queryClient.invalidateQueries({ queryKey: ['pinned-post'] });
    },
  });

  return (
    <div className="card group">
      {/* Pinned badge */}
      {pinned && (
        <div className="px-4 pt-3 pb-1 flex items-center gap-2 text-xs text-amber-600 font-medium">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
          </svg>
          Pinned
          {onUnpin && (
            <button
              onClick={onUnpin}
              className="ml-auto text-xs text-gray-400 hover:text-red-500 transition-colors"
            >
              Unpin
            </button>
          )}
        </div>
      )}

      {/* Recommended badge */}
      {post.recommended && (
        <div className="px-4 pt-3 pb-1 flex items-center gap-2 text-xs text-primary-600 font-medium">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
          </svg>
          Suggested for you
        </div>
      )}

      {/* Author header */}
      <div className="flex items-center gap-3 p-4 pb-2">
        <Link to={`/profile/${author.id}`}>
          {author.avatarUrl ? (
            <img
              src={author.avatarUrl}
              alt=""
              className="w-10 h-10 rounded-full object-cover"
            />
          ) : (
            <div className="w-10 h-10 bg-primary-500 text-white rounded-full flex items-center justify-center text-sm font-semibold">
              {author.displayName?.[0]?.toUpperCase() ??
                author.username?.[0]?.toUpperCase() ??
                '?'}
            </div>
          )}
        </Link>
        <div className="flex-1 min-w-0">
          <div className="flex items-center flex-wrap gap-x-1">
            <Link
              to={`/profile/${author.id}`}
              className="font-semibold text-sm text-gray-900 hover:underline"
            >
              {author.displayName || author.username}
            </Link>
            {post.targetType && post.targetId && targetRoute && target?.name && (
              <>
                <svg className="w-3 h-3 text-gray-400 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                </svg>
                <Link
                  to={`/${targetRoute}/${post.targetId}`}
                  className="font-semibold text-sm text-gray-700 hover:underline"
                >
                  {target.name}
                </Link>
              </>
            )}
          </div>
          <p className="text-xs text-gray-400">
            {formatRelativeTime(post.createdAt)}
            {post.targetType && targetLabel && (
              <span className="ml-1 text-gray-300">
                &middot; {targetLabel}
              </span>
            )}
            {post.visibility !== 'PUBLIC' && (
              <span className="ml-1 text-gray-300">
                {' '}
                &middot; {post.visibility}
              </span>
            )}
          </p>
        </div>

        {/* Actions menu (own posts or pin capability) */}
        {(isOwnPost || canPin) && (
          <div className="relative">
            <button
              onClick={() => setShowMenu(!showMenu)}
              className="p-1.5 rounded-full text-gray-400 hover:text-gray-600 hover:bg-gray-100 opacity-0 group-hover:opacity-100 transition-all"
              style={{ opacity: showMenu ? 1 : undefined }}
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 5v.01M12 12v.01M12 19v.01" />
              </svg>
            </button>
            {showMenu && (
              <div className="absolute right-0 top-8 bg-white shadow-lg rounded-lg border border-gray-200 py-1 z-10 min-w-[120px]">
                {isOwnPost && (
                  <button
                    onClick={() => {
                      setEditing(true);
                      setEditContent(post.content);
                      setShowMenu(false);
                    }}
                    className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center gap-2"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    Edit
                  </button>
                )}
                {canPin && !pinned && onPin && (
                  <button
                    onClick={() => {
                      onPin(post.id);
                      setShowMenu(false);
                    }}
                    className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 flex items-center gap-2"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z" />
                    </svg>
                    Pin
                  </button>
                )}
                <button
                  onClick={() => setShowMenu(false)}
                  className="w-full text-left px-4 py-2 text-sm text-gray-400 hover:bg-gray-50"
                >
                  Cancel
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="px-4 pb-3">
        {editing ? (
          <div className="space-y-2">
            <textarea
              value={editContent}
              onChange={(e) => setEditContent(e.target.value)}
              className="input-field resize-none w-full"
              rows={4}
            />
            <div className="flex gap-2">
              <button
                onClick={() => editMutation.mutate()}
                disabled={editMutation.isPending || !editContent.trim()}
                className="btn-primary text-sm px-4"
              >
                {editMutation.isPending ? 'Saving...' : 'Save'}
              </button>
              <button
                onClick={() => {
                  setEditing(false);
                  setEditContent(post.content);
                }}
                className="text-sm text-gray-500 hover:text-gray-700 px-4 py-1.5 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        ) : (() => {
          const hasAttachments = post.attachments && post.attachments.length > 0;
          const urlRegex = /https?:\/\/[^\s<]+/g;
          const urls = post.content?.match(urlRegex) ?? [];
          const previewUrl = !hasAttachments ? urls[0] : undefined;
          return (
            <>
              <RichContent
                content={post.content}
                className="text-sm text-gray-800 leading-relaxed"
                suppressUrls={previewUrl ? [previewUrl] : undefined}
              />
              {previewUrl && (
                <div className="mt-3">
                  <LinkPreview url={previewUrl} />
                </div>
              )}
            </>
          );
        })()}
      </div>

      {/* Attachments */}
      {post.attachments?.length > 0 && (
        <div className="px-4 pb-3">
          <AttachmentViewer attachments={post.attachments} />
        </div>
      )}

      {/* Poll */}
      {post.poll && (
        <div className="px-4 pb-2">
          <PollDisplay poll={post.poll} postId={post.id} />
        </div>
      )}

      {/* Divider + stats */}
      <div className="px-4 py-2 border-t border-gray-100 flex items-center justify-between">
        <ReactionBar
          targetId={post.id}
          reactionCounts={post.reactionCounts ?? {}}
          currentUserReaction={post.currentUserReaction}
        />
        <button
          onClick={() => setShowComments(!showComments)}
          className="text-sm text-gray-500 hover:text-gray-700 hover:bg-gray-100 px-3 py-1.5 rounded-lg transition-colors"
        >
          {post.commentCount ?? 0} comment{post.commentCount !== 1 ? 's' : ''}
        </button>
      </div>

      {/* Comments section */}
      {showComments && (
        <div className="border-t border-gray-100">
          <PostDetail postId={post.id} />
        </div>
      )}
    </div>
  );
}
