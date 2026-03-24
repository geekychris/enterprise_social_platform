import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { CommentDto } from '../../api/types';
import { useAuth } from '../../hooks/useAuth';
import ReactionBar from '../feed/ReactionBar';
import CommentForm from './CommentForm';
import { formatRelativeTime } from '../../utils';

interface Props {
  comment: CommentDto;
  maxDepth?: number;
}

export default function CommentThread({ comment, maxDepth = 2 }: Props) {
  const [showReplyForm, setShowReplyForm] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editContent, setEditContent] = useState(comment.content);
  const { userId } = useAuth();
  const { author } = comment;
  const queryClient = useQueryClient();

  const isOwnComment = String(author.id) === String(userId);

  const editMutation = useMutation({
    mutationFn: () => api.put(`/comments/${comment.id}`, { content: editContent }),
    onSuccess: () => {
      setEditing(false);
      queryClient.invalidateQueries({ queryKey: ['post-comments'] });
      queryClient.invalidateQueries({ queryKey: ['post'] });
    },
  });

  return (
    <div className={`${comment.depth > 0 ? 'ml-8' : ''}`}>
      <div className="flex gap-2 py-2">
        {/* Avatar */}
        <Link to={`/profile/${author.id}`} className="shrink-0">
          {author.avatarUrl ? (
            <img
              src={author.avatarUrl}
              alt=""
              className="w-8 h-8 rounded-full object-cover"
            />
          ) : (
            <div className="w-8 h-8 bg-gray-300 text-white rounded-full flex items-center justify-center text-xs font-semibold">
              {author.displayName?.[0]?.toUpperCase() ?? '?'}
            </div>
          )}
        </Link>

        <div className="flex-1 min-w-0">
          {/* Bubble */}
          <div className="bg-gray-50 rounded-2xl px-3 py-2 group relative">
            <Link
              to={`/profile/${author.id}`}
              className="text-xs font-semibold text-gray-900 hover:underline"
            >
              {author.displayName || author.username}
            </Link>
            {editing ? (
              <div className="mt-1 space-y-2">
                <textarea
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  className="w-full text-sm border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                  rows={2}
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => editMutation.mutate()}
                    disabled={editMutation.isPending || !editContent.trim()}
                    className="text-xs bg-primary-500 text-white px-3 py-1 rounded-lg hover:bg-primary-600 transition-colors"
                  >
                    {editMutation.isPending ? 'Saving...' : 'Save'}
                  </button>
                  <button
                    onClick={() => {
                      setEditing(false);
                      setEditContent(comment.content);
                    }}
                    className="text-xs text-gray-500 hover:text-gray-700 px-3 py-1 rounded-lg border border-gray-200 hover:bg-gray-100 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <>
                <p className="text-sm text-gray-700 whitespace-pre-wrap">
                  <MentionText text={comment.content} />
                </p>
                {comment.attachments && comment.attachments.length > 0 && (
                  <div className="flex gap-2 flex-wrap mt-1.5">
                    {comment.attachments.map((att) =>
                      att.mediaType?.startsWith('image/') ? (
                        <img key={att.id} src={att.fileUrl} alt="" className="max-w-xs max-h-48 rounded-lg border border-gray-200 object-cover" />
                      ) : att.mediaType?.startsWith('video/') ? (
                        <video key={att.id} src={att.fileUrl} controls className="max-w-xs max-h-48 rounded-lg border border-gray-200" />
                      ) : (
                        <a key={att.id} href={att.fileUrl} target="_blank" rel="noreferrer" className="text-xs text-primary-500 hover:underline">
                          {att.fileUrl.split('/').pop()}
                        </a>
                      )
                    )}
                  </div>
                )}
              </>
            )}

            {/* Edit button - only visible on hover for own comments */}
            {isOwnComment && !editing && (
              <button
                onClick={() => {
                  setEditing(true);
                  setEditContent(comment.content);
                }}
                className="absolute top-2 right-2 p-1 rounded-full text-gray-400 hover:text-gray-600 hover:bg-gray-200 opacity-0 group-hover:opacity-100 transition-all"
                title="Edit comment"
              >
                <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
            )}
          </div>

          {/* Actions */}
          <div className="flex items-center gap-3 mt-1 ml-2">
            <span className="text-xs text-gray-400">
              {formatRelativeTime(comment.createdAt)}
            </span>
            <ReactionBar
              targetId={comment.id}
              reactionCounts={comment.reactionCounts ?? {}}
              currentUserReaction={comment.currentUserReaction}
            />
            {comment.depth < maxDepth && (
              <button
                onClick={() => setShowReplyForm(!showReplyForm)}
                className="text-xs font-semibold text-gray-500 hover:text-gray-700"
              >
                Reply
              </button>
            )}
          </div>

          {/* Reply form */}
          {showReplyForm && (
            <div className="mt-2">
              <CommentForm
                postId={comment.postId}
                parentCommentId={comment.id}
                onDone={() => setShowReplyForm(false)}
                placeholder="Write a reply..."
              />
            </div>
          )}
        </div>
      </div>

      {/* Nested replies */}
      {comment.replies?.map((reply) => (
        <CommentThread key={reply.id} comment={reply} maxDepth={maxDepth} />
      ))}
    </div>
  );
}

/**
 * Renders text with @[Name](id) patterns as clickable profile links.
 */
function MentionText({ text }: { text: string }) {
  // Match @[Display Name](userId)
  const mentionRegex = /@\[([^\]]+)\]\((\d+)\)/g;
  const parts: (string | { name: string; id: string })[] = [];
  let lastIndex = 0;
  let match;

  while ((match = mentionRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.substring(lastIndex, match.index));
    }
    parts.push({ name: match[1], id: match[2] });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    parts.push(text.substring(lastIndex));
  }

  if (parts.length <= 1 && typeof parts[0] === 'string') {
    return <>{text}</>;
  }

  return (
    <>
      {parts.map((part, i) =>
        typeof part === 'string' ? (
          <span key={i}>{part}</span>
        ) : (
          <Link
            key={i}
            to={`/profile/${part.id}`}
            className="text-primary-600 font-medium hover:underline"
          >
            @{part.name}
          </Link>
        )
      )}
    </>
  );
}
