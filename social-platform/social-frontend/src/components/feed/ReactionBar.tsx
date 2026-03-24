import { useState, useEffect, useRef, useCallback } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import api from '../../api/client';
import type { ReactionType } from '../../api/types';

const REACTIONS: { type: ReactionType; emoji: string; label: string }[] = [
  { type: 'LIKE', emoji: '\uD83D\uDC4D', label: 'Like' },
  { type: 'LOVE', emoji: '\u2764\uFE0F', label: 'Love' },
  { type: 'HAHA', emoji: '\uD83D\uDE02', label: 'Haha' },
  { type: 'WOW', emoji: '\uD83D\uDE2E', label: 'Wow' },
  { type: 'SAD', emoji: '\uD83D\uDE22', label: 'Sad' },
  { type: 'ANGRY', emoji: '\uD83D\uDE20', label: 'Angry' },
];

const REACTION_EMOJI: Record<string, string> = Object.fromEntries(
  REACTIONS.map((r) => [r.type, r.emoji]),
);

interface Reactor {
  userId: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  reactionType: string;
  followed: boolean;
}

interface Props {
  targetId: number;
  reactionCounts: Record<string, number>;
  currentUserReaction: string | null;
}

export default function ReactionBar({
  targetId,
  reactionCounts: initialCounts,
  currentUserReaction: initialReaction,
}: Props) {
  const [showPicker, setShowPicker] = useState(false);
  const [showWhoLiked, setShowWhoLiked] = useState(false);
  const queryClient = useQueryClient();

  // Local optimistic state
  const [localCounts, setLocalCounts] = useState(initialCounts);
  const [localReaction, setLocalReaction] = useState(initialReaction);

  // Track previous props — only sync local state when server data actually changes
  const prevCounts = useRef(initialCounts);
  const prevReaction = useRef(initialReaction);

  useEffect(() => {
    if (prevCounts.current !== initialCounts || prevReaction.current !== initialReaction) {
      prevCounts.current = initialCounts;
      prevReaction.current = initialReaction;
      setLocalCounts(initialCounts);
      setLocalReaction(initialReaction);
    }
  }, [initialCounts, initialReaction]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['feed'] });
    queryClient.invalidateQueries({ queryKey: ['post'] });
    queryClient.invalidateQueries({ queryKey: ['reactors', targetId] });
  };

  const addReaction = useMutation({
    mutationFn: (reactionType: ReactionType) =>
      api.post('/reactions', { targetId, reactionType }),
    onMutate: (reactionType: ReactionType) => {
      setLocalCounts((prev) => {
        const updated = { ...prev };
        if (localReaction) {
          updated[localReaction] = Math.max(0, Number(updated[localReaction] ?? 1) - 1) as any;
          if (Number(updated[localReaction]) === 0) delete updated[localReaction];
        }
        updated[reactionType] = (Number(updated[reactionType] ?? 0) + 1) as any;
        return updated;
      });
      setLocalReaction(reactionType);
    },
    onError: () => {
      // Rollback on failure
      setLocalCounts(initialCounts);
      setLocalReaction(initialReaction);
    },
    onSuccess: () => invalidate(),
  });

  const removeReaction = useMutation({
    mutationFn: () => api.delete(`/reactions/${targetId}`),
    onMutate: () => {
      if (localReaction) {
        setLocalCounts((prev) => {
          const updated = { ...prev };
          updated[localReaction] = Math.max(0, Number(updated[localReaction] ?? 1) - 1) as any;
          if (Number(updated[localReaction]) === 0) delete updated[localReaction];
          return updated;
        });
      }
      setLocalReaction(null);
    },
    onError: () => {
      setLocalCounts(initialCounts);
      setLocalReaction(initialReaction);
    },
    onSuccess: () => invalidate(),
  });

  // Fetch "who reacted" on hover — stays loaded once fetched
  const { data: reactors } = useQuery<Reactor[]>({
    queryKey: ['reactors', targetId],
    queryFn: async () => {
      const { data } = await api.get(`/reactions/${targetId}/users`);
      return data;
    },
    enabled: showWhoLiked,
    staleTime: 30000,
  });

  const handleReact = (type: ReactionType) => {
    setShowPicker(false);
    if (localReaction === type) {
      removeReaction.mutate();
    } else {
      addReaction.mutate(type);
    }
  };

  const totalReactions = Object.values(localCounts).reduce((a, b) => Number(a) + Number(b), 0);
  const isPending = addReaction.isPending || removeReaction.isPending;

  // Build summary text like "Jane, Bob, and 72 others"
  const followedReactors = reactors?.filter((r) => r.followed) ?? [];
  const otherReactors = reactors?.filter((r) => !r.followed) ?? [];
  const othersCount = totalReactions - followedReactors.length;

  const buildSummaryText = () => {
    if (!reactors || reactors.length === 0) return null;
    const names = followedReactors.slice(0, 2).map((r) => r.displayName);
    if (names.length === 0) {
      // No followed users reacted — just show count
      return null;
    }
    if (othersCount <= 0) {
      return names.join(' and ');
    }
    return `${names.join(', ')} and ${othersCount} other${othersCount !== 1 ? 's' : ''}`;
  };

  const summaryText = buildSummaryText();

  return (
    <div className="relative flex items-center gap-2 flex-1">
      {/* Reaction summary with "who liked" popover */}
      {totalReactions > 0 && (
        <div
          className="relative flex items-center gap-1.5 text-sm text-gray-500 cursor-pointer group flex-1 min-w-0"
          onMouseEnter={() => setShowWhoLiked(true)}
          onMouseLeave={() => setShowWhoLiked(false)}
        >
          {/* Emoji icons */}
          <span className="flex -space-x-0.5 shrink-0">
            {Object.entries(localCounts)
              .filter(([, c]) => Number(c) > 0)
              .sort(([, a], [, b]) => Number(b) - Number(a))
              .slice(0, 3)
              .map(([type]) => (
                <span key={type} className="text-base">
                  {REACTION_EMOJI[type] ?? type}
                </span>
              ))}
          </span>

          {/* Summary text or just count */}
          <span className="group-hover:underline truncate">
            {summaryText ?? totalReactions}
          </span>

          {/* Who liked popover */}
          {showWhoLiked && reactors && reactors.length > 0 && (
            <div className="absolute bottom-full left-0 mb-2 w-72 bg-gray-800 text-white text-xs rounded-lg shadow-xl p-3 z-50">
              {/* Followed users section */}
              {followedReactors.length > 0 && (
                <>
                  <div className="font-semibold text-blue-300 mb-1.5 text-[11px] uppercase tracking-wide">
                    People you follow
                  </div>
                  <div className="space-y-1.5 mb-2">
                    {followedReactors.map((r) => (
                      <Link
                        key={r.userId}
                        to={`/profile/${r.userId}`}
                        className="flex items-center gap-2 hover:bg-gray-700 rounded px-1 py-0.5 -mx-1"
                      >
                        {r.avatarUrl ? (
                          <img src={r.avatarUrl} alt="" className="w-5 h-5 rounded-full" />
                        ) : (
                          <div className="w-5 h-5 bg-blue-500 rounded-full flex items-center justify-center text-[10px] font-bold">
                            {r.displayName?.[0]?.toUpperCase() ?? '?'}
                          </div>
                        )}
                        <span className="flex-1 truncate font-medium">{r.displayName}</span>
                        <span>{REACTION_EMOJI[r.reactionType] ?? r.reactionType}</span>
                      </Link>
                    ))}
                  </div>
                </>
              )}

              {/* Other users */}
              {otherReactors.length > 0 && (
                <>
                  {followedReactors.length > 0 && (
                    <div className="border-t border-gray-600 my-2" />
                  )}
                  <div className="font-semibold text-gray-400 mb-1.5 text-[11px] uppercase tracking-wide">
                    {followedReactors.length > 0 ? 'Others' : 'Reactions'}
                  </div>
                  <div className="space-y-1.5 max-h-36 overflow-y-auto">
                    {otherReactors.map((r) => (
                      <Link
                        key={r.userId}
                        to={`/profile/${r.userId}`}
                        className="flex items-center gap-2 hover:bg-gray-700 rounded px-1 py-0.5 -mx-1"
                      >
                        {r.avatarUrl ? (
                          <img src={r.avatarUrl} alt="" className="w-5 h-5 rounded-full" />
                        ) : (
                          <div className="w-5 h-5 bg-gray-600 rounded-full flex items-center justify-center text-[10px]">
                            {r.displayName?.[0]?.toUpperCase() ?? '?'}
                          </div>
                        )}
                        <span className="flex-1 truncate">{r.displayName}</span>
                        <span>{REACTION_EMOJI[r.reactionType] ?? r.reactionType}</span>
                      </Link>
                    ))}
                  </div>
                </>
              )}

              {totalReactions > 20 && (
                <div className="text-gray-400 text-[10px] mt-2 pt-1.5 border-t border-gray-600">
                  and {totalReactions - 20} more...
                </div>
              )}

              {/* Arrow */}
              <div className="absolute top-full left-4 w-0 h-0 border-l-[6px] border-r-[6px] border-t-[6px] border-transparent border-t-gray-800" />
            </div>
          )}
        </div>
      )}

      {/* React button + picker */}
      <ReactionPicker
        localReaction={localReaction}
        isPending={isPending}
        onReact={handleReact}
        onToggle={() => localReaction ? removeReaction.mutate() : handleReact('LIKE')}
      />
    </div>
  );
}

