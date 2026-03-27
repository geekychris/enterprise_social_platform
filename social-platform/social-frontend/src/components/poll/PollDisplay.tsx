import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { PollDto } from '../../api/types';

export default function PollDisplay({ poll: initialPoll, postId }: { poll: PollDto; postId: number }) {
  const [poll, setPoll] = useState(initialPoll);
  const [selected, setSelected] = useState<number[]>(initialPoll.currentUserVotes || []);
  const [hasVoted, setHasVoted] = useState(initialPoll.currentUserVotes?.length > 0);
  const queryClient = useQueryClient();

  const voteMutation = useMutation({
    mutationFn: async (optionIds: number[]) => {
      const { data } = await api.post(`/polls/${poll.id}/vote`, { optionIds });
      return data as PollDto;
    },
    onSuccess: (updatedPoll) => {
      setPoll(updatedPoll);
      setSelected(updatedPoll.currentUserVotes || []);
      setHasVoted(true);
      // Invalidate all potential parent queries
      queryClient.invalidateQueries({ queryKey: ['feed'] });
      queryClient.invalidateQueries({ queryKey: ['post'] });
      queryClient.invalidateQueries({ queryKey: ['group-posts'] });
      queryClient.invalidateQueries({ queryKey: ['page-posts'] });
    },
  });

  const toggle = (optionId: number) => {
    if (hasVoted || poll.closed) return;
    setSelected((prev) => {
      if (poll.allowMultiple) {
        return prev.includes(optionId) ? prev.filter((id) => id !== optionId) : [...prev, optionId];
      }
      return [optionId];
    });
  };

  const submit = () => {
    if (selected.length > 0) voteMutation.mutate(selected);
  };

  return (
    <div className="bg-gray-50 rounded-lg p-3 mt-2 space-y-2">
      <p className="text-sm font-semibold text-gray-800">{poll.question}</p>

      <div className="space-y-1.5">
        {poll.options.map((opt) => {
          const pct = poll.totalVotes > 0 ? Math.round((opt.voteCount / poll.totalVotes) * 100) : 0;
          const isSelected = selected.includes(opt.id);
          const showResults = hasVoted || poll.closed;

          return (
            <button
              key={opt.id}
              onClick={() => toggle(opt.id)}
              disabled={hasVoted || poll.closed}
              className={`w-full text-left rounded-lg border transition-colors relative overflow-hidden ${
                isSelected
                  ? 'border-primary-500 bg-primary-50'
                  : 'border-gray-200 bg-white hover:bg-gray-50'
              } ${hasVoted || poll.closed ? 'cursor-default' : 'cursor-pointer'}`}
            >
              {showResults && (
                <div
                  className="absolute inset-y-0 left-0 bg-primary-100 transition-all duration-500"
                  style={{ width: `${pct}%` }}
                />
              )}
              <div className="relative px-3 py-2 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {!showResults && (
                    <div
                      className={`w-4 h-4 ${poll.allowMultiple ? 'rounded-sm' : 'rounded-full'} border-2 flex items-center justify-center ${
                        isSelected ? 'border-primary-500 bg-primary-500' : 'border-gray-300'
                      }`}
                    >
                      {isSelected && (
                        <svg className="w-2.5 h-2.5 text-white" fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                        </svg>
                      )}
                    </div>
                  )}
                  <span className="text-sm text-gray-700">{opt.label}</span>
                </div>
                {showResults && (
                  <span className="text-xs text-gray-500 font-medium">{pct}% ({opt.voteCount})</span>
                )}
              </div>
            </button>
          );
        })}
      </div>

      <div className="flex items-center justify-between">
        <span className="text-[10px] text-gray-400">{poll.totalVotes} vote{poll.totalVotes !== 1 ? 's' : ''}</span>
        {!hasVoted && !poll.closed && selected.length > 0 && (
          <button
            onClick={submit}
            disabled={voteMutation.isPending}
            className="text-xs bg-primary-500 text-white px-3 py-1 rounded-lg hover:bg-primary-600"
          >
            {voteMutation.isPending ? 'Voting...' : 'Vote'}
          </button>
        )}
        {poll.closed && <span className="text-[10px] text-red-400">Poll closed</span>}
      </div>
    </div>
  );
}