/* ── Reaction Picker with hover delay ── */

function ReactionPicker({
  localReaction,
  isPending,
  onReact,
  onToggle,
}: {
  localReaction: string | null;
  isPending: boolean;
  onReact: (type: ReactionType) => void;
  onToggle: () => void;
}) {
  const [showPicker, setShowPicker] = useState(false);
  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearHideTimer = useCallback(() => {
    if (hideTimer.current) {
      clearTimeout(hideTimer.current);
      hideTimer.current = null;
    }
  }, []);

  const startHideTimer = useCallback(() => {
    clearHideTimer();
    hideTimer.current = setTimeout(() => setShowPicker(false), 300);
  }, [clearHideTimer]);

  const handleEnter = useCallback(() => {
    clearHideTimer();
    setShowPicker(true);
  }, [clearHideTimer]);

  useEffect(() => {
    return () => clearHideTimer();
  }, [clearHideTimer]);

  return (
    <div className="relative shrink-0">
      {/* Picker (above) */}
      {showPicker && (
        <div
          className="absolute bottom-full right-0 mb-1 bg-white rounded-full shadow-lg border border-gray-100 px-1.5 py-1 flex gap-0.5 z-40"
          onMouseEnter={handleEnter}
          onMouseLeave={startHideTimer}
        >
          {REACTIONS.map(({ type, emoji, label }) => (
            <button
              key={type}
              onClick={() => { onReact(type); setShowPicker(false); }}
              className={`text-xl hover:scale-125 transition-transform p-1 rounded-full ${
                localReaction === type ? 'bg-primary-50' : ''
              }`}
              title={label}
            >
              {emoji}
            </button>
          ))}
        </div>
      )}

      {/* Button */}
      <button
        onClick={onToggle}
        onMouseEnter={handleEnter}
        onMouseLeave={startHideTimer}
        disabled={isPending}
        className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium transition-all ${
          localReaction
            ? 'text-primary-500 bg-primary-50'
            : 'text-gray-500 hover:bg-gray-100'
        } ${isPending ? 'opacity-70' : ''}`}
      >
        <span className="text-base">
          {localReaction
            ? REACTION_EMOJI[localReaction] ?? '\uD83D\uDC4D'
            : '\uD83D\uDC4D'}
        </span>
        {localReaction ?? 'Like'}
      </button>
    </div>
  );
}
